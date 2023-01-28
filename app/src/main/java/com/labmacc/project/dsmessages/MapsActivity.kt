// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.labmacc.project.dsmessages

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*

/**
 * An activity that displays a map showing the place at the device's current location.
 */
@Suppress("DEPRECATION")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private var backgroundPermission: Boolean = false
    private var notificationPermissionGranted: Boolean = false
    private lateinit var mService: MessageStore
    private var mBound: Boolean = false
    private val requestingLocationUpdates: Boolean = false
    private var map: GoogleMap? = null
    private var cameraPosition: CameraPosition? = null
    private lateinit var placed:MutableList<Int>

    // The entry point to the Fused Location Provider.
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback

    // A default location (Sydney, Australia) and default zoom to use when location permission is
    // not granted.
    private var locationPermissionGranted = false

    // The geographical location where the device is currently located. That is, the last-known
    // location retrieved by the Fused Location Provider.
    private var lastKnownLocation: Location? = null

    //MessageStore connection
    private val msgStrConnection : ServiceConnection = object: ServiceConnection{
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as MessageStore.LocalBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mBound = false
        }
    }

    //Add message Button
    private lateinit var button: AppCompatImageButton

    //Notification range
    private lateinit var range: Circle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION, Location::class.java)
                cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION, CameraPosition::class.java)

            }else{
                lastKnownLocation = savedInstanceState.getParcelable(KEY_LOCATION)
                cameraPosition = savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
            }

        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps)

        placed = mutableListOf()
        // Prompt the user for permission.
        //getPermissions()

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Build the map.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        //location Callback
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult
                    for (location in locationResult.locations){
                        lastKnownLocation = location
                        if (lastKnownLocation != null) {
                            val pos = LatLng(lastKnownLocation!!.latitude,
                                lastKnownLocation!!.longitude)
                            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                pos, DEFAULT_ZOOM.toFloat()))
                            if(mBound){
                                mService.lastLocation = pos
                            }
                            updateMap()
                        }
                    }
                }
            }

        //SignIn Activity
        val signIn = Intent(this,FirebaseUIActivity::class.java)
        this.startActivity(signIn)

        //Bind the msgStore Service
        Intent(this, MessageStore::class.java).also{
            intent -> bindService(intent,msgStrConnection, BIND_AUTO_CREATE)
            }

        button = findViewById(R.id.placeMessage)
        button.setOnClickListener{
            val writeMessage = Intent(this,WriteMessage::class.java)
            writeMessage.putExtra("Lat",lastKnownLocation?.latitude)
            writeMessage.putExtra("Lng",lastKnownLocation?.longitude)
            this.startActivity(writeMessage)
        }
    }

    @SuppressLint("NewApi")
    override fun onStart() {
        super.onStart()

        getPermissions()
    }

    /**
     * Saves the state of the map when the activity is paused.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        map?.let { map ->
            outState.putParcelable(KEY_CAMERA_POSITION, map.cameraPosition)
            outState.putParcelable(KEY_LOCATION, lastKnownLocation)
        }
        super.onSaveInstanceState(outState)
    }

    /**
     * Manipulates the map when it's available.
     * This callback is triggered when the map is ready to be used.
     */
    override fun onMapReady(map: GoogleMap) {
        this.map = map

        range = map.addCircle(CircleOptions().center(LatLng(0.0,0.0)).radius(10.0).strokeColor(Color.BLUE).fillColor(Color.TRANSPARENT))
//        getPermissions()
        // Turn on the My Location layer and the related control on the map.
        updateLocationUI()

        // Get the current location of the device and set the position of the map.
        //getDeviceLocation()

        createLocationRequest()

        startLocationUpdates()
    }

    override fun onResume(){
        super.onResume()
        if (requestingLocationUpdates) startLocationUpdates()
    }

    override fun onDestroy() {
        stopService(Intent(this,MessageStore::class.java))
        super.onDestroy()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if(locationPermissionGranted){
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }else{
            map?.moveCamera(CameraUpdateFactory.newCameraPosition(
                cameraPosition?:
                CameraPosition.fromLatLngZoom(LatLng(0.0,0.0),
                DEFAULT_ZOOM.toFloat())))
        }
    }

    /**
     * Create a LocationRequest object
     */
    private fun createLocationRequest() {
        locationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            LocationRequest.Builder(UPDATE_INTERVAL).build()
        } else {
            LocationRequest.create().apply {
                interval = 10000
                fastestInterval = 5000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }
        }
    }

    /**
     * Prompts the user for permission to use the device location.
     */
    private fun getPermissions() {
        val neededPermissions = arrayListOf<String>()
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) -> {
                locationPermissionGranted = true
            }
            else -> neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS) -> {
                notificationPermissionGranted = true
            }
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }else{
                AlertDialog.Builder(this).setTitle(R.string.app_name)
                    .setMessage(R.string.permission_notification_rationale).setCancelable(false)
                    .setPositiveButton(R.string.ok){_,_->
                        val settingsIntent: Intent =
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                                .putExtra(Settings.EXTRA_CHANNEL_ID, R.string.notification_channel_id)
                        startActivity(settingsIntent)
                    }.show()

            }

        }

        if(neededPermissions.isNotEmpty()){
            ActivityCompat.requestPermissions(this,neededPermissions.toTypedArray(),
                PERMISSION_REQUEST)
        }
    }


    /**
     * Handles the result of the request for location permissions.
     */
    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<String>,
                                            grantResults: IntArray) {
        locationPermissionGranted = false
        notificationPermissionGranted = false
        when (requestCode) {
            PERMISSION_REQUEST -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty()) {
                    if(Manifest.permission.ACCESS_FINE_LOCATION in permissions) {
                        locationPermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                        startLocationUpdates()
                        updateLocationUI()
                    }
                    if(Manifest.permission.POST_NOTIFICATIONS in permissions) {
                        notificationPermissionGranted = grantResults[permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)] == PackageManager.PERMISSION_GRANTED
                    }
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    /**
     * Updates the map's UI settings based on whether the user has granted location permission.
     */
    @SuppressLint("MissingPermission")
    private fun updateLocationUI() {
        if (map == null) {
            return
        }
        try {
            if (locationPermissionGranted) {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
                map?.uiSettings?.isTiltGesturesEnabled = false
                map?.uiSettings?.isScrollGesturesEnabled = false
                map?.uiSettings?.isZoomControlsEnabled = false
                map?.setMinZoomPreference(DEFAULT_ZOOM.toFloat())
                map?.setMaxZoomPreference(DEFAULT_ZOOM.toFloat())
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
                map?.uiSettings?.isTiltGesturesEnabled = false
                map?.uiSettings?.isScrollGesturesEnabled = false
                map?.uiSettings?.isZoomControlsEnabled = false
                lastKnownLocation = null
                //getPermissions()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    private fun updateMap(){
        if(map!=null) {
            mService.messages.forEach { entry ->
                if (entry.key !in placed) {
                    map?.addMarker(MarkerOptions().position(LatLng(entry.value.lat,entry.value.lng)))
                    placed.add(entry.key)
                }
            }
        }
        if(lastKnownLocation!=null){
            range.center = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
        }
    }
    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 20
        private const val UPDATE_INTERVAL: Long= 10
        private const val PERMISSION_REQUEST = 1
        // Keys for storing activity state.
        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"

    }

}


    