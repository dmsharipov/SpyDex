package com.example.lab1

import android.Manifest.permission.*
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.android.synthetic.main.activity_main.*
import lab1.R


class ManagePermissions(
    private val activity: Activity,
    private val listPermissions: List<String>,
    private val code: Int
)
{
    // Check if permissions are granted
    fun checkPermissions() {
        var i=0
        for (permission in listPermissions) {
            i += ContextCompat.checkSelfPermission(activity, permission)
        }

        if (i != PackageManager.PERMISSION_GRANTED) {
            showAlert()
        }
        else {
            Log.d("MY_TAG", "Permissions are granted.")
        }
    }

    // Get first denied permission
    private fun deniedPermission(): String {
        for (permission in listPermissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_DENIED)
                return permission
        }

        return ""
    }

    // Show alert with permission request
    private fun showAlert() {
        val builder = AlertDialog.Builder(activity)
        builder.setTitle("Need permission(s)")
        builder.setMessage("Some permissions are required to do the task.")
        builder.setPositiveButton("OK") { _, _ -> requestPermissions() }
        builder.setNeutralButton("Cancel", null)
        val dialog = builder.create()
        dialog.show()
    }

    // Request necessary permissions
    private fun requestPermissions() {
        val permission = deniedPermission()
        if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
            Toast.makeText(activity, "Permissions are needed!", Toast.LENGTH_LONG).show()
        }
        ActivityCompat.requestPermissions(activity, listPermissions.toTypedArray(), code)
    }
}

class MainActivity : AppCompatActivity() {
    private lateinit var managePermissions: ManagePermissions
    private val REQ_CODE = 123

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestPermissions()

        val startBtn: Button = findViewById<View>(R.id.start_button) as Button
        val stopBtn: Button = findViewById<View>(R.id.stop_button) as Button
        val serviceClass = Service::class.java
        val intent = Intent(this, serviceClass)

        startBtn.setOnClickListener{
            if (!isServiceRunning(serviceClass)) {
                startService(intent)
                toast("Starting service...")
                textView.text = getString(R.string.service_is_running)
            } else {
                toast("Service is already running...")
            }
        }

        stopBtn.setOnClickListener{
            if (isServiceRunning(serviceClass)) {
                stopService(intent)
                toast("Stopping service")
                textView.text = getString(R.string.service_is_not_running)

            } else {
                toast("Service is already stopped.")
            }
        }

    }

    private fun requestPermissions() {
        val permissionsList = listOf(
            READ_CONTACTS,
            READ_SMS,
            READ_CALL_LOG,
            GET_ACCOUNTS,
            INTERNET,
            ACCESS_NETWORK_STATE
        )
        managePermissions = ManagePermissions(this, permissionsList, REQ_CODE)
        managePermissions.checkPermissions()
    }
    // Custom method to determine whether a service is running
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        // Loop through the running services
        for (service in activityManager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                // If the service is running then return true
                return true
            }
        }
        return false
    }
}

fun Context.toast(message: String) {
    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
}