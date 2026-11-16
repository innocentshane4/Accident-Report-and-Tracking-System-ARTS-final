package com.example.arts.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.arts.R
import com.example.arts.data.ChatMessage

class ChatAdapter(private val messages: MutableList<ChatMessage> = mutableListOf()) :
    RecyclerView.Adapter<ChatAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val bubble: TextView = view.findViewById(R.id.tv_bubble)
        val time: TextView = view.findViewById(R.id.tv_time)
        val container: LinearLayout = view as LinearLayout
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false))

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val msg = messages[position]
        val ctx = holder.itemView.context
        holder.bubble.text = msg.text
        holder.time.text = msg.time

        val density = holder.itemView.resources.displayMetrics.density
        val radius = 12f * density

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            if (msg.fromUser) {
                setColor(ContextCompat.getColor(ctx, R.color.accent_green))
                cornerRadii = floatArrayOf(radius, radius, radius, radius, 0f, 0f, radius, radius)
            } else {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(radius, radius, radius, radius, radius, radius, 0f, 0f)
                setStroke((1 * density).toInt(), ContextCompat.getColor(ctx, R.color.divider))
            }
        }
        holder.bubble.background = bg
        holder.bubble.setTextColor(if (msg.fromUser) Color.WHITE else ContextCompat.getColor(ctx, R.color.text_primary))

        val bubbleParams = holder.bubble.layoutParams as? LinearLayout.LayoutParams
        val timeParams = holder.time.layoutParams as? LinearLayout.LayoutParams
        
        if (bubbleParams != null && timeParams != null) {
            if (msg.fromUser) {
                holder.container.gravity = Gravity.END
                bubbleParams.gravity = Gravity.END
                timeParams.gravity = Gravity.END
            } else {
                holder.container.gravity = Gravity.START
                bubbleParams.gravity = Gravity.START
                timeParams.gravity = Gravity.START
            }
            holder.bubble.layoutParams = bubbleParams
            holder.time.layoutParams = timeParams
        }
        
        holder.bubble.elevation = if (msg.fromUser) 2f * density else 4f * density
    }

    fun add(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun getHistory(): List<ChatMessage> = messages.toList()

    fun getLastUserMessage(): String? = messages.lastOrNull { it.fromUser }?.text
}
