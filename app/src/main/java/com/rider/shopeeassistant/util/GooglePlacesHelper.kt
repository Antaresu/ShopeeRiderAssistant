package com.rider.shopeeassistant.util

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.maps.model.LatLng
import java.io.IOException
import java.util.Locale

data class SelectedArea(
    val name: String,
    val latLng: LatLng,
    val isBanned: Boolean,
    val radiusMeters: Double = 3000.0,
    val keywords: List<String>
)

object GooglePlacesHelper {

    // Default Pre-loaded Manila Google Zones
    val DEFAULT_AREAS = listOf(
        SelectedArea(
            name = "Tondo, Manila (Banned Zone)",
            latLng = LatLng(14.6186, 120.9675),
            isBanned = true,
            radiusMeters = 3500.0,
            keywords = listOf("Tondo", "Gagalangin", "Parola", "Isla Puting Bato", "Herbosa", "Tayuman West")
        ),
        SelectedArea(
            name = "Manila Proper (Allowed Zone)",
            latLng = LatLng(14.5995, 120.9842),
            isBanned = false,
            radiusMeters = 5000.0,
            keywords = listOf("Manila", "Malate", "Ermita", "Sampaloc", "Paco", "Binondo", "Quiapo", "San Miguel", "Santa Ana")
        )
    )

    /**
     * Resolves an address string to LatLng using Google Geocoder
     */
    fun geocodeAddress(context: Context, addressText: String): LatLng? {
        val geocoder = Geocoder(context, Locale.getDefault())
        return try {
            val results: List<Address>? = geocoder.getFromLocationName(addressText, 1)
            if (!results.isNullOrEmpty()) {
                val loc = results[0]
                LatLng(loc.latitude, loc.longitude)
            } else null
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Checks if the scanned address matches any Banned or Allowed Google Area
     */
    fun checkAreaMatch(
        context: Context,
        extractedAddress: String,
        configuredAreas: List<SelectedArea>
    ): Pair<Boolean, SelectedArea?> {
        val lowerText = extractedAddress.lowercase()

        // 1. First check quick keyword match from Google Places
        for (area in configuredAreas) {
            for (kw in area.keywords) {
                if (lowerText.contains(kw.lowercase())) {
                    return Pair(area.isBanned, area)
                }
            }
        }

        // 2. Geocode if text has full street name
        val coords = geocodeAddress(context, extractedAddress)
        if (coords != null) {
            for (area in configuredAreas) {
                val distance = calculateDistanceMeters(coords, area.latLng)
                if (distance <= area.radiusMeters) {
                    return Pair(area.isBanned, area)
                }
            }
        }

        return Pair(false, null)
    }

    private fun calculateDistanceMeters(p1: LatLng, p2: LatLng): Float {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            p1.latitude, p1.longitude,
            p2.latitude, p2.longitude,
            results
        )
        return results[0]
    }
}
