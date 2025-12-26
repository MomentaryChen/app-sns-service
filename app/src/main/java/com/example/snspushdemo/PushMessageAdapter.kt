package com.example.snspushdemo

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.snspushdemo.R
import com.example.snspushdemo.model.PushMessage

/**
 * 推送訊息列表適配器
 */
class PushMessageAdapter(
    private val onItemClick: (PushMessage) -> Unit
) : ListAdapter<PushMessage, PushMessageAdapter.MessageViewHolder>(MessageDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_push_message, parent, false)
        return MessageViewHolder(view, onItemClick)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class MessageViewHolder(
        itemView: View,
        private val onItemClick: (PushMessage) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val titleTextView: TextView = itemView.findViewById(R.id.tvMessageTitle)
        private val bodyTextView: TextView = itemView.findViewById(R.id.tvMessageBody)
        private val timeTextView: TextView = itemView.findViewById(R.id.tvMessageTime)

        fun bind(message: PushMessage) {
            titleTextView.text = message.title
            bodyTextView.text = message.body
            timeTextView.text = message.getFormattedTime()

            itemView.setOnClickListener {
                onItemClick(message)
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<PushMessage>() {
        override fun areItemsTheSame(oldItem: PushMessage, newItem: PushMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: PushMessage, newItem: PushMessage): Boolean {
            return oldItem == newItem
        }
    }
}

