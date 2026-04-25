package com.aetherlink.app

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.aetherlink.app.databinding.ActivityMainBinding
import com.aetherlink.app.databinding.ItemMessageBinding
import com.google.android.material.snackbar.Snackbar
import com.xwray.groupie.GroupAdapter
import com.xwray.groupie.GroupieViewHolder
import com.xwray.groupie.viewbinding.BindableItem
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


private const val REQUEST_PERMISSIONS = 200

class MainActivity : AppCompatActivity() {

    private val TAG: String = "AetherLink"
    private lateinit var binding: ActivityMainBinding
    private var bluetoothAdapter: BluetoothAdapter? = null
    private val mUUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    private var mPairedDevices = listOf<BluetoothDevice>()
    private lateinit var mMessagesAdapter: GroupAdapter<GroupieViewHolder>
    private lateinit var mHandler: Handler
    private var suppressBluetoothToggleCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "MainActivity launched")
        Toast.makeText(this, "AetherLink started", Toast.LENGTH_SHORT).show()

        setupBluetoothToggle()
        ensurePermissionsThen {
            setup()
            syncBluetoothUiState()
            if (isBluetoothEnabled()) startBluetoothFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        syncBluetoothUiState()
    }

    private fun setupBluetoothToggle() {
        binding.switchBluetoothPower.setOnCheckedChangeListener { _, isChecked ->
            if (suppressBluetoothToggleCallback) return@setOnCheckedChangeListener
            if (isChecked) enableBluetooth() else disableBluetooth()
        }
    }

    private fun setBluetoothToggleChecked(checked: Boolean) {
        suppressBluetoothToggleCallback = true
        binding.switchBluetoothPower.isChecked = checked
        suppressBluetoothToggleCallback = false
    }

    private fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    private fun syncBluetoothUiState() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        val enabled = isBluetoothEnabled()
        setBluetoothToggleChecked(enabled)

        if (bluetoothAdapter == null) {
            binding.tvDeviceName.text = "Unknown"
            binding.tvDeviceAddress.text = "Hidden"
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            binding.tvDeviceName.text = bluetoothAdapter?.name ?: "Unknown"
            binding.tvDeviceAddress.text = "Hidden"
        }
    }

    @SuppressLint("MissingPermission")
    private fun disableBluetooth() {
        bluetoothAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                adapter.disable()
                Snackbar.make(binding.root, "Bluetooth turned off", Snackbar.LENGTH_SHORT).show()
            }
        }
        clearBluetoothUiState()
        setBluetoothToggleChecked(false)
    }

    private fun clearBluetoothUiState() {
        binding.tvDeviceName.text = "Unknown"
        binding.tvDeviceAddress.text = "Hidden"
        binding.tvConnectionLabel.text = getString(R.string.select_device)
        binding.spinnerConnections.adapter = null
        mPairedDevices = listOf()
    }

    private fun ensurePermissionsThen(action: () -> Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.BLUETOOTH_SCAN)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (needed.isEmpty()) {
            action()
        } else {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val denied = grantResults.any { it != PackageManager.PERMISSION_GRANTED }
            if (denied) {
                Snackbar.make(binding.root, "Permissions required to use Bluetooth", Snackbar.LENGTH_LONG).show()
                setBluetoothToggleChecked(false)
            } else {
                setup()
                syncBluetoothUiState()
                if (isBluetoothEnabled()) startBluetoothFlow() else enableBluetooth()
            }
        }
    }

    private fun setup() {
        binding.apply {
            rvResponse.layoutManager =
                LinearLayoutManager(applicationContext, LinearLayoutManager.VERTICAL, false)
            mMessagesAdapter = GroupAdapter()
            mMessagesAdapter.add(ChatMessageItem("Begin Conversation By Connecting To Another Device.....", "Help", resources.getColor(R.color.white, null)))
            rvResponse.adapter = mMessagesAdapter

            btnScan.setOnClickListener {
                setupBluetoothClientConnection()
                Snackbar.make(root, "Scanning paired devices...", Snackbar.LENGTH_SHORT).show()
            }

            btnSettings.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBluetooth() {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Snackbar.make(binding.root, "Your Device Does Not Support Bluetooth.", Snackbar.LENGTH_LONG).show()
            setBluetoothToggleChecked(false)
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            binding.tvDeviceName.text = bluetoothAdapter!!.name ?: "Unknown"
            binding.tvDeviceAddress.text = "Hidden"
        }

        if (bluetoothAdapter?.isEnabled == false) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), REQUEST_ENABLE_BT)
            return
        }

        startBluetoothFlow()
    }

    @SuppressLint("MissingPermission")
    private fun startBluetoothFlow() {
        syncBluetoothUiState()
        setupBluetoothClientConnection()
        AcceptThread().start()
    }

    @SuppressLint("MissingPermission")
    private fun setupBluetoothClientConnection() {
        val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter?.bondedDevices
        val allPairs: MutableList<String> = pairedDevices?.map { device -> device.name ?: "Unknown Device" }?.toMutableList() ?: mutableListOf()

        allPairs.add(0, "Select Connection")
        mPairedDevices = pairedDevices?.toList() ?: listOf()

        val arrayAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, allPairs)
        binding.spinnerConnections.adapter = arrayAdapter
        binding.spinnerConnections.setSelection(0)
        binding.spinnerConnections.setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != 0) {
                    val selectedConnection: BluetoothDevice = pairedDevices!!.toList()[position - 1]
                    ConnectThread(selectedConnection).start()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        })
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT && resultCode == RESULT_OK) {
            Snackbar.make(binding.root, "Devices Bluetooth Enabled", Snackbar.LENGTH_LONG).show()
            syncBluetoothUiState()
            startBluetoothFlow()
        } else if (requestCode == REQUEST_ENABLE_BT) {
            setBluetoothToggleChecked(false)
            binding.tvConnectionLabel.text = getString(R.string.select_device)
        }
    }

    @Suppress("MissingPermission")
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(
                bluetoothAdapter?.name ?: "AetherLink",
                mUUID
            )
        }

        fun createServerSocket(): BluetoothServerSocket? = mmServerSocket

        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    Log.d(TAG, "Establishing new Connection")
                    createServerSocket()?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    shouldLoop = false
                    null
                } catch (se: SecurityException) {
                    Log.e(TAG, "Bluetooth permission denied", se)
                    shouldLoop = false
                    null
                }
                socket?.also { bluetoothSocket ->
                    val client = bluetoothSocket.remoteDevice.name ?: "Unknown Device"
                    manageServerSocketConnection(bluetoothSocket, client)
                    mHandler.post {
                        val idx = mPairedDevices.indexOfFirst { it.name == client }
                        if (idx != -1) {
                            binding.spinnerConnections.setSelection(idx + 1)
                            binding.tvConnectionLabel.text = getString(R.string.connected_to)
                        }
                        Snackbar.make(binding.root, "Connection Established With $client", Snackbar.LENGTH_LONG).show()
                    }
                    createServerSocket()?.close()
                    shouldLoop = false
                }
            }
        }

        fun cancel() {
            try {
                createServerSocket()?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    @Suppress("MissingPermission")
    private inner class ConnectThread(val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(mUUID)
        }

        fun createClientSocket(): BluetoothSocket? = mmSocket

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()

            createClientSocket()?.let { socket ->
                try {
                    socket.connect()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to connect", e)
                }

                val client = socket.remoteDevice.name ?: "Unknown Device"
                manageServerSocketConnection(socket, client)
                Snackbar.make(binding.root, "Connection Established With $client", Snackbar.LENGTH_LONG).show()
            }
        }

        fun cancel() {
            try {
                createClientSocket()?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    private inner class ConnectedThread(private val mmSocket: BluetoothSocket, val opName: String) : Thread() {
        @SuppressLint("MissingPermission")
        private val mmInStream: InputStream = mmSocket.inputStream
        @SuppressLint("MissingPermission")
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        override fun run() {
            var numBytes: Int
            while (true) {
                numBytes = try {
                    mmInStream.read(mmBuffer)
                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    break
                }

                val readMsg = mHandler.obtainMessage(MESSAGE_READ, numBytes, -1, opName to mmBuffer)
                readMsg.sendToTarget()
            }
        }

        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                Log.e(TAG, "Error occurred when sending data", e)
                val writeErrorMsg = mHandler.obtainMessage(MESSAGE_TOAST)
                val bundle = Bundle().apply { putString("toast", "Couldn't send data to the other device") }
                writeErrorMsg.data = bundle
                mHandler.sendMessage(writeErrorMsg)
                return
            }

            val writtenMsg = mHandler.obtainMessage(MESSAGE_WRITE, -1, -1, mmBuffer)
            writtenMsg.sendToTarget()
        }

        fun cancel() {
            try {
                mmSocket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun manageServerSocketConnection(socket: BluetoothSocket, name: String) {
        mHandler = Handler(this.mainLooper, Handler.Callback {
            try {
                val response = it.obj as? Pair<String, ByteArray> ?: return@Callback false
                val from = response.first
                val msg = response.second.decodeToString()
                Toast.makeText(this, "New Message Received", Toast.LENGTH_SHORT).show()
                mMessagesAdapter.add(ChatMessageItem(msg, from, resources.getColor(R.color.reply, null)))
                return@Callback true
            } catch (e: Exception) {
                return@Callback false
            }
        })
        val communicationService = ConnectedThread(socket, name)
        communicationService.start()
        mHandler.post {
            binding.apply {
                etReply.isEnabled = true
                btnSendToConnected.setOnClickListener {
                    val text = etReply.text.toString()
                    communicationService.write(text.encodeToByteArray())
                    mMessagesAdapter.add(ChatMessageItem(text, bluetoothAdapter?.name ?: "AetherLink", resources.getColor(R.color.response, null)))
                    etReply.setText("")
                }
            }
        }
    }

    companion object {
        const val REQUEST_ENABLE_BT = 100
        const val MESSAGE_READ: Int = 0
        const val MESSAGE_WRITE: Int = 1
        const val MESSAGE_TOAST: Int = 2
    }
}

class ChatMessageItem(
    private val message: String,
    private val name: String,
    private val color: Int
) : BindableItem<ItemMessageBinding>() {
    override fun bind(viewBinding: ItemMessageBinding, position: Int) {
        viewBinding.apply {
            tvFrom.text = name
            tvMessage.text = message
            cardRoot.setCardBackgroundColor(color)
        }
    }

    override fun getLayout(): Int = R.layout.item_message

    override fun initializeViewBinding(view: View): ItemMessageBinding {
        return ItemMessageBinding.bind(view)
    }
}
