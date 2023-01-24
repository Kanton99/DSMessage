package com.labmacc.project.dsmessages

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.EditText

class WriteMessage : AppCompatActivity() {

    private lateinit var mService: MessageStore
    private var mBound: Boolean = false
    private val msgStrConnection : ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as MessageStore.LocalBinder
            mService = binder.getService()
            mBound = true
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mBound = false
        }
    }

    private var Lat:Double = 0.0
    private var Lng:Double = 0.0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.message_write)

        val extras = intent.extras
        if(extras!= null){
            Lat = extras.getDouble("Lat")
            Lng = extras.getDouble("Lng")
        }

        Intent(this, MessageStore::class.java).also { intent ->
            bindService(intent, msgStrConnection, 0)
        }
    }

    fun Place(view: View){
        if(mBound) {
            val text = findViewById<EditText>(R.id.MessageTextInput).text.toString()

            mService.writeDatabase(text, Lat, Lng)
        }
        finish()
    }

    fun Delete(view: View){
        finish()
    }
}