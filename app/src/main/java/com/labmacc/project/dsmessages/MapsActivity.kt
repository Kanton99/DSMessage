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
import android.os.Handler
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
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
import java.util.*

/**
 * An activity that displays a map showing the place at the device's current location.
 */
@Suppress("DEPRECATION")
class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    //Permissions
    private var backgroundPermission: Boolean = false
    private var notificationPermissionGranted: Boolean = false
    private var locationPermissionGranted = false

    //MessageStore connection
    private lateinit var messageStore: MessageStore
    private var mStoreBound: Boolean = false
    private val msgStrConnection : ServiceConnection = object: ServiceConnection{
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as MessageStore.LocalBinder
            messageStore = binder.getService()
            mStoreBound = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mStoreBound = false
        }
    }

    //Location Service connection
    private lateinit var location: BackgroundLocation
    private var bLocBound: Boolean = false
    private val locationConnection : ServiceConnection = object: ServiceConnection{
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as BackgroundLocation.LocalBinder
            bLocBound = true
            location = binder.getService()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            bLocBound = false
        }
    }

    private var map: GoogleMap? = null
    private var cameraPosition: CameraPosition? = null
    private lateinit var placed:MutableList<Int>

    // The last known geographical location where the device is currently located.
    private var lastKnownLocation: Location? = null

    //Add message Button
    private lateinit var button: AppCompatImageButton

    //Notification range
    private lateinit var range: Circle

    //Update UI
    private lateinit var mHandler: Handler
    private lateinit var markers:MutableMap<Int,Marker>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retrieve location and camera position from saved instance state.
        if (savedInstanceState != null) {
            cameraPosition = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                savedInstanceState.getParcelable(KEY_CAMERA_POSITION,CameraPosition::class.java)
            } else {
                savedInstanceState.getParcelable(KEY_CAMERA_POSITION)
            }

        }

        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_maps)

        //add layout functionalities
        button = findViewById(R.id.placeMessage)
        button.setOnClickListener {
            val writeMessage = Intent(this, WriteMessage::class.java)
            writeMessage.putExtra("Lat", lastKnownLocation?.latitude)
            writeMessage.putExtra("Lng", lastKnownLocation?.longitude)
            this.startActivity(writeMessage)
        }

        val mMsgButton = findViewById<Button>(R.id.my_msg_button)
        mMsgButton.setOnClickListener {
            openMyMessages(it)
        }

        placed = mutableListOf()

        // Build the map.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)

        //SignIn Activity
        val signIn = Intent(this, FirebaseUIActivity::class.java)
        this.startActivity(signIn)

        //Bind the msgStore Service
        Intent(this, MessageStore::class.java).also { intent ->
            bindService(intent, msgStrConnection, BIND_AUTO_CREATE)
        }

        mHandler = Handler()
        markers = mutableMapOf()
        startUIUpdate()
    }

    var mStatusChecker: Runnable = object : Runnable {
        override fun run() {
            try {
                // Perform your task here
                updateMap()
            } finally {
                mHandler.postDelayed(this, DELAY)
            }
        }
    }

    private fun startUIUpdate() {
        mStatusChecker.run()
    }

    private fun stopUIUpdate() {
        mHandler.removeCallbacks(mStatusChecker)
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
        range = map.addCircle(
            CircleOptions().center(LatLng(0.0, 0.0)).radius(10.0).strokeColor(Color.BLUE)
                .fillColor(Color.TRANSPARENT))
        updateLocationUI()

    }

    override fun onResume(){
        super.onResume()
        if(map!=null) startUIUpdate()
    }

    override fun onDestroy() {
        stopUIUpdate()
        unbindService(locationConnection)
        unbindService(msgStrConnection)
        super.onDestroy()
    }

    /**
     * Prompts the user for permission to use the device location.
     */
    @SuppressLint("NewApi")
    private fun getPermissions() {
        val neededPermissions = arrayListOf<String>()


        //Foreground location
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) -> {
                locationPermissionGranted = true
                val intent = Intent(this,BackgroundLocation::class.java)
                startService(intent)
                bindService(intent,locationConnection, BIND_AUTO_CREATE)

                if(!backgroundPermission){
                    requestBackgroundLocation()
                }
            }
            else -> {
                neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        //Notification
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

    @SuppressLint("NewApi")
    private fun requestBackgroundLocation() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            AlertDialog.Builder(this)
                .setTitle(R.string.app_name)
                .setMessage("The app needs access to your location in backgroud to work when you are not using it")
                .setPositiveButton(R.string.ok) { _, _ ->
                    ActivityCompat.requestPermissions(
                        this, arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                        PERMISSION_REQUEST
                    )
                }.setNegativeButton(R.string.cancel) { _, _ -> }
                .show()
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
                        val intent = Intent(this,BackgroundLocation::class.java)
                        startService(intent)
                        bindService(intent,locationConnection, BIND_AUTO_CREATE)
                        updateLocationUI()
                        requestBackgroundLocation()
                    }
                    if(Manifest.permission.POST_NOTIFICATIONS in permissions) {
                        notificationPermissionGranted = grantResults[permissions.indexOf(Manifest.permission.POST_NOTIFICATIONS)] == PackageManager.PERMISSION_GRANTED
                    }
                    if(Manifest.permission.ACCESS_BACKGROUND_LOCATION in permissions){
                        val it = permissions.indexOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        backgroundPermission = grantResults[it] == PackageManager.PERMISSION_GRANTED
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
            map?.uiSettings?.isTiltGesturesEnabled = false
            map?.uiSettings?.isScrollGesturesEnabled = false
            map?.uiSettings?.isZoomControlsEnabled = true
            map?.setMinZoomPreference(MIN_ZOOM.toFloat())
            map?.setMaxZoomPreference(MAX_ZOOM.toFloat())
            map?.uiSettings?.isRotateGesturesEnabled = true
            if (locationPermissionGranted) {
                map?.isMyLocationEnabled = true
                map?.uiSettings?.isMyLocationButtonEnabled = true
            } else {
                map?.isMyLocationEnabled = false
                map?.uiSettings?.isMyLocationButtonEnabled = false
                lastKnownLocation = null
                //getPermissions()
            }
        } catch (e: SecurityException) {
            Log.e("Exception: %s", e.message, e)
        }
    }

    private fun updateMap(){
        if(bLocBound) {
            lastKnownLocation = location.lastKnownLocation
            var pos = LatLng(0.0,0.0)
            if(lastKnownLocation!=null){
                pos = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
            }
            if (map != null) {
                map!!.moveCamera(CameraUpdateFactory.newLatLng(pos))
                range.center = pos
                messageStore.messages.forEach { entry ->
                    if(entry.key !in markers.keys) {
                        val marker = map!!.addMarker(
                            MarkerOptions()
                                .position(LatLng(entry.value.lat, entry.value.lng))
                                .title(entry.value.msgID.toString())
                        )
                        markers[entry.key] = marker!!
                    }
                }
                val deleted = markers.keys.subtract(messageStore.messages.keys)
                if(deleted.isNotEmpty()){
                    deleted.toList().forEach{
                        val marker = markers[it]
                        marker?.remove()
                        markers.remove(it)
                    }
                }
            }
        }
    }

    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 20f
        private const val MIN_ZOOM = 19f
        private const val MAX_ZOOM = 30f
        private const val DELAY: Long= 100
        private const val PERMISSION_REQUEST = 1
        // Keys for storing activity state.
        private const val KEY_CAMERA_POSITION = "camera_position"
        private const val KEY_LOCATION = "location"

    }

    private fun openMyMessages(view: View){
        val intent = Intent(this,MyMessages::class.java)
        startActivity(intent)
    }

}


    