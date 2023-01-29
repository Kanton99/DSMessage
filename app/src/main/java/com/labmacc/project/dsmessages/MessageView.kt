package com.labmacc.project.dsmessages

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

class MessageView : AppCompatActivity() {
    private var msg: Message? = null

    private var mBound: Boolean = false
    private lateinit var mService: MessageStore
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

    private lateinit var button: Button
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_message_view)

        val extras = intent.extras

        if(extras != null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                msg = extras.getParcelable(getString(R.string.MessageExtra),Message::class.java)
                }else msg = extras.getParcelable<Message>(getString(R.string.MessageExtra))!!

        if(msg!=null) {
            val msg_text = findViewById<TextView>(R.id.message_text)
            msg_text.text = msg!!.text

            val msg_coord = findViewById<TextView>(R.id.coords_text)
            val coords = "Coordinates: ${msg!!.lat}, ${msg!!.lng}"
            msg_coord.text = coords

            Intent(this, MessageStore::class.java).also{
                    intent -> bindService(intent,msgStrConnection, BIND_AUTO_CREATE)
            }

            button = findViewById(R.id.delete_button)
            button.setOnClickListener {
                if(mBound){
                    mService.delete(msg!!.msgID)
                    finish()
                }else{
                    Toast.makeText(this,"Can't delete, no access to message database",Toast.LENGTH_LONG).show()
                }
            }
        }else{
            finish()
        }
    }
}