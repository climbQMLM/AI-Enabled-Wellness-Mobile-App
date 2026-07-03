package sg.edu.nus.iss.wellness.ui.main

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import sg.edu.nus.iss.wellness.R
import sg.edu.nus.iss.wellness.data.SessionManager
import sg.edu.nus.iss.wellness.databinding.ActivityMainBinding
import sg.edu.nus.iss.wellness.network.ApiClient
import sg.edu.nus.iss.wellness.ui.chat.ChatFragment
import sg.edu.nus.iss.wellness.ui.dashboard.DashboardFragment
import sg.edu.nus.iss.wellness.ui.importcsv.ImportFragment
import sg.edu.nus.iss.wellness.ui.login.LoginActivity
import sg.edu.nus.iss.wellness.ui.recommendations.RecommendationsFragment
import sg.edu.nus.iss.wellness.ui.wellness.WellnessFragment

/**
 *
 * Main container after login.
 * BottomNavigationView controls switching between 5 Fragments:
 *   Dashboard → Wellness → Import → Chat → Recommendations
 *
 * Uses show/hide instead of replace to switch Fragments,
 * so each Fragment's state (e.g. scroll position) is preserved across tabs.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var sessionManager: SessionManager

    // Five Fragment instances, lazily created on first tab switch
    private val dashboardFragment by lazy { DashboardFragment() }
    private val wellnessFragment by lazy { WellnessFragment() }
    private val importFragment by lazy { ImportFragment() }
    private val chatFragment by lazy { ChatFragment() }
    private val recommendationsFragment by lazy { RecommendationsFragment() }

    private var activeFragment: Fragment = dashboardFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sessionManager = SessionManager(this)

        // Check login state: redirect to login if no token
        if (!sessionManager.isLoggedIn()) {
            goToLogin(); return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show Dashboard Fragment initially
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, dashboardFragment, "dashboard")
            .commit()

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard      -> switchTo(dashboardFragment, "dashboard")
                R.id.nav_wellness       -> switchTo(wellnessFragment, "wellness")
                R.id.nav_import         -> switchTo(importFragment, "import")
                R.id.nav_chat           -> switchTo(chatFragment, "chat")
                R.id.nav_recommendations -> switchTo(recommendationsFragment, "recs")
            }
            true
        }
    }

    /**
     * Switch to the specified Fragment:
     *   - If Fragment not yet added to FragmentManager, add it first
     *   - Otherwise show the target Fragment and hide the current one
     */
    private fun switchTo(target: Fragment, tag: String) {
        val transaction = supportFragmentManager.beginTransaction()
        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target, tag)
        }
        transaction.hide(activeFragment).show(target).commit()
        activeFragment = target
    }

    /** Logout: clear token → reset ApiClient → go to login */
    fun logout() {
        sessionManager.clear()
        ApiClient.reset()
        goToLogin()
    }

    private fun goToLogin() {
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
