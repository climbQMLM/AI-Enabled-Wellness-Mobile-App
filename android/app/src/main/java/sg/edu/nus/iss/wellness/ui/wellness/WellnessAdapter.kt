package sg.edu.nus.iss.wellness.ui.wellness

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import sg.edu.nus.iss.wellness.databinding.ItemWellnessLogBinding
import sg.edu.nus.iss.wellness.model.WellnessLog

/**
 * RecyclerView adapter for wellness log list.
 * Tap row → open edit page; long-press → delete confirmation.
 */
class WellnessAdapter(
    private val onEdit: (WellnessLog) -> Unit,
    private val onDelete: (WellnessLog) -> Unit
) : RecyclerView.Adapter<WellnessAdapter.ViewHolder>() {

    private val items = mutableListOf<WellnessLog>()

    fun submitList(list: List<WellnessLog>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemWellnessLogBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(log: WellnessLog) {
            binding.tvDate.text = log.logDate
            binding.tvSource.text = if (log.source == "ringconn") "📱 RingConn" else "✏️ Manual"
            binding.tvReadiness.text = log.readinessScore?.let { "Readiness: $it" } ?: "Readiness: --"

            // Summary row: show only populated key fields
            val summary = buildString {
                log.steps?.let { append("Steps: $it  ") }
                log.hrvAvg?.let { append("HRV: $it ms  ") }
                log.timeAsleepMin?.let { append("Sleep: $it min") }
            }
            binding.tvSummary.text = summary.ifEmpty { "No data" }

            binding.root.setOnClickListener { onEdit(log) }
            binding.root.setOnLongClickListener { onDelete(log); true }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWellnessLogBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}
