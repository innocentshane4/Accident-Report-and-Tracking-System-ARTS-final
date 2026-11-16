package com.example.arts.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.arts.R
import com.example.arts.utils.NotificationHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var fabReport: View

    // Safe as a property here: unlike a Fragment sitting in a back stack, an
    // Activity's registerForActivityResult always runs during construction,
    // before onCreate() — there is no scenario where this Activity gets
    // reused while already STARTED the way a Fragment can.
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Alerts still work inside the app either way; nothing to handle here. */ }

    private val hideNavFragments = setOf(
        R.id.loginFragment,
        R.id.registerFragment,
        R.id.aiAssistantFragment,
        R.id.reportAccidentFragment,
        R.id.incidentDetailFragment,
        R.id.medicalInfoFragment,
        R.id.settingsFragment,
        R.id.helpFragment,
        R.id.voiceReportFragment,
        R.id.weatherAlertFragment,
        R.id.nearbyUnitsFragment,
        R.id.safetyScoreFragment,
        R.id.helplinesFragment,
        R.id.communityFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        NotificationHelper.ensureChannel(this)
        requestNotificationPermissionIfNeeded()

        bottomNav = findViewById(R.id.bottom_nav)
        fabReport = findViewById(R.id.fab_report)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Correct usage of Navigation Component's setup
        bottomNav.setupWithNavController(navController)

        // FAB opens Report Accident screen
        fabReport.setOnClickListener {
            navController.navigate(R.id.reportAccidentFragment)
        }

        // Show/hide navigation bars based on destination
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id in hideNavFragments) {
                bottomNav.visibility = View.GONE
                fabReport.visibility = View.GONE
            } else {
                bottomNav.visibility = View.VISIBLE
                fabReport.visibility = View.VISIBLE
            }
        }
    }

    /** Android 13+ requires explicit runtime permission before any notification is shown. */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return navController.navigateUp() || super.onSupportNavigateUp()
    }
}
