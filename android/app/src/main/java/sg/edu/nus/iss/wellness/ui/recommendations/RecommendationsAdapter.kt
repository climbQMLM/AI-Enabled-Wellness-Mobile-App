package sg.edu.nus.iss.wellness.ui.recommendations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import sg.edu.nus.iss.wellness.databinding.ItemRecommendationBinding
import sg.edu.nus.iss.wellness.model.Recommendation

/**
 * RecyclerView adapter for the recommendations list.
 */
class RecommendationsAdapter : RecyclerView.Adapter<RecommendationsAdapter.ViewHolder>() {

    private val items = mutableListOf<Recommendation>()

    fun submitList(list: List<Recommendation>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    inner class ViewHolder(private val binding: ItemRecommendationBinding)
        : RecyclerView.ViewHolder(binding.root) {

        fun bind(rec: Recommendation) {
            binding.tvDate.text = rec.recDate
            binding.tvContent.text = rec.content
            binding.tvMeta.text = "by ${rec.createdBy} · ${rec.type}"
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecommendationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount() = items.size
}
