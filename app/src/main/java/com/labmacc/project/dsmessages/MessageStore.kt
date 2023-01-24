package com.labmacc.project.dsmessages

import android.app.Service
import android.content.Intent
import android.os.*
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import com.labmacc.project.dsmessages.Message as Message

class MessageStore : Service() {
    private var startMode: Int = 0             // indicates how to behave if the service is killed
    private var binder: IBinder? = null        // interface for clients that bind
    private var allowRebind: Boolean = false   // indicates whether onRebind should be used
    private var sBinder = LocalBinder()

    private lateinit var messages: MutableMap<Int,Message>
    //Firebase realtime database instance
    private lateinit var database: FirebaseDatabase
    private var user:String? = null

    private var TAG :String = "Message Store"
    override fun onCreate() {
        // The service is being created
        Log.i(TAG,"Message Store Created")

        val auth = Firebase.auth
        user = auth.uid

        database = Firebase.database("https://dsmessages-default-rtdb.europe-west1.firebasedatabase.app/")
        messages = mutableMapOf()

        //test
        val msg = Message("Hello World4!", 0.0,0.0,0,0,user,0)

        writeDatabase(msg)
    }

    fun writeDatabase(msg: Message){
        messages.put(msg.msgID,msg)
        var path: String = "Messages/Msg"+msg.msgID
        val myRef = database.getReference(path)

        myRef.setValue(msg)

        myRef.addValueEventListener(object : ValueEventListener
        {
            override fun onDataChange(snapshot: DataSnapshot) {
                val value = snapshot.getValue(Message::class.java)
                if(value != null){
                    messages[value.msgID] = value
                    Log.i(TAG, "Message changed on database $value")
                }

            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Failed to read value.", error.toException())
            }
        }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is starting, due to a call to startService()
        return startMode
    }

    override fun onBind(intent: Intent): IBinder? {
        // A client is binding to the service with bindService()
        Log.i(TAG,"Service binded")

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