package sg.edu.nus.iss.wellness.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.data.SessionManager
import sg.edu.nus.iss.wellness.databinding.FragmentChatBinding
import sg.edu.nus.iss.wellness.model.ChatMessage
import sg.edu.nus.iss.wellness.model.ChatRequest
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.network.ChatApi
import sg.edu.nus.iss.wellness.ui.main.MainActivity

/**
 *
 * Chat page. Message list + bottom input field + send button.
 *   - Load history on enter (GET /api/chat/history)
 *   - Optimistically show user bubble, then call API, then show AI reply
 *   - Ollama inference is slow; disable input and button while waiting, show loading
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var chatApi: ChatApi
    private lateinit var adapter: ChatAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        chatApi = ApiClient.getInstance(sessionManager).create(ChatApi::class.java)

        adapter = ChatAdapter()
        val layoutManager = LinearLayoutManager(requireContext())
        layoutManager.stackFromEnd = true  // New messages appear at the bottom (like messaging apps)
        binding.recyclerView.layoutManager = layoutManager
        binding.recyclerView.adapter = adapter

        binding.btnSend.setOnClickListener { sendMessage() }

        loadHistory()
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            try {
                val resp = chatApi.history()
                if (resp.isSuccessful) {
                    adapter.submitList(resp.body() ?: emptyList())
                    scrollToBottom()
                } else if (resp.code() == 401) {
                    (activity as? MainActivity)?.logout()
                }
            } catch (e: Exception) {
                // History load failure does not block the user from sending new messages
            }
        }
    }

    private fun sendMessage() {
        val text = binding.etMessage.text.toString().trim()
        if (text.isEmpty()) return

        // 1. Immediately show user message in UI (optimistic update)
        adapter.addMessage(ChatMessage(0, "user", text, null))
        binding.etMessage.text?.clear()
        scrollToBottom()

        setLoading(true)
        lifecycleScope.launch {
            try {
                val resp = chatApi.chat(ChatRequest(text))
                if (resp.isSuccessful && resp.body() != null) {
                    // 2. Show AI reply after API returns
                    adapter.addMessage(ChatMessage(0, "assistant", resp.body()!!.reply, null))
                    scrollToBottom()
                } else {
                    if (resp.code() == 401) (activity as? MainActivity)?.logout()
                    else Toast.makeText(context, "Send failed (${resp.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun scrollToBottom() {
        binding.recyclerView.postDelayed({
            if (adapter.itemCount > 0) {
                binding.recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
            }
        }, 100)
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSend.isEnabled = !loading
        binding.etMessage.isEnabled = !loading
        if (!loading) binding.etMessage.requestFocus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
