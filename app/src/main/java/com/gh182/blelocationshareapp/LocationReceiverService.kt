package com.gh182.blelocationshareapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer

import android.bluetooth.BluetoothManager
import android.Manifest
import android.os.ParcelUuid

class LocationReceiverService : Service() {
    private lateinit var scanner: BluetoothLeScanner
    private val notificationId = 2
    private val channelId = "location_receiver_channel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(notificationId, createNotification("Waiting for location..."))
        initializeBluetoothScanner()
        if (checkPermissions()) {
            startScanning()
        } else {
            Log.e("LocationReceiverService", "Permissions not granted!")
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            channelId,
            "Location Receiver Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Location Receiver")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
    }

    private fun initializeBluetoothScanner() {
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        if (bluetoothManager != null) {
            val bluetoothAdapter = bluetoothManager.adapter
            scanner = bluetoothAdapter.bluetoothLeScanner
        } else {
            Log.e("LocationReceiverService", "BluetoothManager not available")
        }
    }

    private fun checkPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startScanning() {
        try {
            scanner.startScan(scanCallback)
            Log.d("LocationReceiverService", "Started scanning for BLE devices.")
        } catch (e: SecurityException) {
            Log.e("LocationReceiverService", "Bluetooth scan permission error: ${e.message}")
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val data = result.scanRecord?.getServiceData(ParcelUuid.fromString("0000180F-0000-1000-8000-00805F9B34FB"))

            // Check if data is null
            if (data == null) {
                Log.d("LocationReceiverService", "No valid data received. Skipping...")
                return
            }

            // Proceed with processing if data is valid
            val buffer = ByteBuffer.wrap(data) // Wrap the data in a ByteBuffer
            val latitude = buffer.float // Extract latitude
            val longitude = buffer.float // Extract longitude
            val altitude = buffer.float // Extract altitude
            val accuracy = buffer.float // Extract accuracy

            Log.d("LocationReceiverService", "Received Location: ($latitude, $longitude), Altitude: $altitude, Accuracy: $accuracy")

            // Update notification and inject mock location
            updateNotification("Location: $latitude, $longitude, Altitude: $altitude m, Accuracy: $accuracy m")
            injectMockLocation(latitude, longitude, altitude, accuracy)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("LocationReceiverService", "Scan failed with error code: $errorCode")
            updateNotification("Scan failed. Waiting for location...")
        }
    }


    private fun injectMockLocation(latitude: Float, longitude: Float, altitude: Float, accuracy: Float) {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        // Create the mock location object
        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude.toDouble()
            this.longitude = longitude.toDouble()
            this.altitude = altitude.toDouble()
            this.accuracy = accuracy
            this.time = System.currentTimeMillis()
            this.elapsedRealtimeNanos = System.nanoTime()
        }

        try {
            // Add a test provider
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
            )
            // Enable the test provider
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

            // Set the mock location
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)

            Log.d("LocationReceiverService", "Mock location set: $latitude, $longitude, Altitude: $altitude, Accuracy: $accuracy")
        } catch (e: SecurityException) {
            Log.e("LocationReceiverService", "Failed to set mock location: ${e.message}")
        }

        // Note: Do not remove the provider immediately; you may want to keep it enabled while testing.
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            scanner.stopScan(scanCallback)
            Log.d("LocationReceiverService", "Stopped scanning for BLE devices.")
        } catch (e: SecurityException) {
            Log.e("LocationReceiverService", "Bluetooth scan permission error: ${e.message}")
        }
        Log.d("LocationReceiverService", "Service destroyed.")
    }
}
