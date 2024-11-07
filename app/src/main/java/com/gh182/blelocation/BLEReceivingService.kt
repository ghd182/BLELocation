package com.gh182.blelocation

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import java.nio.ByteBuffer
import android.content.pm.PackageManager
import android.bluetooth.BluetoothManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Locale

class BLEReceivingService : Service() {
    private lateinit var bleScanner: BluetoothLeScanner
    private val serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB"
    private val notificationId = 1
    private val notificationChannelId = "ble_scan_channel"



    override fun onCreate() {
        super.onCreate()
        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        bleScanner = bluetoothManager?.adapter?.bluetoothLeScanner ?: run {
            Log.e("BLEReceivingService", "BLE scanner initialization failed")
            stopSelf()
            return
        }
        createNotificationChannel()
        startForeground(1, createNotification("Waiting for BLE Locations..."))
        startScanning()
    }

    private fun startScanning() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            Log.e("BLEReceivingService", "Missing BLUETOOTH_SCAN permission")
            stopSelf()
            return
        }

        bleScanner.startScan(scanCallback)
        Log.d("BLEReceivingService", "Started scanning for BLE devices.")
    }






    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val serviceData = result.scanRecord?.getServiceData(ParcelUuid.fromString(serviceUuid))
            if (serviceData != null && serviceData.size >= 16) {
                val buffer = ByteBuffer.wrap(serviceData)
                val latitude = buffer.float.toDouble()
                val longitude = buffer.float.toDouble()
                val altitude = buffer.float.toDouble()
                val accuracy = buffer.float.toDouble()

                Log.d("BLEReceivingService", "Received Location: ($latitude, $longitude, Alt: $altitude, Acc: $accuracy)")

                updateNotification(String.format(
                    Locale.getDefault(),
                    "Location: %.6f, %.6f\nAltitude: %.3fm, Accuracy: %.3fm",
                    latitude, longitude, altitude, accuracy
                ))

                injectMockLocation(latitude, longitude, altitude, accuracy)
            } else {
                Log.d("BLEReceivingService", "No valid service data found or insufficient data size")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e("BLEReceivingService", "Scan failed with error code: $errorCode")
            updateNotification("Scan failed. Waiting for location...")
        }
    }

    private fun injectMockLocation(latitude: Double, longitude: Double, altitude: Double, accuracy: Double) {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager


        // Ensure the GPS provider is disabled so real location is not used
        try {
            locationManager.removeUpdates{} // Stop receiving updates from other providers
            // Disable GPS provider to prevent real GPS location
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)

            // Optionally, you can disable other location providers:
//            locationManager.setTestProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
//            locationManager.setTestProviderEnabled(LocationManager.PASSIVE_PROVIDER, false)
        } catch (e: SecurityException) {
            Log.e("BLEReceivingService", "Failed to disable GPS provider: ${e.message}")
        }

        try {
            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false, false, false, false, true, true, true,
                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
                android.location.provider.ProviderProperties.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)



            val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = longitude
                this.altitude = altitude
                this.accuracy = accuracy.toFloat()
                this.time = System.currentTimeMillis()
                this.elapsedRealtimeNanos = System.nanoTime()
            }
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)

            Log.d("BLEReceivingService", "Mock location set: $latitude, $longitude, Alt: $altitude, Acc: $accuracy")
        } catch (e: SecurityException) {
            Log.e("BLEReceivingService", "Failed to set mock location: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(notificationChannelId, "BLE Receiver", NotificationManager.IMPORTANCE_DEFAULT)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {


        // Create an intent to open the app
        val intent = Intent(this, MainActivity::class.java).apply {
            // This will bring the existing instance of the activity to the foreground if it exists
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        // Create a PendingIntent to wrap the intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification with the pending intent
        return NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("BLE Receiving Location")
            .setContentText(contentText)
//            .setContentIntent(pendingIntent)  // Set the intent that will fire when the user taps the notification
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, createNotification(contentText))
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            bleScanner.stopScan(scanCallback)
            Log.d("BLEReceivingService", "Stopped scanning for BLE devices.")
        } catch (e: SecurityException) {
            Log.e("BLEReceivingService", "Bluetooth scan permission error: ${e.message}")
        }
    }
}