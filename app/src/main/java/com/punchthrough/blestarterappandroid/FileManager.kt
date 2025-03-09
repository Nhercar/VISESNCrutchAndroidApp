package com.punchthrough.blestarterappandroid

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import timber.log.Timber
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

    // Add a sealed class for file operations results
    sealed class FileOperationResult {
        object Success : FileOperationResult()
        data class Error(val message: String) : FileOperationResult()
    }

    private val csvFile: File
        get() = File(context.getExternalFilesDir(null), "log_data.csv")

    fun writeToCSVFile(fileName: String, data: List<String>): FileOperationResult {
        return try {
            FileOutputStream(csvFile, true).use { output ->
                val csvLine = data.joinToString(",") + "\n"
                output.write(csvLine.toByteArray())
            }
            Timber.d("Data written to CSV file: $fileName")
            FileOperationResult.Success
        } catch (e: Exception) {
            Timber.e(e, "Error writing to file")
            FileOperationResult.Error(e.localizedMessage ?: "Unknown error")
        }
    }

    fun deleteCsvFile(): FileOperationResult {
        return when {
            !csvFile.exists() -> {
                Timber.w("CSV file does not exist")
                FileOperationResult.Error("File does not exist")
            }
            csvFile.delete() -> {
                Timber.i("CSV file deleted successfully")
                FileOperationResult.Success
            }
            else -> {
                Timber.e("Failed to delete CSV file")
                FileOperationResult.Error("Failed to delete file")
            }
        }.also { result ->
            val message = when (result) {
                is FileOperationResult.Success -> "CSV file deleted successfully"
                is FileOperationResult.Error -> "Error: ${result.message}"
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Method to share the CSV file
    fun shareCsvFile() {
        val csvFile = File(context.getExternalFilesDir(null), "log_data.csv")

        if (csvFile.exists()) {
            val fileUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                csvFile
            )

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share CSV file"))
        } else {
            Timber.e("CSV file not found")
            Toast.makeText(context, "CSV file not found", Toast.LENGTH_SHORT).show()
        }
    }

}
