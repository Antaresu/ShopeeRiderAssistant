package com.rider.shopeeassistant.ui

import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CircleOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.rider.shopeeassistant.R
import com.rider.shopeeassistant.util.GooglePlacesHelper
import com.rider.shopeeassistant.util.SelectedArea

class AreaSelectorActivity : AppCompatActivity(), OnMapReadyCallback {

    private var googleMap: GoogleMap? = null
    private val configuredAreas = mutableListOf<SelectedArea>()

    private lateinit var etSearch: EditText
    private lateinit var btnSearch: Button
    private lateinit var rbBanned: RadioButton
    private lateinit var btnSave: Button
    private lateinit var tvSummary: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_area_selector)

        etSearch = findViewById(R.id.etSearchPlace)
        btnSearch = findViewById(R.id.btnSearchPlace)
        rbBanned = findViewById(R.id.rbBanned)
        btnSave = findViewById(R.id.btnSaveAreas)
        tvSummary = findViewById(R.id.tvConfiguredAreasSummary)

        // Preload default areas (Tondo = Banned, Manila = Allowed)
        configuredAreas.addAll(GooglePlacesHelper.DEFAULT_AREAS)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.googleMapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        setupEventListeners()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap?.uiSettings?.isZoomControlsEnabled = true

        // Focus on Manila
        val manilaCenter = LatLng(14.5995, 120.9842)
        googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(manilaCenter, 12.5f))

        renderConfiguredAreasOnMap()

        // Allow user to tap anywhere on Google Maps to add a custom zone
        googleMap?.setOnMapClickListener { latLng ->
            val isBanned = rbBanned.isChecked
            val areaName = if (isBanned) "Custom Banned Zone" else "Custom Allowed Zone"
            val newArea = SelectedArea(
                name = areaName,
                latLng = latLng,
                isBanned = isBanned,
                radiusMeters = 2000.0,
                keywords = listOf(areaName)
            )
            configuredAreas.add(newArea)
            renderConfiguredAreasOnMap()
            Toast.makeText(this, "Naidagdag ang $areaName sa mapa!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderConfiguredAreasOnMap() {
        googleMap?.clear()

        for (area in configuredAreas) {
            val strokeColor = if (area.isBanned) Color.RED else Color.GREEN
            val fillColor = if (area.isBanned) Color.argb(70, 239, 68, 68) else Color.argb(60, 16, 185, 129)

            googleMap?.addMarker(
                MarkerOptions()
                    .position(area.latLng)
                    .title(area.name)
                    .snippet(if (area.isBanned) "Zero-Click (Ignore)" else "Allowed Zone")
            )

            googleMap?.addCircle(
                CircleOptions()
                    .center(area.latLng)
                    .radius(area.radiusMeters)
                    .strokeColor(strokeColor)
                    .strokeWidth(3f)
                    .fillColor(fillColor)
            )
        }

        updateSummaryText()
    }

    private fun setupEventListeners() {
        btnSearch.setOnClickListener {
            val query = etSearch.text.toString().trim()
            if (query.isNotEmpty()) {
                val coords = GooglePlacesHelper.geocodeAddress(this, "$query, Manila, Philippines")
                if (coords != null) {
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(coords, 14f))

                    val isBanned = rbBanned.isChecked
                    val newArea = SelectedArea(
                        name = query,
                        latLng = coords,
                        isBanned = isBanned,
                        radiusMeters = 2500.0,
                        keywords = listOf(query)
                    )
                    configuredAreas.add(newArea)
                    renderConfiguredAreasOnMap()
                    Toast.makeText(this, "Nahanap sa Google Maps: $query", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Hindi mahanap ang lugar sa Google Geocoder.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnSave.setOnClickListener {
            Toast.makeText(this, "Na-save na ang Google Area Rules para sa Shopee Driver!", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun updateSummaryText() {
        val bannedList = configuredAreas.filter { it.isBanned }.joinToString { it.name }
        val allowedList = configuredAreas.filter { !it.isBanned }.joinToString { it.name }
        tvSummary.text = "BANNED (Zero-Click): $bannedList\nALLOWED: $allowedList"
    }
}
