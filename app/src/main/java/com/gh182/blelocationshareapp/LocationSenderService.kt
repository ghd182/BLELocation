// sender
package com.gh182.blelocationshareapp

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.nio.ByteBuffer


class LocationSenderService : Service() {
    private lateinit var advertiser: BluetoothLeAdvertiser
    private var isAdvertising = false

    private val notificationId = 1
    private val serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB" // Ensure this is correct for your use case

    private var useCustomLocation: Boolean = false
    private var customLocation: Location? = null
    private var isLocationUpdatesStarted = false

    override fun onDestroy() {
        super.onDestroy()
        isLocationUpdatesStarted = false // Reset the flag
        // Add any other cleanup tasks here, like stopping advertising
        if (isAdvertising) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.w("onDestroy","not able to stop advertising")
                return
            }
            advertiser.stopAdvertising(advertiseCallback)
            isAdvertising = false
        }
        Log.d("LocationSenderService", "Service destroyed")
    }

    override fun onCreate() {
        super.onCreate()
        checkAndRequestNotificationPermission()

        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        advertiser = bluetoothAdapter.bluetoothLeAdvertiser

        createNotificationChannel()
        startForeground(notificationId, createNotification("Waiting for location..."))

        if (checkPermissions() && !isLocationUpdatesStarted) {
            startSendingLocation()
            isLocationUpdatesStarted = true
        } else {
            Log.e("LocationSenderService", "Permissions not granted!")
            stopSelf()
        }
    }


    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        Log.d("onStartCommand", "onStartCommand called")
        when (intent?.action) {
            ACTION_STOP_LOCATION_UPDATES -> {
                stopLocationUpdates() // Stop location updates and release resources
                stopSelf() // Stop the service
                return START_NOT_STICKY // Prevent the service from restarting automatically
            }

            else -> {

                intent?.let {

                    useCustomLocation = it.getBooleanExtra("USE_CUSTOM_LOCATION", false)
                    Log.d("LocationSenderService", "Custom location: $useCustomLocation")
                    if (useCustomLocation) {
                        // Retrieve custom location data
                        val latitude = it.getStringExtra("LATITUDE")?.toDoubleOrNull() ?: 0.0
                        val longitude = it.getStringExtra("LONGITUDE")?.toDoubleOrNull() ?: 0.0
                        val altitude = it.getStringExtra("ALTITUDE")?.toDoubleOrNull() ?: 0.0
                        val accuracy = it.getStringExtra("ACCURACY")?.toFloatOrNull() ?: 0.0f
                        val timeFreq = it.getStringExtra("TIME_FREQ")?.toLongOrNull() ?: 0

                        customLocation = Location("CustomLocation").apply {
                            this.latitude = latitude
                            this.longitude = longitude
                            this.altitude = altitude
                            this.accuracy = accuracy
                        }
                    }
                }
                return START_STICKY
            }
        }
    }

    private fun stopLocationUpdates() {
        // ... (Logic to stop location updates and release resources, e.g., using FusedLocationProviderClient)
        // ... (Stop Bluetooth advertising if necessary)
        isLocationUpdatesStarted = false // Reset the flag
        Log.d("LocationSenderService", "Location updates stopped")
    }

    private fun checkAndRequestNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("LocationSenderService", "Missing POST_NOTIFICATIONS permission.")
                stopSelf()
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "location_sender_channel",
            "Location Sender Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, "location_sender_channel")
            .setContentTitle("Location Sender")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
    }

    private fun updateNotification(location: Location) {
        val contentText = "Location: ${location.latitude}, ${location.longitude}, Altitude: ${location.altitude} m, Accuracy: ${location.accuracy} m"
        val notification = createNotification(contentText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, notification)
        Log.d("LocationSender", "Notification data: $location")
    }

    private fun checkPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT
        )

        return permissions.all { perm ->
            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun startSendingLocation() {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                fusedLocationClient.requestLocationUpdates(locationRequest, object : LocationCallback() {
                    override fun onLocationResult(locationResult: LocationResult) {
                        val location = if (useCustomLocation && customLocation != null) {
                            customLocation
                        } else {
                            locationResult.lastLocation
                        }
                        if (location != null) {
                            updateNotification(location)
                            advertiseLocation(location)
                            Log.d("LocationSender", "Sending data: $location")
                        }
                    }
                }, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            Log.e("LocationSenderService", "Location permission error: ${e.message}")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            isAdvertising = true
            Log.d("LocationSenderService", "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            isAdvertising = false
            Log.e("LocationSenderService", "Advertising failed with error code: $errorCode")
        }
    }

    private fun advertiseLocation(location: Location) {
        if (isAdvertising && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED) {
            advertiser.stopAdvertising(advertiseCallback)
        }

        val buffer = ByteBuffer.allocate(24) // 4 + 4 + 8 + 4 bytes
            .putFloat(location.latitude.toFloat())
            .putFloat(location.longitude.toFloat())
            .putFloat(location.altitude.toFloat())
            .putFloat(location.accuracy)

        val data = AdvertiseData.Builder()
            .addServiceData(android.os.ParcelUuid.fromString(serviceUuid), buffer.array())
            .build()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_LOW)
            .setConnectable(false)
            .build()

        try {
            isAdvertising = true
            advertiser.startAdvertising(settings, data, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e("LocationSenderService", "Bluetooth advertise permission error: ${e.message}")
        }
    }

    override fun onBind(intent: android.content.Intent?): IBinder? = null
}