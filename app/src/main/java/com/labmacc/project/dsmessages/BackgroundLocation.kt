package com.labmacc.project.dsmessages

import android.annotation.SuppressLint
import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import java.util.*


class BackgroundLocation : Service() {
    var lastKnownLocation: Location? = null
    private lateinit var locationRequest: LocationRequest
    private val locationPermissionGranted: Boolean = true
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var locationCallback: LocationCallback

    private lateinit var mService: MessageStore
    private var mBound: Boolean = false
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

    private var startMode: Int = 0             // indicates how to behave if the service is killed
    private var allowRebind: Boolean = false   // indicates whether onRebind should be used
    private var sBinder = LocalBinder()

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createLocationRequest()

        locationCallback = object : LocationCallback(){
            override fun onLocationResult(p0: LocationResult) {
                super.onLocationResult(p0)
                Log.i("BACKGROUND LOCATION","found your location")
                for (location in p0.locations) {
                    lastKnownLocation = location
                    if (lastKnownLocation != null) {
                        val pos = LatLng(
                            lastKnownLocation!!.latitude,
                            lastKnownLocation!!.longitude
                        )
                        if (mBound) {
                            mService.lastLocation = pos
                            mService.search()
                        }
                    }
                }
            }
        }

        startLocationUpdates()
        Intent(this, MessageStore::class.java).also { intent ->
            bindService(intent, msgStrConnection, BIND_AUTO_CREATE)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is starting, due to a call to startService()
        return startMode
    }

    override fun onBind(intent: Intent): IBinder? {
        // A client is binding to the service with bindService()
        return sBinder
    }

    override fun onUnbind(intent: Intent): Boolean {
        // All clients have unbound with unbindService()
        return allowRebind
    }

    override fun onRebind(intent: Intent) {
        // A client is binding to the service with bindService(),
        // after onUnbind() has already been called
    }

    override fun onDestroy() {
        // The service is no longer used and is being destroyed
        Timer().purge()
        Timer().cancel()
    }

    inner class LocalBinder : Binder(){
        fun getService(): BackgroundLocation = this@BackgroundLocation
    }


    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if(locationPermissionGranted){
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.myLooper()
            )
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

    companion object{
        private val UPDATE_INTERVAL: Long = 50
    }
}