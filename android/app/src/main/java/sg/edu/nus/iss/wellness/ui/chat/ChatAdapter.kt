package sg.edu.nus.iss.wellness.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import sg.edu.nus.iss.wellness.databinding.ItemChatMessageBinding
import sg.edu.nus.iss.wellness.model.ChatMessage

/**
 * RecyclerView adapter for chat messages.
 * role='user' → bubble on right; role='assistant' → bubble on left.
 */
class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {

    private val items = mutableListOf<ChatMessage>()

    fun submitList(list: List<ChatMessage>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    /** Append a single message (real-time add, no full list refresh needed) */
    fun addMessage(msg: ChatMessage) {
        items.add(msg)
        notifyItemInserted(items.size - 1)
    }

    inner class ViewHolder(private val binding: ItemChatMessageBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(msg: ChatMessage) {
            val isUser = msg.role == "user"
            binding.tvContent.text = msg.content

            // User message: right-aligned, blue background; AI message: left-aligned, grey background
            if (isUser) {
                binding.layoutUser.visibility = View.VISIBLE
                binding.layoutAssistant.visibility = View.GONE
                binding.tvUserContent.text = msg.content
            } else {
                binding.layoutUser.visibility = View.GONE
                binding.layoutAssistant.visibility = View.VISIBLE
                binding.tvAssistantContent.text = msg.content
            }
            // tvContent is a dummy view used in common binding; the actual display is via layoutUser/layoutAssistant
            binding.tvContent.visibility = View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}
