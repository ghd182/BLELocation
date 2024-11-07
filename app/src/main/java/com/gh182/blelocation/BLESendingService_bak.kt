//package com.gh182.blelocation
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.bluetooth.BluetoothManager
//import android.bluetooth.le.AdvertiseData
//import android.bluetooth.le.AdvertiseSettings
//import android.bluetooth.le.BluetoothLeAdvertiser
//import android.content.Intent
//import android.content.pm.PackageManager
//import android.os.*
//import android.util.Log
//import androidx.core.app.NotificationCompat
//import androidx.core.content.ContextCompat
//import java.nio.ByteBuffer
//import android.Manifest
//import android.R
//import android.app.Service
//import android.bluetooth.le.AdvertiseCallback
//import android.os.ParcelUuid
//
//class BLESendingService_bak : Service() {
//    private lateinit var bleAdvertiser: BluetoothLeAdvertiser
//    private var isAdvertising = false
//    private val notificationId = 1
//    private val serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB"
//
//    override fun onCreate() {
//        super.onCreate()
//        checkNotificationPermission()
//        bleAdvertiser = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter.bluetoothLeAdvertiser
//        createNotificationChannel()
//        // startForeground(notificationId, createNotification("Waiting to advertise location..."))
//    }
//
//    override fun onDestroy() {
//        super.onDestroy()
//        stopAdvertising() // Ensure BLE advertising is stopped when service is destroyed
//    }
//
//
//
//    private fun checkNotificationPermission() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
//            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//            Log.w("BLEAdvertisingService", "Missing POST_NOTIFICATIONS permission.")
//            stopSelf()
//        }
//    }
//
//    private fun checkPermissions(): Boolean {
//        Log.d("BLEAdvertisingService", "Checking permissions...")
//        val permissions = listOf(
//            Manifest.permission.BLUETOOTH_ADVERTISE,
//            Manifest.permission.BLUETOOTH_SCAN,
//            Manifest.permission.BLUETOOTH_CONNECT,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        )
//        return permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
//    }
//
//    private fun hasBluetoothAdvertisePermission() =
//        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
//
//
//
//
//
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        // Retrieve latitude, longitude, altitude, and accuracy as Double
//        Log.d("BLEAdvertisingService", "onStartCommand called")
//        if (intent == null) {
//            Log.e("BLEAdvertisingService", "Intent is null!")
//            return START_NOT_STICKY
//        }else{
//            Log.d("BLEAdvertisingService", "Intent is not null!")
//
//            val customLocationEnabled = intent.getBooleanExtra("customLocationEnabled", false)
//
//            if (checkPermissions()) {
//                if(customLocationEnabled){
//                    Log.d("BLEAdvertisingService", "Custom location enabled!")
//                    val latitude = intent.getDoubleExtra("latitude", 0.0)
//                    val longitude = intent.getDoubleExtra("longitude", 0.0)
//                    val altitude = intent.getDoubleExtra("altitude", 0.0)
//                    val accuracy: Double = intent.getFloatExtra("accuracy_key", 0.0f).toDouble()
//                    startBLEAdvertising (latitude, longitude, altitude, accuracy)
//                }else{
//                    Log.d("BLEAdvertisingService", "Custom location disabled!")
//                    startBLEAdvertising(0.0, 0.0, 0.0, 0.0)
//                }
//            } else {
//                Log.e("BLEAdvertisingService", "Permissions not granted!")
//                stopSelf()
//            }
//            return START_STICKY
//        }
//    }
//
//
//    private fun startBLEAdvertising(latitude: Double, longitude: Double, altitude: Double, accuracy: Double) {
//        if (isAdvertising) {
//            Log.d("BLEAdvertisingService", "Already advertising, ignoring start request.")
//            return
//        }
//        // Ensure to stop previous advertising before starting new
//        stopAdvertising()
//
//        // Add a small delay here if necessary
//        Handler(Looper.getMainLooper()).post {
//            val buffer = ByteBuffer.allocate(32) // Increase size for additional precision
//                .putFloat(latitude.toFloat())
//                .putFloat(longitude.toFloat())
//                .putFloat(altitude.toFloat())
//                .putFloat(accuracy.toFloat()) // Ensure accuracy is still a Float
//
//            val advertisement = AdvertiseData.Builder()
//                .addServiceUuid(ParcelUuid.fromString(serviceUuid))
//                .addServiceData(ParcelUuid.fromString(serviceUuid), buffer.array())
//                .setIncludeDeviceName(true)
//                .build()
//
//            val settings = AdvertiseSettings.Builder()
//                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
//                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
//                .setConnectable(true)
//                .build()
//
//            if (hasBluetoothAdvertisePermission()) {
//                try {
//                    bleAdvertiser.startAdvertising(settings, advertisement, advertiseCallback)
//                    isAdvertising = true
//                    updateNotification("BLE Advertising location: ($latitude, $longitude, $altitude, $accuracy)")
//                } catch (e: SecurityException) {
//                    Log.e("BLEAdvertisingService", "Failed to start advertising: ${e.message}")
//                }
//            } else {
//                Log.e("BLEAdvertisingService", "Missing BLUETOOTH_ADVERTISE permission.")
//            }
//        }
//    }
//
//    private val advertiseCallback = object : AdvertiseCallback() {
//        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
//            isAdvertising = true
//            Log.d("BLEAdvertisingService", "Advertising started successfully")
//        }
//
////        override fun onStartFailure(errorCode: Int) {
////            isAdvertising = false
////            Log.e("BLEAdvertisingService", "Advertising failed with error code: $errorCode")
////            updateNotification("Advertising failed with error code: $errorCode")
////        }
//    }
//
//
//    private fun stopAdvertising() {
//        Log.d("BLEAdvertisingService", "Try to stop advertising")
//        if (isAdvertising) {
//            if (hasBluetoothAdvertisePermission()) {
//                try {
//                    bleAdvertiser.stopAdvertising(advertiseCallback)
//                    isAdvertising = false
//                    Log.d("BLEAdvertisingService", "Advertising stopped")
//                    updateNotification("Advertising stopped")
//                } catch (e: SecurityException) {
//                    Log.e("BLEAdvertisingService", "Failed to stop advertising: ${e.message}")
//                }
//            } else {
//                Log.e("BLEAdvertisingService", "Missing BLUETOOTH_ADVERTISE permission.")
//            }
//        }
//    }
//
//
//
//
//    private fun createNotificationChannel() {
//        val channel = NotificationChannel("ble_advertising_channel", "BLE Advertising Service", NotificationManager.IMPORTANCE_DEFAULT)
//        val manager = getSystemService(NotificationManager::class.java)
//        manager.createNotificationChannel(channel)
//    }
//
//    private fun createNotification(contentText: String): Notification {
//        Log.d("BLEAdvertisingService", "Notification: $contentText")
//        return NotificationCompat.Builder(this, "ble_advertising_channel")
//            .setContentTitle("BLE Advertising")
//            .setContentText(contentText)
//            .setSmallIcon(R.drawable.ic_menu_mylocation)
//            .build()
//    }
//
//    private fun updateNotification(contentText: String) {
//        Log.d("BLEAdvertisingService", "Notification updated: $contentText")
//        val manager = getSystemService(NotificationManager::class.java)
//        manager.notify(notificationId, createNotification(contentText))
//    }
//
//
//
//
//    override fun onBind(intent: Intent?): IBinder? = null
//}
