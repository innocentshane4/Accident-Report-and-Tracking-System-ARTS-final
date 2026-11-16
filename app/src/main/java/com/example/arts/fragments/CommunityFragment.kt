package com.example.arts.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.arts.R
import com.example.arts.utils.TimeFormat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Community feed — the 20 most recent incidents across all users.
 *
 * FIX: The previous version queried incidents without a reporterId filter,
 * which returned PERMISSION DENIED under the old Firestore rules (the list
 * rule required resource.data.reporterId == auth.uid, so other users'
 * documents were rejected). The rules have been updated to allow any
 * signed-in user to list incidents. A sign-in guard is also added here
 * so the query is never attempted when no user is authenticated.
 */
class CommunityFragment : Fragment() {

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_community, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_back).setOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        val container = view.findViewById<LinearLayout>(R.id.feed_container)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_no_feed)

        // Guard: Firestore rules require authentication for all reads.
        if (FirebaseAuth.getInstance().currentUser == null) {
            tvEmpty.text = getString(R.string.error_not_signed_in)
            tvEmpty.visibility = View.VISIBLE
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val docs = FirebaseFirestore.getInstance()
                    .collection("incidents")
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()

                if (!isAdded) return@launch

                if (docs.isEmpty) {
                    tvEmpty.visibility = View.VISIBLE
                    return@launch
                }

                tvEmpty.visibility = View.GONE
                val ctx = context ?: return@launch
                val inflater = LayoutInflater.from(ctx)

                docs.documents.forEach { doc ->
                    val title = doc.getString("title") ?: return@forEach
                    val type = doc.getString("type") ?: ""
                    val priority = doc.getString("priority") ?: ""
                    val location = doc.getString("location") ?: ""
                    val createdAt = doc.getString("createdAt") ?: ""

                    val card = inflater.inflate(R.layout.item_feed_card, container, false)
                    card.findViewById<TextView>(R.id.tv_feed_title).text = title
                    card.findViewById<TextView>(R.id.tv_feed_type).text = "$type - $priority priority"
                    card.findViewById<TextView>(R.id.tv_feed_location).text = location
                    card.findViewById<TextView>(R.id.tv_feed_time).text = TimeFormat.ago(createdAt)
                    container.addView(card)
                }
            } catch (e: Exception) {
                if (isAdded) {
                    tvEmpty.text = getString(R.string.error_loading_feed, e.message ?: "")
                    tvEmpty.visibility = View.VISIBLE
                }
            }
        }
    }
}
