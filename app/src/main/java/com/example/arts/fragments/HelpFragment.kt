package com.example.arts.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.arts.R

/** In-app help: common questions and answers, grouped by topic. Tap a question to open its answer. */
class HelpFragment : Fragment() {

    private val faq: List<Pair<String, List<Pair<String, String>>>> = listOf(
        "Getting started" to listOf(
            "How do I report an accident?" to "Tap Report on the Home screen. Choose the type and priority, describe what happened, and pin the exact spot on the map. You can add a photo too. Then tap Submit. You can follow it under Incidents.",
            "What does the Emergency button do?" to "On the Home screen, Emergency lets you call 112 or send a panic report. A panic report shares your current location as a High priority emergency and notifies ambulance and police straight away.",
            "Does ARTS replace calling the emergency services?" to "No. If someone is in danger, call 112 (any cellphone), 10111 (police) or 10177 (ambulance) first. ARTS helps by sending your location and details to the services we work with."
        ),
        "Reports and alerts" to listOf(
            "How does ARTS decide who to notify?" to "A report is urgent if it is marked High priority, or if the description has two or more warning words such as fire, trapped or unconscious. Urgent reports go automatically to the matching active services.",
            "What do the statuses mean?" to "Pending: not reviewed yet. Forwarded: sent to emergency services. Resolved: dealt with.",
            "Where do I see updates?" to "Open the Alerts tab for messages about your reports, or the Incidents tab and tap a report to see its status, map, weather and QR code."
        ),
        "Your information" to listOf(
            "Why add medical information?" to "If you cannot speak for yourself after an accident, responders can see your blood type, allergies, medication, medical aid and emergency contact. Only you and ARTS administrators can see it.",
            "How do I change my details or photo?" to "Open Profile, then Settings. You can change your name, phone number and photo."
        ),
        "Signing in and security" to listOf(
            "Can I sign in with Google?" to "Yes. Tap Continue with Google on the sign-in screen. The first time, it creates your ARTS account.",
            "How do I turn on two-factor login?" to "Open Profile, then Security, then Two-Factor Authentication. Add the key to Google Authenticator or Authy, then enter the 6-digit code it shows.",
            "What is biometric unlock?" to "When it is on, ARTS asks for your fingerprint or face before opening an account that is already signed in on this phone.",
            "I forgot my password." to "On the sign-in screen, type your email address and tap Forgot password. A reset link is emailed to you."
        )
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_help, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_back).setOnClickListener { 
            if (isAdded) findNavController().navigateUp() 
        }

        val container = view.findViewById<LinearLayout>(R.id.faq_container) ?: return
        faq.forEach { (group, items) ->
            groupTitle(group)?.let { container.addView(it) }
            items.forEach { (question, answer) -> 
                faqCard(question, answer)?.let { container.addView(it) }
            }
        }
    }

    private fun dp(value: Int): Int {
        val ctx = context ?: return value * 2
        return (value * ctx.resources.displayMetrics.density).toInt()
    }

    private fun groupTitle(text: String): View? {
        val ctx = context ?: return null
        return TextView(ctx).apply {
            this.text = text.uppercase()
            setTextColor(ContextCompat.getColor(ctx, R.color.accent_green))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            letterSpacing = 0.08f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, dp(20), 0, dp(8)) }
        }
    }

    private fun faqCard(question: String, answer: String): View? {
        val ctx = context ?: return null
        val card = CardView(ctx).apply {
            radius = dp(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.bg_card))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { setMargins(0, 0, 0, dp(8)) }
        }

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val questionView = TextView(ctx).apply {
            text = question
            setTextColor(ContextCompat.getColor(ctx, R.color.text_primary))
            textSize = 14.5f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val marker = TextView(ctx).apply {
            text = "+"
            setTextColor(ContextCompat.getColor(ctx, R.color.accent_green))
            textSize = 20f
        }
        row.addView(questionView)
        row.addView(marker)

        val answerView = TextView(ctx).apply {
            text = answer
            setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
            textSize = 13.5f
            setLineSpacing(dp(2).toFloat(), 1f)
            visibility = View.GONE
            setPadding(0, dp(10), 0, 0)
        }

        column.addView(row)
        column.addView(answerView)
        card.addView(column)

        card.isClickable = true
        card.setOnClickListener {
            val open = answerView.visibility == View.VISIBLE
            answerView.visibility = if (open) View.GONE else View.VISIBLE
            marker.text = if (open) "+" else "\u2212"
        }
        return card
    }
}
