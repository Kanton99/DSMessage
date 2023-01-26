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
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng

/**
 * An activity that displays a map showing the place at the device's current location.
 */
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {
    private var notificationPermissionGranted: Boolean = false
    private lateinit var mService: MessageStore
    private var mBound: Boolean = false
    private val UPDATE_INTERVAL: Long= 500
    private val requestingLocationUpdates: Boolean = false
    private var map: GoogleMap? = null
    private var cameraPosition: CameraPosition? = null

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

        // Prompt the user for permission.
        getPermissions()

        // Construct a FusedLocationProviderClient.
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        // Build the map.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        //location Callback
        locationCallback = object : LocationCallback(){
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult ?: return
                    for (location in locationResult.locations){
                        lastKnownLocation = location
                        if (lastKnownLocation != null) {
                            val pos = LatLng(lastKnownLocation!!.latitude,
                                lastKnownLocation!!.longitude)
                            map?.moveCamera(CameraUpdateFactory.newLatLngZoom(
                                pos, DEFAULT_ZOOM.toFloat()))
                            if(mBound){
                                mService.search(pos)
                            }
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
        map.uiSettings.isScrollGesturesEnabled = false
        map.uiSettings.isZoomControlsEnabled = false
        map.setMinZoomPreference(20f)
        map.setMaxZoomPreference(20f)
        map.uiSettings.isTiltGesturesEnabled = true

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
            TODO("VERSION.SDK_INT < S")

        }
    }

    /**
     * Prompts the user for permission to use the device location.
     */
    private fun getPermissions() {
        val neededPermissions = arrayListOf<String>()
        if (ContextCompat.checkSelfPermission(this.applicationContext,Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        } else {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if(ContextCompat.checkSelfPermission(this.applicationContext,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED){
            notificationPermissionGranted = true
        }else{
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if(neededPermissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                    this,
                    neededPermissions.toTypedArray(),
                    PERMISSION_REQUEST_LOCATION
                )
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
            PERMISSION_REQUEST_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty()) {
                    if(Manifest.permission.ACCESS_FINE_LOCATION in permissions) {
                        locationPermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED
                    }
                    if(Manifest.permission.POST_NOTIFICATIONS in permissions) {
                        notificationPermissionGranted = grantResults[permissions.size-1] == PackageManager.PERMISSION_GRANTED
                    }
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
        //updateLocationUI()
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
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
                lastKnownLocation = null
                getPermissions()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 15
        private const val PERMISSION_REQUEST_LOCATION = 1
        private const val PERMISSION_REQUEST_NOTIFICATION = 2
        // Keys for storing activity state.
        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"

    }

}


    