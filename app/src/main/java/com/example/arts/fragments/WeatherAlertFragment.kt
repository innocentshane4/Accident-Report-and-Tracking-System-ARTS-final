package com.example.arts.fragments

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.arts.R
import com.example.arts.utils.GeocodeHelper
import com.example.arts.utils.LocationHelper
import com.example.arts.utils.MapHelper
import com.example.arts.utils.WeatherCodeMapper
import com.example.arts.utils.toast
import com.example.arts.viewmodels.WeatherState
import com.example.arts.viewmodels.WeatherViewModel
import kotlinx.coroutines.launch

class WeatherAlertFragment : Fragment() {

    private val weatherViewModel: WeatherViewModel by viewModels()

    // FIX: Registered in onCreate() to avoid IllegalStateException on Android 14+.
    private lateinit var locationPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        locationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                refreshWeather()
            } else {
                val root = view
                if (isAdded && root != null) {
                    showError(root, getString(R.string.error_location_permission))
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_weather_alert, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        view.findViewById<View>(R.id.btn_back).setOnClickListener { 
            if (isAdded) findNavController().navigateUp() 
        }

        weatherViewModel.weatherState.observe(viewLifecycleOwner) { state ->
            // Use the property 'view' to refer to the fragment's root view,
            // which is nullable and valid within viewLifecycleOwner.
            val root = this@WeatherAlertFragment.view ?: return@observe
            when (state) {
                is WeatherState.Loading -> showLoading(root)
                is WeatherState.Success -> showWeather(root, state)
                is WeatherState.Error -> showError(root, state.message)
                else -> {}
            }
        }

        val ctx = context ?: return
        if (MapHelper.hasLocationPermission(ctx)) {
            refreshWeather()
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun refreshWeather() {
        val root = view ?: return
        showLoading(root)
        val ctx = context ?: return
        
        viewLifecycleOwner.lifecycleScope.launch {
            val location = LocationHelper.currentLocation(ctx)
            if (location == null) {
                if (isAdded) showError(root, "Could not get your location. Make sure location services are enabled.")
                return@launch
            }
            
            weatherViewModel.loadWeather(location.latitude, location.longitude)
            
            val name = GeocodeHelper.reverseGeocode(ctx, location.latitude, location.longitude)
            if (isAdded) {
                root.findViewById<TextView>(R.id.tv_weather_location).text = name ?: "${location.latitude}, ${location.longitude}"
            }
        }
    }

    private fun showLoading(view: View) {
        view.findViewById<View>(R.id.layout_loading).visibility = View.VISIBLE
        view.findViewById<View>(R.id.card_weather).visibility = View.GONE
        view.findViewById<View>(R.id.card_advice).visibility = View.GONE
        view.findViewById<View>(R.id.tv_weather_error).visibility = View.GONE
    }

    private fun showWeather(view: View, state: WeatherState.Success) {
        view.findViewById<View>(R.id.layout_loading).visibility = View.GONE
        view.findViewById<View>(R.id.card_weather).visibility = View.VISIBLE
        view.findViewById<View>(R.id.card_advice).visibility = View.VISIBLE
        view.findViewById<View>(R.id.tv_weather_error).visibility = View.GONE

        val weather = state.weather
        val condition = WeatherCodeMapper.describe(weather.weathercode)
        view.findViewById<TextView>(R.id.tv_weather_condition).text = condition
        view.findViewById<TextView>(R.id.tv_temperature).text = "${weather.temperature}°C"
        view.findViewById<TextView>(R.id.tv_wind).text = "Wind: ${weather.windspeed} km/h"

        val advice = buildRoadAdvice(weather.weathercode, weather.windspeed, weather.temperature)
        view.findViewById<TextView>(R.id.tv_advice).text = advice
    }

    private fun showError(view: View, message: String) {
        view.findViewById<View>(R.id.layout_loading).visibility = View.GONE
        view.findViewById<View>(R.id.card_weather).visibility = View.GONE
        view.findViewById<View>(R.id.card_advice).visibility = View.GONE
        view.findViewById<TextView>(R.id.tv_weather_error).apply {
            text = message
            visibility = View.VISIBLE
        }
    }

    private fun buildRoadAdvice(code: Int, windspeed: Double, temperature: Double): String {
        val lines = mutableListOf<String>()
        when (code) {
            0, 1 -> lines.add("Roads are dry and visibility is good. Normal driving conditions.")
            2, 3 -> lines.add("Partly cloudy. Watch for sudden changes in visibility.")
            45, 48 -> lines.add("Foggy conditions. Reduce your speed and use fog lights.")
            51, 53, 55, 61, 63, 65, 80, 81, 82 -> {
                lines.add("Rain is falling. Roads may be slippery, especially at the start of a shower.")
                lines.add("Slow down, increase following distance, and avoid sudden braking.")
            }
            56, 57, 66, 67 -> lines.add("Freezing rain. Extreme caution. Black ice is likely.")
            71, 73, 75, 77, 85, 86 -> lines.add("Snow is falling. Roads may be icy. Drive slowly.")
            95, 96, 99 -> lines.add("Thunderstorm in your area. Pull over safely if lightning is close.")
            else -> lines.add("Check conditions before setting out.")
        }
        if (windspeed > 60) lines.add("Strong winds (${windspeed.toInt()} km/h). Be aware of high-sided vehicles.")
        if (temperature < 4) lines.add("Near-freezing temperatures. Watch for black ice.")
        return lines.joinToString("\n\n")
    }
}
