package com.labmacc.project.dsmessages

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity


class MyMessages : AppCompatActivity() {
    //Execution booleans
    private var mBound: Boolean = false
    //data variables
    private lateinit var mService: MessageStore
    private lateinit var messages: MutableList<String>
    private lateinit var ids: MutableList<Int>

    private val msgStrConnection : ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(p0: ComponentName?, p1: IBinder?) {
            val binder = p1 as MessageStore.LocalBinder
            mService = binder.getService()
            mBound = true

            updateListLayout()
        }

        override fun onServiceDisconnected(p0: ComponentName?) {
            mBound = false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_messages)

        Intent(this, MessageStore::class.java).also{
                intent -> bindService(intent,msgStrConnection, BIND_AUTO_CREATE)
        }

        messages = mutableListOf()
        ids = mutableListOf()
    }


    private fun updateListLayout(){
        val listView = findViewById<ListView>(R.id.message_list)

        getMyMessageList()

        val adapter: ArrayAdapter<String> = ArrayAdapter<String>(
            this,android.R.layout.simple_list_item_1,messages.toTypedArray()
        )
        listView.adapter = adapter
        listView.setOnItemClickListener { _, view, i, _ ->
            val intent = Intent(this,MessageView::class.java)
            val msg = mService.messages[ids[i]]
            intent.putExtra(getString(R.string.MessageExtra),msg)
            startActivity(intent)
        }
    }

    private fun getMyMessageList(){
        for (msg in mService.messages) {
            if(msg.value.uID == mService.user) {
                val item = msg.value.text
                messages.add(item)
                ids.add(msg.value.msgID)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if(mBound){
            messages = mutableListOf()
            ids = mutableListOf()
            updateListLayout()
        }
    }
}