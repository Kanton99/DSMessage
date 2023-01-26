package com.labmacc.project.dsmessages

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.Channel
import java.util.*
import kotlin.concurrent.timer
import com.labmacc.project.dsmessages.Message as Message

class MessageStore : Service() {
    private var startMode: Int = 0             // indicates how to behave if the service is killed
    private var allowRebind: Boolean = false   // indicates whether onRebind should be used
    private var sBinder = LocalBinder()

    lateinit var messages: MutableMap<Int,Message>
    //Firebase realtime database instance
    private lateinit var database: FirebaseDatabase
    private var user:String? = null

    private var TAG :String = "Message Store"
    private var DISTANCE:Float=10f//meters

    private var lateId = 0

    lateinit var lastLocation: LatLng

    private lateinit var notified: MutableList<Int>
    //Notifications
    private lateinit var notificationManager: NotificationManager
    override fun onCreate() {
        // The service is being created
        Log.i(TAG,"Message Store Created")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val auth = Firebase.auth
        user = auth.uid
        lastLocation = LatLng(0.0,0.0)
        database = Firebase.database("https://dsmessages-default-rtdb.europe-west1.firebasedatabase.app/")
        messages = mutableMapOf()

        val dataRef = database.getReference("Messages").orderByKey()
        dataRef.addValueEventListener(object : ValueEventListener{
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.children.forEach(
                    fun(child){
                        val value = child.getValue(Message::class.java)!!
                        val key = value.msgID
                        messages[key] = value
                        lateId = messages.size
                    }
                )
            }

            override fun onCancelled(error: DatabaseError) {
                return
            }
        })

        notified = arrayListOf()
        Timer().schedule(object: TimerTask(){
            override fun run() {
                search()
            }
        },1000,10000)
    }

    fun writeDatabase(text: String,Lat: Double, Lng: Double){
        val msg = Message(text,Lat,Lng,0,0,user,lateId)
        messages[msg.msgID] = msg
        lateId++
        var path: String = "Messages/Msg"+msg.msgID
        val myRef = database.getReference(path)

        myRef.setValue(msg)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The service is starting, due to a call to startService()
        return startMode
    }

    override fun onBind(intent: Intent): IBinder? {
        // A client is binding to the service with bindService()
        Log.i(TAG,"Service binded to ${intent.toString()}")
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
        fun getService(): MessageStore = this@MessageStore
    }

    fun search(){
        val pos = LatLng(lastLocation.latitude,
            lastLocation.longitude)
        messages.forEach(
            fun(msg){
                val msgLoc = LatLng(msg.value.lat,msg.value.lng)
                val res = floatArrayOf(0f)
                Location.distanceBetween(pos.latitude,pos.longitude,msgLoc.latitude,msgLoc.longitude,res)
                if(res[0]<=DISTANCE && msg.value.uID!=user && msg.value.msgID !in notified){
                    notify(msg.value)
                    notified.add(msg.value.msgID)
                }
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun notify(msg: Message){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if(notificationManager.areNotificationsEnabled()){
                    val builder = NotificationCompat.Builder(this, getString(R.string.notification_channel_id))
                        .setContentTitle("Your found a message")
                        .setContentText(msg.text)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSmallIcon(androidx.core.R.drawable.notification_template_icon_bg)

                    with(NotificationManagerCompat.from(this)){
                        notify(0,builder.build())
                    }
            }
        } else {
                TODO("VERSION.SDK_INT < N")
        }
    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(getString(R.string.notification_channel_id), name, importance).apply {
                description = descriptionText
            }
            // Register the channel with the system
            val notificationManager: NotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

}