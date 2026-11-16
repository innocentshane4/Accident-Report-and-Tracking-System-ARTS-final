package com.example.arts

import com.example.arts.utils.AiAssistant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for AiAssistant — covers parseChatResponse (the Gemini envelope parser)
 * and the local fallbacks (localTriage, localFallbackReply, tidy).
 *
 * JSON schema change: the response object now uses "incident" (optional) instead
 * of "incidentDetected" + "analysis". The incident field is omitted entirely for
 * conversational messages so the AI doesn't attach a report to every reply.
 */
class GeminiHelperTest {

    // -------------------------------------------------------------------------
    // Helper: wrap inner JSON in a fake Gemini API envelope
    // -------------------------------------------------------------------------

    private fun geminiEnvelope(innerJson: String): String {
        // The inner JSON goes inside a text part. We escape it as a JSON string value.
        val escaped = innerJson
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        return """{"candidates":[{"content":{"parts":[{"text":"$escaped"}]}}]}"""
    }

    // -------------------------------------------------------------------------
    // parseChatResponse — incident detected
    // -------------------------------------------------------------------------

    @Test
    fun `parses a well-formed reply with an incident block`() {
        val inner = """{
            "reply": "That sounds serious. I've started a report for you.",
            "incident": {
                "priority": "High",
                "type": "Collision",
                "refinedDescription": "Two vehicles collided at the intersection."
            }
        }"""
        val (reply, incident) = AiAssistant.parseChatResponse(geminiEnvelope(inner), "two cars crashed")

        assertEquals("That sounds serious. I've started a report for you.", reply)
        assertNotNull(incident)
        assertEquals("High", incident?.priority)
        assertEquals("Collision", incident?.type)
        assertEquals("Two vehicles collided at the intersection.", incident?.refinedDescription)
        assertEquals("AI", incident?.source)
    }

    @Test
    fun `strips markdown code fences before parsing`() {
        val inner = "```json\n{\"reply\": \"Car broke down.\", \"incident\": {\"priority\":\"Low\",\"type\":\"Breakdown\",\"refinedDescription\":\"Vehicle breakdown on the roadside.\"}}\n```"
        val (reply, incident) = AiAssistant.parseChatResponse(geminiEnvelope(inner), "my car broke down")

        assertEquals("Car broke down.", reply)
        assertNotNull(incident)
        assertEquals("Low", incident?.priority)
        assertEquals("Breakdown", incident?.type)
    }

    @Test
    fun `replaces invalid priority and type values with safe defaults`() {
        val inner = """{
            "reply": "Something happened.",
            "incident": {
                "priority": "CRITICAL!!!",
                "type": "Alien abduction",
                "refinedDescription": ""
            }
        }"""
        val (_, incident) = AiAssistant.parseChatResponse(geminiEnvelope(inner), "something happened")

        assertNotNull(incident)
        assertEquals("Medium", incident?.priority)
        assertEquals("Collision", incident?.type)
        // refinedDescription is blank, so it falls back to tidy(fallbackText)
        assertEquals("Something happened.", incident?.refinedDescription)
    }

    @Test
    fun `returns no incident for a pure conversational reply`() {
        val inner = """{
            "reply": "Of course! To change your photo, go to Settings and tap your avatar."
        }"""
        val (reply, incident) = AiAssistant.parseChatResponse(geminiEnvelope(inner), "how do I change my photo?")

        assertEquals("Of course! To change your photo, go to Settings and tap your avatar.", reply)
        assertNull(incident)
    }

    @Test
    fun `handles a greeting with no incident gracefully`() {
        val inner = """{"reply": "Hey! What's going on?"}"""
        val (reply, incident) = AiAssistant.parseChatResponse(geminiEnvelope(inner), "hi")

        assertTrue(reply.isNotBlank())
        assertNull(incident)
    }

    @Test
    fun `returns a fallback reply when the envelope is malformed`() {
        val (reply, incident) = AiAssistant.parseChatResponse("not json at all", "test")

        assertTrue(reply.isNotBlank())
        assertNull(incident)
    }

    @Test
    fun `returns a fallback reply when candidates array is empty`() {
        val emptyEnvelope = """{"candidates":[]}"""
        val (reply, _) = AiAssistant.parseChatResponse(emptyEnvelope, "test")

        assertTrue(reply.isNotBlank())
    }

    // -------------------------------------------------------------------------
    // localTriage
    // -------------------------------------------------------------------------

    @Test
    fun `localTriage flags fire and trapped as High priority`() {
        val result = AiAssistant.localTriage("car is on fire and driver is trapped inside")
        assertEquals("High", result.priority)
        assertEquals("Local System", result.source)
    }

    @Test
    fun `localTriage recognises a pedestrian incident`() {
        val result = AiAssistant.localTriage("a child was hit while crossing the road")
        assertEquals("Pedestrian", result.type)
    }

    @Test
    fun `localTriage classifies a breakdown as Low priority`() {
        val result = AiAssistant.localTriage("my car stalled on the highway shoulder")
        assertEquals("Breakdown", result.type)
        assertEquals("Low", result.priority)
    }

    @Test
    fun `localTriage defaults to Collision for unrecognised input`() {
        val result = AiAssistant.localTriage("there was an incident on the road")
        assertEquals("Collision", result.type)
    }

    // -------------------------------------------------------------------------
    // localFallbackReply
    // -------------------------------------------------------------------------

    @Test
    fun `localFallbackReply returns offline message for non-incident input`() {
        val (reply, incident) = AiAssistant.localFallbackReply("how do I update my profile?")
        assertTrue(reply.contains("offline", ignoreCase = true))
        assertNull(incident)
    }

    @Test
    fun `localFallbackReply returns a triage suggestion for incident keywords`() {
        val (reply, incident) = AiAssistant.localFallbackReply("I was in a crash on the N1")
        assertTrue(reply.isNotBlank())
        assertNotNull(incident)
    }

    // -------------------------------------------------------------------------
    // tidy
    // -------------------------------------------------------------------------

    @Test
    fun `tidy capitalises and appends a full stop`() {
        assertEquals("Car stopped suddenly.", AiAssistant.tidy("  car   stopped suddenly "))
    }

    @Test
    fun `tidy leaves existing terminal punctuation intact`() {
        assertEquals("Already fine!", AiAssistant.tidy("already fine!"))
        assertEquals("Already fine?", AiAssistant.tidy("already fine?"))
    }

    @Test
    fun `tidy returns empty string for blank input`() {
        assertTrue(AiAssistant.tidy("").isEmpty())
        assertTrue(AiAssistant.tidy("   ").isEmpty())
    }
}
