package com.labmacc.project.dsmessages

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import android.widget.Toast
import com.labmacc.project.dsmessages.Message

class MessageStore : Service() {
    private var startMode: Int = 0             // indicates how to behave if the service is killed
    private var binder: IBinder? = null        // interface for clients that bind
    private var allowRebind: Boolean = false   // indicates whether onRebind should be used
    private var sBinder = LocalBinder()

    override fun onCreate() {
        // The service is being created
        Log.i("Serivce Test","Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is starting, due to a call to startService()
        return startMode
    }

    override fun onBind(intent: Intent): IBinder? {
        // A client is binding to the service with bindService()
        Log.i("Serivce Test","Service binded")

        return binder
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
    }

    inner class LocalBinder : Binder(){
        fun getService(): MessageStore = this@MessageStore
    }
}