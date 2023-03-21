package com.example.wifiwithkotlin

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.wifiwithkotlin.R
import java.io.*
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.UnknownHostException


class MainActivity : AppCompatActivity() {
    private var mConnectionStatus: TextView? = null
    private var mInputEditText: EditText? = null
    private var mConversationArrayAdapter: ArrayAdapter<String>? = null
    private var TAG: String? = null
    private var isConnected = false
    private var mServerIP: String? = null
    private var mSocket: Socket? = null
    private var mOut: PrintWriter? = null
    private var mIn: BufferedReader? = null
    private var mReceiverThread: Thread? = null
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mConnectionStatus = findViewById<View>(R.id.connection_status_textview) as TextView
        mInputEditText = findViewById<View>(R.id.input_string_edittext) as EditText
        TAG = "TcpClient"
        val mMessageListview = findViewById<View>(R.id.message_listview) as ListView
        val sendButton = findViewById<View>(R.id.send_button) as Button
        sendButton.setOnClickListener {
            val sendMessage = mInputEditText!!.text.toString()
            if (sendMessage.length > 0) {
                if (!isConnected) showErrorDialog("서버로 접속된후 다시 해보세요.") else {
                    Thread(SenderThread(sendMessage)).start()
                    mInputEditText!!.setText(" ")
                }
            }
        }
        mConversationArrayAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item
        )
        mMessageListview.adapter = mConversationArrayAdapter
        Thread(ConnectThread("192.168.0.17", 8090)).start()
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
    }

    override fun onBackPressed() {
        if (back_pressed + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
            Log.d(TAG, "onBackPressed:")
            isConnected = false
            finish()
        } else {
            Toast.makeText(baseContext, "한번 더 뒤로가기를 누르면 종료됩니다.", Toast.LENGTH_SHORT).show()
            back_pressed = System.currentTimeMillis()
        }
    }

    private inner class ConnectThread internal constructor(
        private val serverIP: String,
        private val serverPort: Int
    ) :
        Runnable {
        override fun run() {
            try {
                mSocket = Socket(serverIP, serverPort)
                //ReceiverThread: java.net.SocketTimeoutException: Read timed out 때문에 주석처리
                //mSocket.setSoTimeout(3000);
                mServerIP = mSocket!!.remoteSocketAddress.toString()
            } catch (e: UnknownHostException) {
                Log.d(TAG, "ConnectThread: can't find host")
            } catch (e: SocketTimeoutException) {
                Log.d(TAG, "ConnectThread: timeout")
            } catch (e: Exception) {
                Log.e(TAG, "ConnectThread:" + e.message)
            }
            if (mSocket != null) {
                try {
                    mOut = PrintWriter(
                        BufferedWriter(
                            OutputStreamWriter(
                                mSocket!!.getOutputStream(), "UTF-8"
                            )
                        ), true
                    )
                    mIn = BufferedReader(InputStreamReader(mSocket!!.getInputStream(), "UTF-8"))
                    isConnected = true
                } catch (e: IOException) {
                    Log.e(TAG, "ConnectThread:" + e.message)
                }
            }
            runOnUiThread {
                if (isConnected) {
                    Log.d(TAG, "connected to $serverIP")
                    mConnectionStatus!!.text = "connected to $serverIP"
                    mReceiverThread = Thread(ReceiverThread())
                    mReceiverThread!!.start()
                } else {
                    Log.d(
                        TAG,
                        "failed to connect to server $serverIP"
                    )
                    mConnectionStatus!!.text = "failed to connect to server $serverIP"
                }
            }
        }

        init {
            mConnectionStatus!!.text = "connecting to $serverIP......."
        }
    }

    private inner class SenderThread internal constructor(private val msg: String) : Runnable {
        override fun run() {
            mOut!!.println(msg)
            mOut!!.flush()
            runOnUiThread {
                Log.d(TAG, "send message: $msg")
                //mConversationArrayAdapter!!.insert("Me - $msg", 0)
                mConversationArrayAdapter!!.insert("<조이름: 매트릭스> 아두이노로 보내는 거: $msg", 0)
            }
        }
    }

    private inner class ReceiverThread : Runnable {
        override fun run() {
            try {
                while (isConnected) {
                    if (mIn == null) {
                        Log.d(TAG, "ReceiverThread: mIn is null")
                        break
                    }
                    val recvMessage = mIn!!.readLine()
                    if (recvMessage != null) {
                        runOnUiThread {
                            Log.d(
                                TAG,
                                "recv message: $recvMessage"
                            )
                            // mConversationArrayAdapter!!.insert("$mServerIP - $recvMessage", 0)
                            mConversationArrayAdapter!!.insert("<조이름: 매트릭스> 아두이노에서 받은 거:(바로 윗줄)", 0)
                            mConversationArrayAdapter!!.insert("$recvMessage", 0)
                        }
                    }
                }
                Log.d(TAG, "ReceiverThread: thread has exited")
                if (mOut != null) {
                    mOut!!.flush()
                    mOut!!.close()
                }
                mIn = null
                mOut = null
                if (mSocket != null) {
                    try {
                        mSocket!!.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "ReceiverThread: $e")
            }
        }
    }

    fun showErrorDialog(message: String?) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Error")
        builder.setCancelable(false)
        builder.setMessage(message)
        builder.setPositiveButton(
            "OK"
        ) { dialog, which ->
            dialog.dismiss()
            finish()
        }
        // builder.create().notify();
    }

    companion object {
        private const val TAG = "TcpClient"
        private var back_pressed: Long = 0
    }
}
