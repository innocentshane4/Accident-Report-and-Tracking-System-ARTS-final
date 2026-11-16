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
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NearbyUnitsFragment : Fragment() {
    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        i.inflate(R.layout.fragment_nearby_units, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_back).setOnClickListener { findNavController().navigateUp() }
        val container = view.findViewById<LinearLayout>(R.id.units_container)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_no_units)
        
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val docs = FirebaseFirestore.getInstance().collection("contractedServices")
                    .whereEqualTo("active", true).get().await()
                
                if (!isAdded) return@launch
                
                if (docs.isEmpty) { 
                    tvEmpty.visibility = View.VISIBLE
                    return@launch 
                }
                
                tvEmpty.visibility = View.GONE
                val ctx = context ?: return@launch
                val inflater = LayoutInflater.from(ctx)
                
                docs.documents.forEach { doc ->
                    val name = doc.getString("name") ?: return@forEach
                    val type = doc.getString("serviceType") ?: ""
                    val phone = doc.getString("phone") ?: ""
                    
                    val card = inflater.inflate(R.layout.item_unit_card, container, false)
                    card.findViewById<TextView>(R.id.tv_unit_name).text = name
                    card.findViewById<TextView>(R.id.tv_unit_type).text = type
                    card.findViewById<TextView>(R.id.tv_unit_phone).text = phone
                    card.setOnClickListener {
                        if (phone.isNotBlank()) {
                            try {
                                startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:$phone")))
                            } catch (e: Exception) {
                                // Ignore if dialer not found
                            }
                        }
                    }
                    container.addView(card)
                }
            } catch (e: Exception) {
                if (isAdded) {
                    tvEmpty.visibility = View.VISIBLE
                    tvEmpty.text = "Could not load units: ${e.message}"
                }
            }
        }
    }
}
