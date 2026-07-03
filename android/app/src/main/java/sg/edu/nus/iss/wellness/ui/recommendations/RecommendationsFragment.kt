package sg.edu.nus.iss.wellness.ui.recommendations

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
import sg.edu.nus.iss.wellness.databinding.FragmentRecommendationsBinding
import sg.edu.nus.iss.wellness.network.AgentApi
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.ui.main.MainActivity

/**
 *
 * Recommendations page.
 *   - RecyclerView shows historical agent recommendations
 *   - "Analyze Now" button → POST /api/agent/run (calls Ollama, may be slow)
 *   - Auto-refresh list after analysis completes
 */
class RecommendationsFragment : Fragment() {

    private var _binding: FragmentRecommendationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var agentApi: AgentApi
    private lateinit var adapter: RecommendationsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecommendationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        agentApi = ApiClient.getInstance(sessionManager).create(AgentApi::class.java)

        adapter = RecommendationsAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.btnRunAgent.setOnClickListener { runAgent() }

        loadRecommendations()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadRecommendations()
    }

    private fun loadRecommendations() {
        lifecycleScope.launch {
            try {
                val resp = agentApi.recommendations()
                if (resp.isSuccessful) {
                    val list = resp.body() ?: emptyList()
                    adapter.submitList(list)
                    binding.tvEmpty.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
                } else if (resp.code() == 401) {
                    (activity as? MainActivity)?.logout()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Manually trigger agentic analysis: fetch 7-day data → rule check → Ollama generates advice → save.
     * May take 10-60 s depending on Ollama inference speed; show loading during.
     */
    private fun runAgent() {
        setLoading(true)
        binding.tvStatus.text = "Analysing — Ollama may take a moment..."
        binding.tvStatus.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val resp = agentApi.run()
                if (resp.isSuccessful && resp.body() != null) {
                    binding.tvStatus.text = "✅ Analysis complete"
                    loadRecommendations() // Refresh list
                } else {
                    binding.tvStatus.text = "Analysis failed (${resp.code()})"
                    if (resp.code() == 401) (activity as? MainActivity)?.logout()
                }
            } catch (e: Exception) {
                binding.tvStatus.text = "Network error: ${e.message}"
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRunAgent.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
