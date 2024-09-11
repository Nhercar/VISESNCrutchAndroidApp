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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import timber.log.Timber

/**
 * Determine whether the current [Context] has been granted the relevant [Manifest.permission].
 */
fun Context.hasPermission(permissionType: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permissionType) ==
        PackageManager.PERMISSION_GRANTED
}

/**
 * Determine whether the current [Context] has been granted the relevant permissions to perform
 * Bluetooth operations depending on the mobile device's Android version.
 */
fun Context.hasRequiredBluetoothPermissions(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
            hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}


/**
 * Request for the necessary permissions for Bluetooth operations to work.
 */
fun Activity.requestRelevantBluetoothPermissions(requestCode: Int) {
    if (hasRequiredBluetoothPermissions()) {
        Timber.w("Required permission(s) for Bluetooth already granted")
        return
    }
    when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (bluetoothPermissionRationaleRequired()) {
                displayNearbyDevicesPermissionRationale(requestCode)
            } else {
                requestNearbyDevicesPermissions(requestCode)
            }
        }
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> {
            if (locationPermissionRationaleRequired()) {
                displayLocationPermissionRationale(requestCode)
            } else {
                requestLocationPermission(requestCode)
            }
        }
    }
}


//region Location permission
/*******************************************
 * Private functions
 *******************************************/



private fun Activity.locationPermissionRationaleRequired(): Boolean {
    return ActivityCompat.shouldShowRequestPermissionRationale(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}

// Created this functions to enable location in order to search and connect to BLE devices
fun Context.isLocationServicesEnabled(): Boolean {
    val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
        locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
}


private fun Activity.displayLocationPermissionRationale(requestCode: Int) {
    runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle(R.string.location_permission_required)
            .setMessage(R.string.location_permission_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requestLocationPermission(requestCode)
            }
            .setNegativeButton(R.string.quit) { _, _ -> finishAndRemoveTask() }
            .setCancelable(false)
            .show()
    }
}

private fun Activity.requestLocationPermission(requestCode: Int) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
        requestCode
    )
}
//endregion

//region Nearby Devices permissions
private fun Activity.bluetoothPermissionRationaleRequired(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) || ActivityCompat.shouldShowRequestPermissionRationale(
            this, Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        false
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Activity.displayNearbyDevicesPermissionRationale(requestCode: Int) {
    runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle(R.string.bluetooth_permission_required)
            .setMessage(R.string.bluetooth_permission_rationale)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                requestNearbyDevicesPermissions(requestCode)
            }
            .setNegativeButton(R.string.quit) { _, _ -> finishAndRemoveTask() }
            .setCancelable(false)
            .show()
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun Activity.requestNearbyDevicesPermissions(requestCode: Int) {
    ActivityCompat.requestPermissions(
        this,
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        ),
        requestCode
    )
}



/**
 * Check if the app is excluded from battery optimization.
 */
fun Context.isIgnoringBatteryOptimizations(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val powerManager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        return powerManager.isIgnoringBatteryOptimizations(packageName)
    }
    return true
}

/**
 * Request to ignore battery optimizations so the app can run in the background.
 */
@SuppressLint("BatteryLife")
fun Activity.requestBackgroundExecutionPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !isIgnoringBatteryOptimizations()) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(android.net.Uri.parse("package:$packageName"))
        startActivity(intent)
    } else {
        // Battery optimization is already ignored or not required on this OS version
        Timber.i("Battery optimization is either not required or already ignored.")
    }
}

/**
 * Display rationale for background execution permission.
 */
private fun Activity.displayBackgroundExecutionRationale(requestCode: Int) {
    runOnUiThread {
        AlertDialog.Builder(this)
            .setTitle("Background Execution Permission Required")
            .setMessage("To ensure the app can run in the background, we need permission to ignore battery optimizations.")
            .setPositiveButton("Grant Permission") { _, _ ->
                requestBackgroundExecutionPermission()
            }
            .setNegativeButton("Quit") { _, _ -> finishAndRemoveTask() }
            .setCancelable(false)
            .show()
    }
}
//endregion
