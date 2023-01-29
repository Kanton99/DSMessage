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
    private fun getPermissions() {
        val neededPermissions = arrayListOf<String>()
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) -> {
                locationPermissionGranted = true
                val intent = Intent(this,BackgroundLocation::class.java)
                startService(intent)
                bindService(intent,locationConnection, BIND_AUTO_CREATE)
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

        when (PackageManager.PERMISSION_GRANTED){
            ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_BACKGROUND_LOCATION)->{
                backgroundPermission = true
            }else -> neededPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
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
                        val intent = Intent(this,BackgroundLocation::class.java)
                        startService(intent)
                        bindService(intent,locationConnection, BIND_AUTO_CREATE)
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
        if(bLocBound) {
            lastKnownLocation = location.lastKnownLocation
            var pos = LatLng(0.0,0.0)
            if(lastKnownLocation!=null){
                pos = LatLng(lastKnownLocation!!.latitude, lastKnownLocation!!.longitude)
            }
            if (map != null) {
                map!!.clear()
                map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    pos,
                    DEFAULT_ZOOM
                ))
                range = map!!.addCircle(
                    CircleOptions().center(LatLng(0.0, 0.0)).radius(10.0).strokeColor(Color.BLUE)
                        .fillColor(Color.TRANSPARENT)
                        .center(pos)
                )
                messageStore.messages.forEach { entry ->
                    val marker = map!!.addMarker(
                        MarkerOptions()
                            .position(LatLng(entry.value.lat, entry.value.lng))
                    )
                }
            }
        }
    }

    companion object {
        private val TAG = MapsActivity::class.java.simpleName
        private const val DEFAULT_ZOOM = 20f
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


    