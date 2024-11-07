//package com.gh182.blelocation
//
//import android.Manifest
//import android.R
//import android.bluetooth.le.BluetoothLeScanner
//import android.bluetooth.le.ScanResult
//import android.content.Intent
//import android.location.Location
//import android.location.LocationManager
//import android.os.IBinder
//import android.os.ParcelUuid
//import android.util.Log
//import java.nio.ByteBuffer
//import android.content.pm.PackageManager
//import android.bluetooth.BluetoothManager
//
//
//import android.app.Notification
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.app.Service
//import android.bluetooth.le.ScanCallback
//import androidx.core.app.ActivityCompat
//import androidx.core.app.NotificationCompat
//import androidx.core.content.ContextCompat
//
//class BLEReceivingService_bak : Service() {
//    private lateinit var bleScanner: BluetoothLeScanner
//    private val serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB"
//    private val notificationId = 1
//    private val channelId = "ble_scanning_channel"
//    private var isScanning = false
//
//    override fun onCreate() {
//        super.onCreate()
//        Log.d("BLEScanningService", "Service created.")
//        createNotificationChannel()
//        startForeground(notificationId, createNotification("Waiting for BLE devices..."))
//        initializeBluetoothScanner()
//        if (checkPermissions()) {
//            startScanning()
//        } else {
//            Log.e("BLEScanningService", "Permissions not granted! Stopping service.")
//            stopSelf()
//        }
//    }
//
//    private fun createNotificationChannel() {
//        val channel = NotificationChannel(
//            channelId,
//            "BLE Scanning Service",
//            NotificationManager.IMPORTANCE_DEFAULT
//        )
//        val manager = getSystemService(NotificationManager::class.java)
//        manager?.createNotificationChannel(channel)
//        Log.d("BLEScanningService", "Notification channel created.")
//    }
//
//    private fun createNotification(contentText: String): Notification {
//        return NotificationCompat.Builder(this, channelId)
//            .setContentTitle("BLE Scanner")
//            .setContentText(contentText)
//            .setSmallIcon(R.drawable.ic_menu_mylocation)
//            .build()
//    }
//
//    private fun updateNotification(contentText: String) {
//        val notification = createNotification(contentText)
//        val manager = getSystemService(NotificationManager::class.java)
//        manager?.notify(notificationId, notification)
//        Log.d("BLEScanningService", "Notification updated: $contentText")
//    }
//
//    private fun initializeBluetoothScanner() {
//        val bluetoothManager = getSystemService(BluetoothManager::class.java)
//        val bluetoothAdapter = bluetoothManager?.adapter
//        bleScanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
//            Log.e("BLEScanningService", "Bluetooth is not enabled or BluetoothManager not available. Stopping service.")
//            stopSelf()
//            return
//        }
//        Log.d("BLEScanningService", "Bluetooth scanner initialized.")
//    }
//
//    private fun checkPermissions(): Boolean {
//        val permissions = listOf(
//            Manifest.permission.BLUETOOTH_SCAN,
//            Manifest.permission.BLUETOOTH_CONNECT,
//            Manifest.permission.ACCESS_FINE_LOCATION,
//            Manifest.permission.ACCESS_COARSE_LOCATION
//        )
//        val allPermissionsGranted = permissions.all { perm ->
//            ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
//        }
//        Log.d("BLEScanningService", "Permission check: $allPermissionsGranted")
//        return allPermissionsGranted
//    }
//
//    private fun startScanning() {
//        if (isScanning) {
//            Log.d("BLEScanningService", "Scanning is already in progress.")
//            return
//        }
//        if (checkPermissions()) {
//            if (ActivityCompat.checkSelfPermission(
//                    this,
//                    Manifest.permission.BLUETOOTH_SCAN
//                ) != PackageManager.PERMISSION_GRANTED
//            ) {
//                Log.e("BLEScanningService", "Permission not granted for scanning. Cannot start scanning.")
//                return
//            }
//            bleScanner.startScan(scanCallback)
//            isScanning = true
//            Log.d("BLEScanningService", "Started scanning for BLE devices.")
//            updateNotification("Scanning for BLE devices...")
//        } else {
//            Log.e("BLEScanningService", "Permission not granted for scanning.")
//        }
//    }
//
//    private fun stopScanning() {
//        if (!isScanning) {
//            Log.d("BLEScanningService", "Scanning is not in progress. Nothing to stop.")
//            return
//        }
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.BLUETOOTH_SCAN
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            Log.e("BLEScanningService", "Permission not granted for stopping scan.")
//            return
//        }
//        bleScanner.stopScan(scanCallback)
//        isScanning = false
//        Log.d("BLEScanningService", "Stopped scanning for BLE devices.")
//    }
//
//    private val scanCallback = object : ScanCallback() {
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            Log.d("BLEScanningService", "Scan result received.")
//            processScanResult(result)
//        }
//
//        override fun onScanFailed(errorCode: Int) {
//            Log.e("BLEScanningService", "Scan failed with error code: $errorCode")
//            updateNotification("Scan failed. Waiting for devices...")
//        }
//    }
//
//    private fun processScanResult(result: ScanResult) {
//        Log.w("BLEScanningService", "Received scan result: ${result.scanRecord}")
//        val serviceData = result.scanRecord?.getServiceData(ParcelUuid.fromString(serviceUuid))
//        serviceData?.let { data ->
//            if (data.size >= 16) {
//                val latitude = ByteBuffer.wrap(data, 0, 4).float.toDouble()
//                val longitude = ByteBuffer.wrap(data, 4, 4).float.toDouble()
//                val altitude = ByteBuffer.wrap(data, 8, 4).float.toDouble()
//                val accuracy = ByteBuffer.wrap(data, 12, 4).float.toDouble()
//
//                Log.i("BLEScanningService", "Received Location: ($latitude, $longitude), Altitude: $altitude, Accuracy: $accuracy")
//                setMockLocation(latitude, longitude, altitude, accuracy)
//                updateNotification("Received Location: $latitude, $longitude, Altitude: $altitude, Accuracy: $accuracy")
//            } else {
//                Log.w("BLEScanningService", "Service data is not sufficient. Size: ${data.size}")
//            }
//        } ?: run {
//            Log.w("BLEScanningService", "No service data found in scan result.")
//        }
//    }
//
//    private fun setMockLocation(latitude: Double, longitude: Double, altitude: Double, accuracy: Double) {
//        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
//        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
//            this.latitude = latitude
//            this.longitude = longitude
//            this.altitude = altitude
//            this.accuracy = accuracy.toFloat()
//            this.time = System.currentTimeMillis()
//        }
//        try {
//            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
//            Log.d("BLEScanningService", "Mock location set: ($latitude, $longitude), Altitude: $altitude, Accuracy: $accuracy")
//        } catch (e: SecurityException) {
//            Log.e("BLEScanningService", "Failed to set mock location: ${e.message}")
//        }
//    }
//
//    override fun onDestroy() {
//        Log.d("BLEScanningService", "Service is being destroyed.")
//        stopScanning()
//        super.onDestroy()
//    }
//
//    override fun onBind(intent: Intent?): IBinder? = null
//}
