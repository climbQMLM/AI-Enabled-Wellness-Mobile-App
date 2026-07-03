package sg.edu.nus.iss.wellness.ui.wellness

import android.app.AlertDialog
import android.content.Intent
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
import sg.edu.nus.iss.wellness.databinding.FragmentWellnessBinding
import sg.edu.nus.iss.wellness.model.WellnessLog
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.network.WellnessApi
import sg.edu.nus.iss.wellness.ui.main.MainActivity

/**
 *
 * Wellness log list page.
 *   - RecyclerView shows last 30 days (backend default)
 *   - FAB tap → create (open WellnessFormActivity without ID)
 *   - Tap item → edit (open WellnessFormActivity with log JSON)
 *   - Long-press item → delete confirmation dialog
 *   - Refresh on every onResume (auto-update after create/edit)
 */
class WellnessFragment : Fragment() {

    private var _binding: FragmentWellnessBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var wellnessApi: WellnessApi
    private lateinit var adapter: WellnessAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentWellnessBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        wellnessApi = ApiClient.getInstance(sessionManager).create(WellnessApi::class.java)

        adapter = WellnessAdapter(
            onEdit = { log -> openForm(log) },
            onDelete = { log -> confirmDelete(log) }
        )

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        binding.fab.setOnClickListener { openForm(null) }

        loadList()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) loadList()
    }

    private fun loadList() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val resp = wellnessApi.list()
                if (resp.isSuccessful) {
                    val logs = resp.body() ?: emptyList()
                    adapter.submitList(logs.reversed()) // Newest first
                    binding.tvEmpty.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
                } else {
                    if (resp.code() == 401) (activity as? MainActivity)?.logout()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Load failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    /**
     * Open create/edit form.
     * [log] = null → create mode; non-null → edit mode (pass log JSON via Intent)
     */
    private fun openForm(log: WellnessLog?) {
        val intent = Intent(requireContext(), WellnessFormActivity::class.java)
        if (log != null) {
            // Serialize the WellnessLog object to JSON and pass it via Intent
            val gson = com.google.gson.Gson()
            intent.putExtra(WellnessFormActivity.EXTRA_LOG_JSON, gson.toJson(log))
        }
        startActivity(intent)
    }

    private fun confirmDelete(log: WellnessLog) {
        AlertDialog.Builder(requireContext())
            .setTitle("Delete Record")
            .setMessage("Delete record for ${log.logDate}?")
            .setPositiveButton("Delete") { _, _ -> doDelete(log) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun doDelete(log: WellnessLog) {
        lifecycleScope.launch {
            try {
                val resp = wellnessApi.delete(log.id)
                if (resp.isSuccessful) {
                    Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show()
                    loadList()
                } else {
                    Toast.makeText(context, "Delete failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
