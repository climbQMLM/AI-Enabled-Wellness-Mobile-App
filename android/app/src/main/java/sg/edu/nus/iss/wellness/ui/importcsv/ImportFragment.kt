package sg.edu.nus.iss.wellness.ui.importcsv

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import sg.edu.nus.iss.wellness.data.SessionManager
import sg.edu.nus.iss.wellness.databinding.FragmentImportBinding
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.network.WellnessApi
import sg.edu.nus.iss.wellness.ui.main.MainActivity

/**
 *
 * RingConn CSV/ZIP import page.
 *   1. User taps "Pick Files" → system file picker (multi-select enabled)
 *   2. Selected filenames are listed
 *   3. Tap "Start Import" → multipart upload to POST /api/wellness/import
 *   4. Show import summary (importedDates / updated / inserted / skippedRows)
 *
 * Supports selecting multiple CSVs or a single ZIP, matching backend expectations.
 */
class ImportFragment : Fragment() {

    private var _binding: FragmentImportBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionManager: SessionManager
    private lateinit var wellnessApi: WellnessApi

    /** List of URIs the user has selected */
    private val selectedUris = mutableListOf<Uri>()

    // Multi-select file picker (supports .csv and .zip)
    private val pickFiles = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedUris.clear()
            val data = result.data
            // Multi-select: read from clipData; single-select: read from data
            if (data?.clipData != null) {
                for (i in 0 until data.clipData!!.itemCount) {
                    selectedUris.add(data.clipData!!.getItemAt(i).uri)
                }
            } else if (data?.data != null) {
                selectedUris.add(data.data!!)
            }
            updateSelectedFilesUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sessionManager = SessionManager(requireContext())
        wellnessApi = ApiClient.getInstance(sessionManager).create(WellnessApi::class.java)

        binding.btnPickFiles.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)  // Allow multiple selection
                // Suggest csv or zip — system does not enforce; backend validates
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "text/csv", "text/comma-separated-values",
                    "application/zip", "application/x-zip-compressed", "*/*"
                ))
            }
            pickFiles.launch(Intent.createChooser(intent, "Select CSV or ZIP file"))
        }

        binding.btnImport.setOnClickListener { doImport() }
    }

    private fun updateSelectedFilesUI() {
        if (selectedUris.isEmpty()) {
            binding.tvSelectedFiles.text = "No files selected"
            binding.btnImport.isEnabled = false
            return
        }
        val names = selectedUris.map { getFileName(it) }
        binding.tvSelectedFiles.text = "Selected:\n" + names.joinToString("\n")
        binding.btnImport.isEnabled = true
        // Clear previous import result when new files are selected
        binding.tvImportResult.visibility = View.GONE
    }

    private fun doImport() {
        if (selectedUris.isEmpty()) return
        setLoading(true)

        lifecycleScope.launch {
            try {
                // Read each URI as bytes and build a MultipartBody.Part
                val parts = selectedUris.mapNotNull { uri ->
                    val fileName = getFileName(uri)
                    val bytes = requireContext().contentResolver.openInputStream(uri)?.readBytes()
                        ?: return@mapNotNull null
                    val mediaType = if (fileName.endsWith(".zip"))
                        "application/zip".toMediaTypeOrNull()
                    else
                        "text/csv".toMediaTypeOrNull()
                    val requestBody = bytes.toRequestBody(mediaType)
                    // Part name "files" must match backend @RequestParam("files")
                    MultipartBody.Part.createFormData("files", fileName, requestBody)
                }

                if (parts.isEmpty()) {
                    Toast.makeText(context, "Failed to read files", Toast.LENGTH_SHORT).show()
                    setLoading(false); return@launch
                }

                val resp = wellnessApi.import(parts)
                if (resp.isSuccessful && resp.body() != null) {
                    val result = resp.body()!!
                    val dateRange = if (result.dateRange.size == 2)
                        "${result.dateRange[0]} ~ ${result.dateRange[1]}"
                    else "N/A"
                    binding.tvImportResult.text = """
                        ✅ Import Complete
                        Date range: $dateRange
                        Total days: ${result.importedDates}
                        Inserted: ${result.inserted}  Updated: ${result.updated}
                        Skipped rows: ${result.skippedRows}
                    """.trimIndent()
                    binding.tvImportResult.visibility = View.VISIBLE
                    // Clear selection to prevent duplicate import
                    selectedUris.clear()
                    updateSelectedFilesUI()
                } else {
                    if (resp.code() == 401) (activity as? MainActivity)?.logout()
                    else Toast.makeText(context, "Import failed (${resp.code()})", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                setLoading(false)
            }
        }
    }

    /** Get filename from URI (used for Content-Disposition and UI display) */
    private fun getFileName(uri: Uri): String {
        var name = "file.csv"
        requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnImport.isEnabled = !loading
        binding.btnPickFiles.isEnabled = !loading
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
