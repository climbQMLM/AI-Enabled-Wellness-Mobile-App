package sg.edu.nus.iss.wellness.ui.wellness

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.data.SessionManager
import sg.edu.nus.iss.wellness.databinding.ActivityWellnessFormBinding
import sg.edu.nus.iss.wellness.model.WellnessCreateRequest
import sg.edu.nus.iss.wellness.model.WellnessLog
import sg.edu.nus.iss.wellness.model.WellnessPatchRequest
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.network.WellnessApi

/**
 *
 * Form page for creating or editing a wellness log.
 *
 * Two modes, determined by Intent extra:
 *   - No EXTRA_LOG_JSON → create mode (POST /api/wellness)
 *   - Has EXTRA_LOG_JSON → edit mode (PUT /api/wellness/{id}, PATCH semantics)
 *
 * Form includes only common manually-entered fields (steps, calories, sleep, HRV avg, HR).
 * Fine-grained RingConn fields come via the import endpoint, not manual entry here.
 */
class WellnessFormActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LOG_JSON = "extra_log_json"
    }

    private lateinit var binding: ActivityWellnessFormBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var wellnessApi: WellnessApi

    /** Original log in edit mode; null in create mode */
    private var existingLog: WellnessLog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWellnessFormBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)
        wellnessApi = ApiClient.getInstance(sessionManager).create(WellnessApi::class.java)

        // Parse Intent: determine create vs edit mode
        val logJson = intent.getStringExtra(EXTRA_LOG_JSON)
        if (logJson != null) {
            existingLog = Gson().fromJson(logJson, WellnessLog::class.java)
            prefillForm(existingLog!!)
            binding.tvTitle.text = "Edit Record"
            // Date is immutable in edit mode — it is the unique key
            binding.tilLogDate.isEnabled = false
        } else {
            binding.tvTitle.text = "New Record"
            // Default to today's date
            binding.etLogDate.setText(java.time.LocalDate.now().toString())
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    /** Pre-fill form fields with existing values in edit mode */
    private fun prefillForm(log: WellnessLog) {
        binding.etLogDate.setText(log.logDate)
        log.steps?.let { binding.etSteps.setText(it.toString()) }
        log.caloriesKcal?.let { binding.etCalories.setText(it.toString()) }
        log.timeAsleepMin?.let { binding.etSleepMin.setText(it.toString()) }
        log.hrvAvg?.let { binding.etHrvAvg.setText(it.toString()) }
        log.hrMin?.let { binding.etHrMin.setText(it.toString()) }
        log.hrAvg?.let { binding.etHrAvg.setText(it.toString()) }
    }

    private fun save() {
        val logDate = binding.etLogDate.text.toString().trim()
        if (logDate.isEmpty()) { toast("Date is required"); return }

        // Read each field; blank string → null (not sent to backend = no change)
        val steps        = binding.etSteps.text.toString().trim().toIntOrNull()
        val calories     = binding.etCalories.text.toString().trim().toIntOrNull()
        val sleepMin     = binding.etSleepMin.text.toString().trim().toIntOrNull()
        val hrvAvg       = binding.etHrvAvg.text.toString().trim().toIntOrNull()
        val hrMin        = binding.etHrMin.text.toString().trim().toIntOrNull()
        val hrAvg        = binding.etHrAvg.text.toString().trim().toIntOrNull()

        setLoading(true)
        lifecycleScope.launch {
            try {
                val success = if (existingLog == null) {
                    // Create: POST /api/wellness
                    val req = WellnessCreateRequest(
                        logDate = logDate,
                        steps = steps,
                        caloriesKcal = calories,
                        timeAsleepMin = sleepMin,
                        hrvAvg = hrvAvg,
                        hrMin = hrMin,
                        hrAvg = hrAvg
                    )
                    val resp = wellnessApi.create(req)
                    if (resp.isSuccessful) true
                    else { toast(parseError(resp.errorBody()?.string()) ?: "Create failed (${resp.code()})"); false }
                } else {
                    // Edit: PUT /api/wellness/{id} (PATCH semantics)
                    val req = WellnessPatchRequest(
                        steps = steps,
                        caloriesKcal = calories,
                        timeAsleepMin = sleepMin,
                        hrvAvg = hrvAvg,
                        hrMin = hrMin,
                        hrAvg = hrAvg
                    )
                    val resp = wellnessApi.patch(existingLog!!.id, req)
                    if (resp.isSuccessful) true
                    else { toast(parseError(resp.errorBody()?.string()) ?: "Update failed (${resp.code()})"); false }
                }

                if (success) {
                    toast(if (existingLog == null) "Created" else "Updated")
                    finish() // Return to list; onResume will auto-refresh
                }
            } catch (e: Exception) {
                toast("Network error: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSave.isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun parseError(json: String?): String? = try {
        org.json.JSONObject(json ?: "").getString("error")
    } catch (e: Exception) { null }
}
