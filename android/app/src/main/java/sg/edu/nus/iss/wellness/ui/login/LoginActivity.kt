package sg.edu.nus.iss.wellness.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import sg.edu.nus.iss.wellness.data.SessionManager
import sg.edu.nus.iss.wellness.databinding.ActivityLoginBinding
import sg.edu.nus.iss.wellness.model.LoginRequest
import sg.edu.nus.iss.wellness.model.RegisterRequest
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.network.AuthApi
import sg.edu.nus.iss.wellness.ui.main.MainActivity

/**
 *
 * Login / Register page. Tab-switch between modes, sharing a single Activity.
 * On success: save token to SessionManager, navigate to MainActivity, and finish.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var sessionManager: SessionManager
    private lateinit var authApi: AuthApi

    /** true = Login mode, false = Register mode */
    private var isLoginMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionManager = SessionManager(this)

        // Already logged in — skip straight to MainActivity
        if (sessionManager.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
            return
        }

        authApi = ApiClient.getInstance(sessionManager).create(AuthApi::class.java)

        // Toggle login / register mode
        binding.btnSwitchMode.setOnClickListener {
            isLoginMode = !isLoginMode
            updateModeUI()
        }

        binding.btnSubmit.setOnClickListener {
            if (isLoginMode) doLogin() else doRegister()
        }
    }

    private fun updateModeUI() {
        if (isLoginMode) {
            binding.tvTitle.text = "Login"
            binding.btnSubmit.text = "Login"
            binding.btnSwitchMode.text = "No account? Register"
            binding.tilDisplayName.visibility = View.GONE
            binding.tvDemoHint.visibility = View.VISIBLE
        } else {
            binding.tvTitle.text = "Register"
            binding.btnSubmit.text = "Register"
            binding.btnSwitchMode.text = "Already have an account? Login"
            binding.tilDisplayName.visibility = View.VISIBLE
            binding.tvDemoHint.visibility = View.GONE
        }
    }

    private fun doLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) {
            toast("Please enter email and password"); return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val resp = authApi.login(LoginRequest(email, password))
                if (resp.isSuccessful && resp.body() != null) {
                    onAuthSuccess(resp.body()!!)
                } else {
                    // Backend returns {"error":"..."} — parse it
                    val msg = parseError(resp.errorBody()?.string()) ?: "Login failed (${resp.code()})"
                    toast(msg)
                }
            } catch (e: Exception) {
                toast("Network error: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun doRegister() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val displayName = binding.etDisplayName.text.toString().trim().ifEmpty { null }
        if (email.isEmpty() || password.isEmpty()) {
            toast("Please enter email and password"); return
        }

        setLoading(true)
        lifecycleScope.launch {
            try {
                val resp = authApi.register(RegisterRequest(email, password, displayName))
                if (resp.isSuccessful && resp.body() != null) {
                    onAuthSuccess(resp.body()!!)
                } else {
                    val msg = parseError(resp.errorBody()?.string()) ?: "Registration failed (${resp.code()})"
                    toast(msg)
                }
            } catch (e: Exception) {
                toast("Network error: ${e.message}")
            } finally {
                setLoading(false)
            }
        }
    }

    private fun onAuthSuccess(auth: sg.edu.nus.iss.wellness.model.AuthResponse) {
        sessionManager.saveToken(auth.token)
        sessionManager.saveEmail(auth.user.email)
        sessionManager.saveDisplayName(auth.user.displayName)
        // Navigate to MainActivity, clear back stack so back button cannot return here
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnSubmit.isEnabled = !loading
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    /** Parse the backend {"error":"xxx"} error response */
    private fun parseError(json: String?): String? {
        if (json == null) return null
        return try {
            org.json.JSONObject(json).getString("error")
        } catch (e: Exception) { null }
    }
}
