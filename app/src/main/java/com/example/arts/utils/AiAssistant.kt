package com.example.arts.utils

import android.content.Context
import android.util.Log
import com.example.arts.BuildConfig
import com.example.arts.R
import com.example.arts.data.ChatMessage
import com.example.arts.data.IncidentAiSuggestion
import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI Assistant — natural multi-turn conversation with silent incident detection.
 *
 * Key design decisions:
 *
 * 1. The model is told to respond as a warm, human call-centre agent first and
 *    an ARTS system second. The JSON envelope is now a thin wrapper around a
 *    free-form reply field rather than a form to fill in. This prevents the AI
 *    from sounding like it is reciting a template.
 *
 * 2. Temperature raised to 0.9 for more natural, varied language.
 *
 * 3. History merging is fixed: consecutive same-role turns are merged into one
 *    part array instead of being silently dropped. Gemini's API rejects
 *    consecutive same-role turns.
 *
 * 4. The incident detection trigger is now optional and the AI decides when it
 *    is genuinely relevant, rather than being asked on every single message.
 *    General questions, greetings, follow-ups, and thank-you messages get a
 *    plain conversational reply with no structured payload.
 *
 * 5. The local fallback gives a human-sounding reply instead of a triage dump.
 */
object AiAssistant {

    private const val TAG = "ARTS-AI"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent"

    private val VALID_PRIORITIES = setOf("High", "Medium", "Low")
    private val VALID_TYPES      = setOf("Collision", "Pedestrian", "Breakdown", "Emergency")

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    private fun getApiKey(context: Context): String {
        val custom = PrefsHelper.getCustomAiKey(context)
        if (custom.isNotBlank()) return custom
        return try { BuildConfig.ARTS_AI_KEY } catch (e: Throwable) { "" }
    }

    private fun getModel(): String = try {
        // FIX: gemini-1.5-flash was fully retired by Google and now returns
        // a model-not-found error on every call, which was silently sending
        // Aria into her offline fallback every single time. gemini-3.1-flash-lite
        // is the current stable, free-tier model with no retirement date
        // announced as of November 2026.
        BuildConfig.ARTS_AI_MODEL.takeUnless { it.isNullOrBlank() } ?: "gemini-3.1-flash-lite"
    } catch (e: Throwable) { "gemini-3.1-flash-lite" }

    /** One-shot incident analysis used by VoiceReportFragment and ReportAccidentFragment. */
    suspend fun analyzeIncident(context: Context, userInput: String): IncidentAiSuggestion {
        val (_, suggestion) = chat(context, emptyList(), userInput)
        return suggestion ?: localTriage(userInput)
    }

