package com.aetherlink.app

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var tvDeviceNameValue: TextView
    private lateinit var btnEditDeviceName: TextView
    private lateinit var deviceNameLayout: LinearLayout
    private lateinit var deviceNameEditLayout: LinearLayout
    private lateinit var etDeviceNameEdit: EditText
    private lateinit var btnSaveDeviceName: TextView
    private lateinit var btnBackSettings: ImageButton

    private val prefs by lazy { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        tvDeviceNameValue = findViewById(R.id.tvDeviceNameValue)
        btnEditDeviceName = findViewById(R.id.btnEditDeviceName)
        deviceNameLayout = findViewById(R.id.deviceNameLayout)
        deviceNameEditLayout = findViewById(R.id.deviceNameEditLayout)
        etDeviceNameEdit = findViewById(R.id.etDeviceNameEdit)
        btnSaveDeviceName = findViewById(R.id.btnSaveDeviceName)
        btnBackSettings = findViewById(R.id.btnBackSettings)

        tvDeviceNameValue.text = prefs.getString(KEY_DEVICE_NAME, DEFAULT_DEVICE_NAME)
            ?: DEFAULT_DEVICE_NAME

        setupListeners()
    }

    private fun setupListeners() {
        btnEditDeviceName.setOnClickListener {
            etDeviceNameEdit.setText(tvDeviceNameValue.text.toString())
            etDeviceNameEdit.setSelection(etDeviceNameEdit.text?.length ?: 0)
            deviceNameLayout.visibility = View.GONE
            deviceNameEditLayout.visibility = View.VISIBLE
            etDeviceNameEdit.requestFocus()
        }

        btnSaveDeviceName.setOnClickListener {
            val newDeviceName = etDeviceNameEdit.text.toString().trim()
            if (newDeviceName.isNotEmpty()) {
                tvDeviceNameValue.text = newDeviceName
                prefs.edit().putString(KEY_DEVICE_NAME, newDeviceName).apply()
            }
            deviceNameEditLayout.visibility = View.GONE
            deviceNameLayout.visibility = View.VISIBLE
        }

        btnBackSettings.setOnClickListener { finish() }
    }

    companion object {
        private const val PREFS_NAME = "aetherlink_settings"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val DEFAULT_DEVICE_NAME = "My Device"
    }
}
