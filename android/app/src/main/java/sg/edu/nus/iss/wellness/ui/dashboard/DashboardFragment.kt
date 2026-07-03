package sg.edu.nus.iss.wellness.ui.dashboard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.data.SessionManager
import sg.edu.nus.iss.wellness.databinding.FragmentDashboardBinding
import sg.edu.nus.iss.wellness.model.WellnessLog
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.network.WellnessApi
import sg.edu.nus.iss.wellness.ui.main.MainActivity

/**
 *
 * Dashboard: shows a 7-day health summary.
 *   - Latest day's readiness score (shown as a large number)
 *   - Three line charts: HRV, sleep duration, steps
 *   - Logout button in the top-right corner
 *
 * Data from GET /api/wellness?from=7 days ago&to=today
 */
class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var wellnessApi: WellnessApi

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        wellnessApi = ApiClient.getInstance(sessionManager).create(WellnessApi::class.java)

        // Greeting
        val name = sessionManager.getDisplayName() ?: sessionManager.getEmail() ?: "User"
        binding.tvWelcome.text = "Hello, $name"

        binding.btnLogout.setOnClickListener {
            (activity as? MainActivity)?.logout()
        }

        loadData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh when returning to Dashboard (e.g. after logging a new entry)
        loadData()
    }

    private fun loadData() {
        val today = java.time.LocalDate.now()
        val from = today.minusDays(6).toString()   // Last 7 days including today
        val to = today.toString()

        setLoading(true)
        lifecycleScope.launch {
            try {
                val resp = wellnessApi.list(from, to)
                if (resp.isSuccessful) {
                    val logs = resp.body() ?: emptyList()
                    updateUI(logs)
                } else {
                    // 401 means the token has expired
                    if (resp.code() == 401) (activity as? MainActivity)?.logout()
                    else Toast.makeText(context, "Load failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Network error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                setLoading(false)
            }
        }
    }

    private fun updateUI(logs: List<WellnessLog>) {
        if (logs.isEmpty()) {
            binding.tvReadiness.text = "--"
            binding.tvReadinessLabel.text = "No data yet — add a record or import RingConn data"
            return
        }

        // Latest day's readiness score (API returns ascending order, take the last)
        val latest = logs.last()
        val score = latest.readinessScore
        binding.tvReadiness.text = score?.toString() ?: "--"
        binding.tvReadinessLabel.text = when {
            score == null -> "Not enough data to compute Readiness"
            score >= 80   -> "Good condition ✓"
            score >= 60   -> "Fair — take it easy"
            else          -> "Fatigued — reduce training load"
        }

        // Prepare chart data (ascending date order, x-axis = date labels)
        val labels = logs.map { it.logDate.takeLast(5) } // "06-28" short format

        renderLineChart(
            chart = binding.chartHrv,
            label = "HRV (ms)",
            entries = logs.mapIndexedNotNull { i, l -> l.hrvAvg?.let { Entry(i.toFloat(), it.toFloat()) } },
            labels = labels,
            color = 0xFF4CAF50.toInt()
        )

        renderLineChart(
            chart = binding.chartSleep,
            label = "Sleep (min)",
            entries = logs.mapIndexedNotNull { i, l -> l.timeAsleepMin?.let { Entry(i.toFloat(), it.toFloat()) } },
            labels = labels,
            color = 0xFF2196F3.toInt()
        )

        renderLineChart(
            chart = binding.chartSteps,
            label = "Steps",
            entries = logs.mapIndexedNotNull { i, l -> l.steps?.let { Entry(i.toFloat(), it.toFloat()) } },
            labels = labels,
            color = 0xFFFF9800.toInt()
        )
    }

    /**
     * Render a single MPAndroidChart line chart.
     * Common settings: no legend, no description, x-axis labels at bottom, clean look.
     */
    private fun renderLineChart(
        chart: com.github.mikephil.charting.charts.LineChart,
        label: String,
        entries: List<Entry>,
        labels: List<String>,
        color: Int
    ) {
        if (entries.isEmpty()) {
            chart.clear(); chart.invalidate(); return
        }

        val dataSet = LineDataSet(entries, label).apply {
            this.color = color
            setCircleColor(color)
            lineWidth = 2f
            circleRadius = 3f
            setDrawValues(false)    // Don't draw value labels on each point — too cluttered
            mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        chart.apply {
            data = LineData(dataSet)
            description.isEnabled = false
            legend.isEnabled = false
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                valueFormatter = IndexAxisValueFormatter(labels)
                granularity = 1f
                setDrawGridLines(false)
                textSize = 9f
            }
            axisLeft.setDrawGridLines(false)
            axisRight.isEnabled = false
            setTouchEnabled(false)  // Dashboard charts are display-only; no gesture interaction needed
            animateX(600)
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