    /**
     * Full multi-turn chat. Returns a natural-language reply and an optional
     * structured incident analysis (only present when the user described an
     * actual incident — not for greetings, follow-up questions, etc.).
     */
    suspend fun chat(
        context: Context,
        history: List<ChatMessage>,
        currentMessage: String
    ): Pair<String, IncidentAiSuggestion?> = withContext(Dispatchers.IO) {
        val apiKey = getApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext localFallbackReply(currentMessage)
        }
        try {
            callGemini(apiKey, history, currentMessage)
        } catch (e: Exception) {
            Log.w(TAG, "Gemini call failed: ${e.message}")
            Pair(
                "Sorry, I'm having a little trouble connecting right now. " +
                "If this is an emergency, please call 112 immediately. " +
                "Otherwise, try again in a moment.",
                null
            )
        }
    }

    // -------------------------------------------------------------------------
    // Gemini call
    // -------------------------------------------------------------------------

    private fun callGemini(
        apiKey: String,
        history: List<ChatMessage>,
        currentMessage: String
    ): Pair<String, IncidentAiSuggestion?> {

        // Build the contents array.
        // Gemini requires strict user/model alternation. We merge consecutive
        // same-role turns into a single content object (multiple parts) rather
        // than dropping them, so the full conversation is preserved.
        val contents = mutableListOf<Map<String, Any>>()

        // Work only with the last 30 messages to stay well under token limits.
        val trimmed = history.takeLast(30)

        data class Turn(val role: String, val parts: MutableList<String>)

        val merged = mutableListOf<Turn>()
        trimmed.forEach { msg ->
            val role = if (msg.fromUser) "user" else "model"
            val last = merged.lastOrNull()
            if (last != null && last.role == role) {
                last.parts.add(msg.text)
            } else {
                merged.add(Turn(role, mutableListOf(msg.text)))
            }
        }

        // The API requires the first turn to be "user". Drop any leading model turns.
        val startsAt = merged.indexOfFirst { it.role == "user" }
        val validHistory = if (startsAt >= 0) merged.drop(startsAt) else emptyList()

        validHistory.forEach { turn ->
            contents.add(mapOf(
                "role"  to turn.role,
                "parts" to turn.parts.map { mapOf("text" to it) }
            ))
        }

        // Append the new user message.
        contents.add(mapOf(
            "role"  to "user",
            "parts" to listOf(mapOf("text" to currentMessage))
        ))

        val body = mapOf(
            "contents"           to contents,
            "system_instruction" to mapOf(
                "parts" to listOf(mapOf("text" to SYSTEM_INSTRUCTION))
            ),
            "generationConfig"   to mapOf(
                // We use application/json so the model always returns valid JSON.
                // The key difference from before is that the JSON schema is now
                // extremely loose — the model writes the reply freely inside "reply"
                // and only fills in "incident" when it genuinely applies.
                "responseMimeType" to "application/json",
                "temperature"      to 0.9,
                "topP"             to 0.95,
                "maxOutputTokens"  to 1024
            )
        )

        val url = URL(ENDPOINT.format(getModel()))
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout   = 30_000
            doOutput      = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            connection.outputStream.use { it.write(Gson().toJson(body).toByteArray()) }
            if (connection.responseCode !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.use { r -> r.readText() }
                    ?: "HTTP ${connection.responseCode}"
                throw IOException("Gemini error: $err")
            }
            val raw = connection.inputStream.bufferedReader().use { it.readText() }
            return parseChatResponse(raw, currentMessage)
        } finally {
            connection.disconnect()
        }
    }

    // -------------------------------------------------------------------------
    // System instruction
    // -------------------------------------------------------------------------

    /**
     * The system instruction defines the assistant's personality and the JSON
     * contract it must follow. The critical change from the original version:
     *
     *   - The model is told to write its reply as a person first. The JSON is
     *     a thin envelope, not a form.
     *   - "incident" is OPTIONAL. It must only appear when the user is
     *     actually describing something that happened on the road. Greetings,
     *     app questions, and general chat get a reply with no incident field.
     *   - The tone section is detailed and gives concrete style examples so
     *     the model learns the register, not just a label.
     */
    private val SYSTEM_INSTRUCTION = """
You are Aria, the emergency support assistant inside the ARTS (Accident Report & Tracking System) app.
Your role is to feel like a calm, compassionate real person talking to a driver who may be stressed or in danger.

PERSONALITY AND TONE
- You speak the way a thoughtful, warm call-centre agent speaks: direct, human, never robotic.
- Short sentences. Plain words. No jargon unless you are explaining a form field.
- Mirror the caller's energy: if they are panicked, steady them first ("You're going to be okay — I'm with you"). If they are calm, match that.
- Never begin a reply with "Of course!", "Certainly!", "Great!", or any hollow affirmation. Just respond.
- Never recite lists of bullet points in casual conversation. Only use structure (numbered steps) when genuinely needed, like walking someone through the report form.
- Contractions are fine: "I'm", "you're", "we'll", "don't".
- If you don't know something, say so honestly: "I'm not sure about that — the best person to ask is your insurance provider."
- You can use mild filler phrases naturally: "Right, so...", "Okay, let me check that for you.", "That makes sense."

WHAT YOU HELP WITH
1. Accidents and emergencies — guide them through reporting, offer to send SOS if danger is immediate.
2. Breakdowns — calm them down, help them file a report, suggest safety steps (hazards on, stay in car if safe).
3. App questions — how to submit a report, change their photo, find their history, etc.
4. General road safety questions — answer factually and briefly.
5. Anything else — if it is completely outside road safety and the app, politely say you are only set up to help with those topics.

INCIDENT DETECTION
- You detect an incident ONLY when the user is clearly describing something that actually happened to them right now or recently — a crash, a person hit, a breakdown, an emergency.
- Greetings, questions about the app, thank-yous, follow-up messages, and "what should I do?" questions are NOT incidents. Do not attach an incident object to those.

REQUIRED RESPONSE FORMAT
Always respond with this exact JSON. The "reply" field is your full, natural message to the user.
The "incident" field is OPTIONAL — include it only when the user described an actual incident.

{
  "reply": "Your full natural reply here.",
  "incident": {
    "priority": "High | Medium | Low",
    "type": "Collision | Pedestrian | Breakdown | Emergency",
    "refinedDescription": "One or two factual sentences summarising what happened, written in third person for the report."
  }
}

If there is no incident, omit the "incident" key entirely. Do not set it to null.
Never include anything outside the JSON object.
""".trimIndent()

    // -------------------------------------------------------------------------
    // Response parser
    // -------------------------------------------------------------------------

    /**
     * Parses the Gemini response envelope and extracts the reply + optional
     * incident analysis.
     *
     * Context is intentionally NOT a parameter here so unit tests can call this
     * function directly without an Android environment.
     */
    fun parseChatResponse(
        json: String,
        fallbackText: String
    ): Pair<String, IncidentAiSuggestion?> = runCatching {

        val root = JsonParser.parseString(json)
        if (!root.isJsonObject) return Pair(conversationalFallback(), null)

        val candidates = root.asJsonObject.getAsJsonArray("candidates")
            ?: return Pair(conversationalFallback(), null)
        if (candidates.size() == 0) return Pair(conversationalFallback(), null)

        val parts = candidates[0].asJsonObject
            ?.getAsJsonObject("content")
            ?.getAsJsonArray("parts")
            ?: return Pair(conversationalFallback(), null)

        val rawText = parts[0]?.asJsonObject?.get("text")?.asString?.trim()
            ?: return Pair(conversationalFallback(), null)

        // Strip any markdown fences the model may have added despite being told not to.
        val jsonText = rawText
            .removePrefix("```json").removePrefix("```")
            .removeSuffix("```").trim()

        val start = jsonText.indexOf("{")
        val end   = jsonText.lastIndexOf("}")
        if (start == -1 || end == -1 || end <= start) {
            // Model returned plain text — treat it as the reply directly.
            return Pair(rawText.take(2000), null)
        }

        val obj = JsonParser.parseString(jsonText.substring(start, end + 1))
            .takeIf { it.isJsonObject }?.asJsonObject
            ?: return Pair(conversationalFallback(), null)

        val reply = safeStr(obj, "reply")
            .ifBlank { conversationalFallback() }
            .take(2000)

        val suggestion: IncidentAiSuggestion? = if (obj.has("incident") && obj.get("incident").isJsonObject) {
            val inc = obj.getAsJsonObject("incident")
            val priority = safeStr(inc, "priority").let {
                if (it in VALID_PRIORITIES) it else "Medium"
            }
            val type = safeStr(inc, "type").let {
                if (it in VALID_TYPES) it else "Collision"
            }
            val desc = safeStr(inc, "refinedDescription").ifBlank { tidy(fallbackText) }
            IncidentAiSuggestion(priority, type, desc, source = "AI")
        } else null

        Pair(reply, suggestion)

    }.getOrElse { e ->
        Log.e(TAG, "parseChatResponse failed", e)
        Pair(
            "I'm still here — something went wrong on my end. Can you say that again?",
            null
        )
    }

    // -------------------------------------------------------------------------
    // Local fallback (no API key or network failure)
    // -------------------------------------------------------------------------

    /**
     * When the Gemini API is unavailable, give a human-sounding holding reply
     * and do a quick keyword triage to still populate the report form.
     */
    fun localFallbackReply(text: String): Pair<String, IncidentAiSuggestion?> {
        val lower = text.lowercase()
        val isIncident = INCIDENT_KEYWORDS.any { lower.contains(it) }

        if (!isIncident) {
            return Pair(
                "I'm in offline mode right now so my answers are limited. " +
                "If you need urgent help, please call 112.",
                null
            )
        }

        val triage = localTriage(text)
        val reply = when (triage.priority) {
            "High" ->
                "That sounds serious. I'm offline right now but I've prepared a report with the details you gave me. " +
                "Please call 112 immediately if anyone is injured."
            "Low" ->
                "Sorry to hear that. I'm offline right now but I've started a breakdown report for you. " +
                "Make sure your hazard lights are on and move away from traffic if it's safe to do so."
            else ->
                "Got it. I'm in offline mode but I've prepared the report details from what you told me. " +
                "You can review and submit it on the next screen."
        }
        return Pair(reply, triage)
    }

    private val INCIDENT_KEYWORDS = listOf(
        "crash", "accident", "hit", "collision", "broke down", "breakdown",
        "breakdown", "flat tyre", "flat tire", "puncture", "injured", "hurt",
        "bleeding", "unconscious", "fire", "trapped", "emergency", "overturned",
        "skidded", "ran over", "pedestrian", "stalled", "engine", "help"
    )

    /** Local keyword triage — used as a fallback and for single-shot analysis. */
    fun localTriage(text: String): IncidentAiSuggestion {
        val lower = text.lowercase()
        val type = when {
            listOf("person", "pedestrian", "walk", "child", "ran over", "hit a")
                .any { lower.contains(it) } -> "Pedestrian"
            listOf("broke down", "breakdown", "engine", "tyre", "tire", "puncture",
                   "stalled", "battery", "fuel", "overheating")
                .any { lower.contains(it) } -> "Breakdown"
            listOf("fire", "emergency", "sos", "help", "trapped")
                .any { lower.contains(it) } -> "Emergency"
            else -> "Collision"
        }
        val urgentWords = listOf(
            "fire", "trapped", "unconscious", "not breathing", "bleeding",
            "fatal", "dead", "pinned", "overturned", "fuel leak", "explosion"
        )
        val urgentCount = urgentWords.count { lower.contains(it) }
        val priority = when {
            urgentCount >= 2  -> "High"
            urgentCount == 1  -> "High"
            type == "Emergency" -> "High"
            type == "Breakdown" -> "Low"
            else              -> "Medium"
        }
        return IncidentAiSuggestion(
            priority           = priority,
            type               = type,
            refinedDescription = tidy(text),
            source             = "Local System"
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun conversationalFallback() =
        "I'm still here. Could you say that again? I want to make sure I understand."

    private fun safeStr(obj: JsonObject, key: String): String =
        obj.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: ""

    fun tidy(text: String): String {
        val t = text.trim().replace(Regex("\\s+"), " ")
        if (t.isEmpty()) return ""
        val c = t.replaceFirstChar { it.uppercase() }
        return if (c.last() in ".!?") c else "$c."
    }
}
