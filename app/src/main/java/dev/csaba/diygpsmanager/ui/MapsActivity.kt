package dev.csaba.diygpsmanager.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.os.ConfigurationCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import dev.csaba.diygpsmanager.ApplicationSingleton
import dev.csaba.diygpsmanager.R
import dev.csaba.diygpsmanager.data.Report
import dev.csaba.diygpsmanager.viewmodel.MapsViewModel
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1
        private const val GOOGLEPLEX_LAT = 37.422160
        private const val GOOGLEPLEX_LON = -122.084270
    }

    private lateinit var map: GoogleMap
    private lateinit var viewModel: MapsViewModel
    private var isRestore = false
    private var lastPinId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isRestore = savedInstanceState != null
        setContentView(R.layout.activity_maps)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val uiSettings = map.uiSettings
        uiSettings.isZoomControlsEnabled = true
        uiSettings.isCompassEnabled = true
        uiSettings.isMapToolbarEnabled = true
        uiSettings.setAllGesturesEnabled(true)

        setMapLongClick(map)
        setMarkerClick(map)
        enableMyLocation()

        val appSingleton = application as ApplicationSingleton
        val assetId = intent.getStringExtra("assetId")
        if (assetId != null && appSingleton.firestore != null) {
            val lookBackMinutes = intent.getIntExtra("lookBackMinutes", 10)
            Timber.d("onMapReady for $assetId with look back $lookBackMinutes mins")
            val lookBackDate = Date(System.currentTimeMillis() - 60 * lookBackMinutes * 1000)
            viewModel = MapsViewModel(appSingleton.firestore!!, assetId, lookBackDate)
            viewModel.reportList.observe(this) {
                addPins(it)
            }
        }
    }

    fun addPins(pins: List<Report>) {
        Timber.d("Received location list of size ${pins.size}")
        if (pins.isEmpty())
            return

        val options = PolylineOptions()
        options.color(Color.RED)

        var previousLocation = LatLng(.0, .0)
        var pinId = ""
        val shouldSkip = if (lastPinId.isBlank()) false else pins.any { it.id == lastPinId }
        var found = false
        var added = 0
        for (pin in pins) {
            val latLng = LatLng(pin.lat, pin.lon)
            pinId = pin.id

            // Looks like we receive the whole array over and over
            // So we need to avoid adding the same pins multiple times
            // We skip over what we already saw
            if (shouldSkip && !found) {
                if (pin.id == pinId) {
                    found = true
                }
                continue
            }

            // Don't record too close consecutive markers (avoid unnecessary crowding)
            // 10^-5 is about 1.1m (https://en.wikipedia.org/wiki/Decimal_degrees)
            if (Math.abs(previousLocation.latitude - latLng.latitude) < 1e-5 &&
                Math.abs(previousLocation.longitude - latLng.longitude) < 1e-5)
            {
                continue
            }
            options.add(latLng)
            added += 1
            previousLocation = latLng

            val currentLocale = ConfigurationCompat.getLocales(resources.configuration)[0]
            val dateTimeFormat = SimpleDateFormat("HH:mm:ss, yyyy-MM-dd", currentLocale)
            val dateTimeString = dateTimeFormat.format(pin.created)
            val title = "${pin.battery}%, ${pin.speed}"
            val marker = MarkerOptions().position(latLng).title(title).snippet(dateTimeString)
            map.addMarker(marker)
        }
        lastPinId = pinId

        if (added > 0) {
            map.addPolyline(options)
        }
    }

    // Initializes contents of Activity's standard options menu. Only called the first time options
    // menu is displayed.
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.map_options, menu)
        return true
    }

    // Called whenever an item in your options menu is selected.
    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        // Change the map type based on the user's selection.
        R.id.normal_map -> {
            map.mapType = GoogleMap.MAP_TYPE_NORMAL
            true
        }
        R.id.hybrid_map -> {
            map.mapType = GoogleMap.MAP_TYPE_HYBRID
            true
        }
        R.id.satellite_map -> {
            map.mapType = GoogleMap.MAP_TYPE_SATELLITE
            true
        }
        R.id.terrain_map -> {
            map.mapType = GoogleMap.MAP_TYPE_TERRAIN
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // Called when user makes a long press gesture on the map.
    private fun setMapLongClick(map: GoogleMap) {
        map.setOnMapLongClickListener { latLng ->
            val navigationIntentUri: Uri =
                Uri.parse("google.navigation:q=${latLng.latitude},${latLng.longitude}")
            val mapIntent = Intent(Intent.ACTION_VIEW, navigationIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent.setClassName("com.google.android.apps.maps",
                "com.google.android.maps.MapsActivity")
            if (mapIntent.resolveActivity(packageManager) != null) {
                startActivity(mapIntent)
            }
        }
    }

    private fun setMarkerClick(map: GoogleMap) {
        map.setOnMarkerClickListener { marker ->
            marker.showInfoWindow()
            true
        }
    }

    // Checks that users have given permission
    private fun isPermissionGranted() : Boolean {
       return ContextCompat.checkSelfPermission(
            this,
           Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    // Checks if users have given their location and sets location enabled if so.
    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        if (isPermissionGranted()) {
            map.isMyLocationEnabled = true

            if (!isRestore) {
                val locationManager =
                    getSystemService(LOCATION_SERVICE) as LocationManager
                val locationProvider = LocationManager.NETWORK_PROVIDER
                @SuppressLint("MissingPermission") val lastKnownLocation =
                    locationManager.getLastKnownLocation(locationProvider)

                // Default to the lattitude and longitude of the Googleplex if no location.
                val userLat = lastKnownLocation?.latitude ?: GOOGLEPLEX_LAT
                val userLong = lastKnownLocation?.longitude ?: GOOGLEPLEX_LON
                val userLatLng = LatLng(userLat, userLong)
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf<String>(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }

    // Callback for the result from requesting permissions.
    // This method is invoked for every call on requestPermissions(android.app.Activity, String[],
    // int).
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Check if location permissions are granted and if so enable the
        // location data layer.
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.contains(PackageManager.PERMISSION_GRANTED)) {
                enableMyLocation()
            }
        }
    }
}
