/*
 * Copyright 2024 Punch Through Design LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.punchthrough.blestarterappandroid

import android.annotation.SuppressLint
//import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.punchthrough.blestarterappandroid.ble.ConnectionEventListener
import com.punchthrough.blestarterappandroid.ble.ConnectionManager
import com.punchthrough.blestarterappandroid.ble.ConnectionManager.parcelableExtraCompat
import com.punchthrough.blestarterappandroid.ble.isIndicatable
import com.punchthrough.blestarterappandroid.ble.isNotifiable
import com.punchthrough.blestarterappandroid.ble.isReadable
import com.punchthrough.blestarterappandroid.ble.isWritable
import com.punchthrough.blestarterappandroid.ble.isWritableWithoutResponse
import com.punchthrough.blestarterappandroid.ble.toHexString
import com.punchthrough.blestarterappandroid.databinding.ActivityBleOperationsBinding
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import android.content.Intent
import android.net.Uri
//import android.widget.Button
import androidx.core.content.FileProvider
import java.io.File


class BleOperationsActivity : AppCompatActivity() {

    private lateinit var fileManager: FileManager
    private lateinit var binding: ActivityBleOperationsBinding

    private val device: BluetoothDevice by lazy {
        intent.parcelableExtraCompat(BluetoothDevice.EXTRA_DEVICE)
            ?: error("Missing BluetoothDevice from MainActivity!")
    }

    // Define a request code constant for permission requests
    companion object {
        private const val REQUEST_CODE_WRITE_EXTERNAL_STORAGE = 1001
    }


    private val dateFormatter = SimpleDateFormat("MMM d, HH:mm:ss", Locale.US)


    private val characteristics by lazy {
        ConnectionManager.servicesOnDevice(device)?.flatMap { service ->
            service.characteristics ?: listOf()
        } ?: listOf()
    }

    private val characteristicProperties by lazy {
        characteristics.associateWith { characteristic ->
            mutableListOf<CharacteristicProperty>().apply {
                if (characteristic.isNotifiable()) add(CharacteristicProperty.Notifiable)
                if (characteristic.isIndicatable()) add(CharacteristicProperty.Indicatable)
                if (characteristic.isReadable()) add(CharacteristicProperty.Readable)
                if (characteristic.isWritable()) add(CharacteristicProperty.Writable)
                if (characteristic.isWritableWithoutResponse()) {
                    add(CharacteristicProperty.WritableWithoutResponse)
                }
            }.toList()
        }
    }
   // private val characteristicAdapter: CharacteristicAdapter by lazy {
   //     CharacteristicAdapter(characteristics) { characteristic ->
   //         showCharacteristicOptions(characteristic)
   //     }
   // }


    private val notifyingCharacteristics = mutableListOf<UUID>()

    @SuppressLint("MissingPermission", "SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ConnectionManager.registerListener(connectionEventListener)

        binding = ActivityBleOperationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize FileManager for handling CSV file writing
        fileManager = FileManager(this)

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowTitleEnabled(true)
            title = getString(R.string.Crutch_characteristics)
        }

        // Retrieve the BluetoothDevice from the intent
        @Suppress("DEPRECATION")
        val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        fileManager.writeToCSVFile("log_data.csv", listOf("0000"))

        // Check Bluetooth permissions
        if (this.hasRequiredBluetoothPermissions()) {
            // Permission granted, access the device name
            binding.deviceName.text = device?.name ?: "Unknown Device"
        } else {
            error("Missing required Bluetooth permissions")
        }

        // Check and request storage permissions before subscribing or saving to external storage
        checkStoragePermission()

        // Set up Share CSV button click handler
        binding.shareCsvButton.setOnClickListener {
            fileManager.shareCsvFile()
        }

        // Set up Delete CSV button click handler
        binding.deleteCsvButton.setOnClickListener {
            fileManager.deleteCsvFile()
            fileManager.writeToCSVFile("log_data.csv", listOf("0000"))
        }

        setupRecyclerView()

        // Automatically subscribe to characteristics
        subscribeToCharacteristics()
    }

    // Function to check for storage permission
    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) and above: No need for WRITE_EXTERNAL_STORAGE permission for app-specific directories
            log("No need for WRITE_EXTERNAL_STORAGE permission on Android 11+.")
        } else {
            // For Android 10 and below, request the WRITE_EXTERNAL_STORAGE permission
            if (!hasPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_EXTERNAL_STORAGE)
            } else {
                log("Storage permission already granted.")
            }
        }
    }


    // Handle the result of permission requests
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, log the success
                log("Storage permission granted.")
            } else {
                // Permission denied, handle accordingly
                log("Storage permission denied.")
            }
        }
    }




    // Method to subscribe to characteristics automatically
    private fun subscribeToCharacteristics() {
        characteristics.forEach { characteristic ->
            when {
                characteristic.isNotifiable() -> {
                    log("Auto-subscribing to notifications on ${characteristic.uuid}")
                    ConnectionManager.enableNotifications(device, characteristic)
                }
                characteristic.isIndicatable() -> {
                    log("Auto-subscribing to indications on ${characteristic.uuid}")
                    ConnectionManager.enableNotifications(device, characteristic)
                }
            }
        }
    }


    override fun onDestroy() {
        ConnectionManager.unregisterListener(connectionEventListener)
        ConnectionManager.teardownConnection(device)
        super.onDestroy()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun setupRecyclerView() {
        // Filter the characteristics to only include those that are notifiable or indicatable
        val subscribableCharacteristics = characteristics.filter { characteristic ->
            characteristic.isNotifiable()
        }

        // Update the adapter with the filtered list
        val characteristicAdapter = CharacteristicAdapter(subscribableCharacteristics) { characteristic ->
            showCharacteristicOptions(characteristic)
        }

        binding.characteristicsRecyclerView.apply {
            adapter = characteristicAdapter
            layoutManager = LinearLayoutManager(
                this@BleOperationsActivity,
                RecyclerView.VERTICAL,
                false
            )
            isNestedScrollingEnabled = false

            itemAnimator.let {
                if (it is SimpleItemAnimator) {
                    it.supportsChangeAnimations = false
                }
            }
        }
    }


    // Preexisting
    @SuppressLint("SetTextI18n")
    private fun log(message: String) {
        val formattedMessage = "${dateFormatter.format(Date())}: $message"
        runOnUiThread {
            val uiText = binding.logTextView.text
            val currentLogText = uiText.ifEmpty { "Beginning of log." }
            binding.logTextView.text = "$currentLogText\n$formattedMessage"
            binding.logScrollView.post { binding.logScrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    // Custom function to write in the log and eventually in the external storage
    @SuppressLint("SetTextI18n")
    private fun logCustomFormat(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        // Limit the UUID to the first 8 digits
        val uuidShort = characteristic.uuid.toString().substring(4, 8)

        // Format the current time to exclude the day
        val timeFormatter = SimpleDateFormat("mm:ss.SSS", Locale.US)
        val currentTime = System.currentTimeMillis()
        val formattedTime = timeFormatter.format(Date(currentTime))



        // Interpret the data as three 8-digit floating-point numbers
        val floatValues = ByteBuffer.wrap(value)
            .order(ByteOrder.LITTLE_ENDIAN) // little-endian byte order
            .asFloatBuffer()
            .let { buffer ->
                floatArrayOf(
                    buffer.getOrNull(0) ?: 0f,
                    buffer.getOrNull(1) ?: 0f,
                    buffer.getOrNull(2) ?: 0f
                )
            }


        // Write the same data to a CSV file
        val csvData = listOf(
            uuidShort,                    // UUID (first 8 characters)
            formattedTime,                  // Timestamp
            floatValues[0].toString(),    // Value 1
            floatValues[1].toString(),    // Value 2
            floatValues[2].toString()     // Value 3
        )

        // Call the CSV writer to log the data
        fileManager.writeToCSVFile("log_data.csv", csvData)
    }


    // Extension function to safely get a float from the buffer
    private fun FloatBuffer.getOrNull(index: Int): Float? {
        return if (index in 0 until this.limit()) this.get(index) else null
    }

    private fun showCharacteristicOptions(
        characteristic: BluetoothGattCharacteristic
    ) = runOnUiThread {
        characteristicProperties[characteristic]?.let { properties ->
            AlertDialog.Builder(this)
                .setTitle("Select an action to perform")
                .setItems(properties.map { it.action }.toTypedArray()) { _, i ->
                    when (properties[i]) {
                        CharacteristicProperty.Readable -> {
                            log("Reading from ${characteristic.uuid}")
                            ConnectionManager.readCharacteristic(device, characteristic)
                            // Optionally log the custom format after reading
                            logCustomFormat(characteristic, byteArrayOf())
                        }
                        CharacteristicProperty.Writable, CharacteristicProperty.WritableWithoutResponse -> {
                            showWritePayloadDialog(characteristic)
                        }
                        CharacteristicProperty.Notifiable, CharacteristicProperty.Indicatable -> {
                            if (notifyingCharacteristics.contains(characteristic.uuid)) {
                                log("Disabling notifications on ${characteristic.uuid}")
                                ConnectionManager.disableNotifications(device, characteristic)
                            } else {
                                log("Enabling notifications on ${characteristic.uuid}")
                                ConnectionManager.enableNotifications(device, characteristic)
                                // Optionally log the custom format when enabling notifications
                                logCustomFormat(characteristic, byteArrayOf()) // Replace byteArrayOf() with actual value if available
                            }
                        }
                    }
                }
                .show()
        }
    }


    @SuppressLint("InflateParams")
    private fun showWritePayloadDialog(characteristic: BluetoothGattCharacteristic) {
        val hexField = layoutInflater.inflate(R.layout.edittext_hex_payload, null) as EditText
        AlertDialog.Builder(this)
            .setView(hexField)
            .setPositiveButton("Write") { _, _ ->
                with(hexField.text.toString()) {
                    if (isNotBlank() && isNotEmpty()) {
                        val bytes = hexToBytes()
                        log("Writing to ${characteristic.uuid}: ${bytes.toHexString()}")
                        ConnectionManager.writeCharacteristic(device, characteristic, bytes)
                    } else {
                        log("Please enter a hex payload to write to ${characteristic.uuid}")
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
            .apply {
                window?.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
                )
                hexField.showKeyboard()
                show()
            }
    }

    private val connectionEventListener by lazy {
        ConnectionEventListener().apply {
            onDisconnect = {
                runOnUiThread {
                    AlertDialog.Builder(this@BleOperationsActivity)
                        .setTitle("Disconnected")
                        .setMessage("Disconnected from device.")
                        .setPositiveButton("OK") { _, _ -> onBackPressed() }
                        .show()
                }
            }

            onCharacteristicRead = { _, characteristic, value ->
                log("Read from ${characteristic.uuid}: ${value.toHexString()}")
            }

            onCharacteristicWrite = { _, characteristic ->
                log("Wrote to ${characteristic.uuid}")
            }

            onMtuChanged = { _, mtu ->

                log("MTU updated to $mtu")
            }

            onCharacteristicChanged = { _, characteristic, value ->
                logCustomFormat(characteristic, value)
            }

            onNotificationsEnabled = { _, characteristic ->
                log("Enabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.add(characteristic.uuid)
            }

            onNotificationsDisabled = { _, characteristic ->
                log("Disabled notifications on ${characteristic.uuid}")
                notifyingCharacteristics.remove(characteristic.uuid)
            }
        }
    }

    private enum class CharacteristicProperty {
        Readable,
        Writable,
        WritableWithoutResponse,
        Notifiable,
        Indicatable;

        val action
            get() = when (this) {
                Readable -> "Read"
                Writable -> "Write"
                WritableWithoutResponse -> "Write Without Response"
                Notifiable -> "Toggle Notifications"
                Indicatable -> "Toggle Indications"
            }
    }

    /*
    private fun Activity.hideKeyboard() {
        hideKeyboard(currentFocus ?: View(this))
    }

    private fun Context.hideKeyboard(view: View) {
        val inputMethodManager = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }  */

    private fun EditText.showKeyboard() {
        val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        requestFocus()
        inputMethodManager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun String.hexToBytes() =
        this.chunked(2).map { it.uppercase(Locale.US).toInt(16).toByte() }.toByteArray()
}
