package com.punchthrough.blestarterappandroid

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileOutputStream

class FileManager(private val context: Context) {

    // Method to write data to external storage (Scoped Storage for Android 11+)
    @SuppressLint("LogNotTimber")
    fun writeToExternalFile(fileName: String, data: String) {
        // Check if the device is running Android 11 (API 30) or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // For Android 11 and above, use scoped storage (no permission required)
            writeToScopedStorage(fileName, data)
        } else {
            // For Android 10 and below, check if external storage is writable
            if (isExternalStorageWritable()) {
                val file = File(context.getExternalFilesDir(null), fileName)
                try {
                    FileOutputStream(file, true).use { output ->
                        output.write((data + "\n").toByteArray())
                    }
                    Log.d("FileManager", "Data written to external file: $fileName")
                } catch (e: Exception) {
                    e.printStackTrace()
                    Log.e("FileManager", "Error writing to file: ${e.localizedMessage}")
                }
            } else {
                Log.e("FileManager", "External storage is not writable.")
            }
        }
    }

    // Scoped Storage for Android 11+ (no permissions required)
    @SuppressLint("LogNotTimber")
    private fun writeToScopedStorage(fileName: String, data: String) {
        val file = File(context.getExternalFilesDir(null), fileName)
        try {
            FileOutputStream(file, true).use { output ->
                output.write((data + "\n").toByteArray())
            }
            Log.d("FileManager", "Data written to scoped storage: $fileName")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("FileManager", "Error writing to file: ${e.localizedMessage}")
        }
    }

    // Method to check if external storage is writable (for Android 10 and below)
    private fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
}
