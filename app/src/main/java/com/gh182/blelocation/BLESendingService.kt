package com.gh182.blelocation

import android.app.PendingIntent
import android.content.Intent
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.pm.PackageManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.nio.ByteBuffer
import android.Manifest
import android.location.Location
import android.os.ParcelUuid
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BLEAdvertisingService"
private const val NOTIFICATION_CHANNEL_ID = "ble_advertising_channel"
private const val NOTIFICATION_ID = 1
private const val SERVICE_UUID = "0000180F-0000-1000-8000-00805F9B34FB"
private const val DEFAULT_UPDATE_INTERVAL = 10000

class BLESendingService : Service() {
    private lateinit var bleAdvertiser: BluetoothLeAdvertiser
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val isAdvertising = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentAdvertisingJob: Runnable? = null
    private var currentLocation: Location? = null
    private val advertisingLock = Object()


    override fun onCreate() {
        super.onCreate()
        initializeService()
    }

    private fun initializeService() {
        if (!checkRequiredPermissions()) {
            Log.e(TAG, "Missing required permissions")
            stopSelf()
            return
        }

        try {
            bleAdvertiser = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager)
                .adapter.bluetoothLeAdvertiser
            fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
            createNotificationChannel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize service", e)
            stopSelf()
        }
    }

    private fun checkRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            ).all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        } else {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ).all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val customLocationEnabled = intent?.getBooleanExtra("customLocationEnabled", false) == true
        // use the frequency from the intent if provided, otherwise use the default
        val updateInterval = intent?.getIntExtra("frequency", DEFAULT_UPDATE_INTERVAL)
            ?: DEFAULT_UPDATE_INTERVAL
        val advertisePower = intent?.getIntExtra("advertisePower", 0) ?: 0
        val advertiseMode = intent?.getIntExtra("advertiseMode", 0) ?: 0
        Log.d(TAG, "Starting service with customLocationEnabled=$customLocationEnabled, updateInterval=$updateInterval")
        Log.d(TAG, "advertisePower=$advertisePower, advertiseMode=$advertiseMode")

        if (customLocationEnabled) {
            handleCustomLocation(intent, updateInterval.toLong(), advertisePower, advertiseMode)
        } else {
            startLocationUpdates(updateInterval.toLong(), advertisePower, advertiseMode)
        }

        return START_STICKY
    }

    private fun handleCustomLocation(intent: Intent?, updateInterval: Long, advertisePower: Int, advertiseMode: Int) {
        val location = Location("custom").apply {
            latitude = intent?.getDoubleExtra("latitude", 0.0) ?: 0.0
            longitude = intent?.getDoubleExtra("longitude", 0.0) ?: 0.0
            altitude = intent?.getDoubleExtra("altitude", 0.0) ?: 0.0
            accuracy = intent?.getFloatExtra("accuracy", 0f) ?: 0f
        }
        advertiseLocation(location, updateInterval, advertisePower, advertiseMode)
    }

    private fun startLocationUpdates(updateInterval: Long, advertisePower: Int, advertiseMode: Int) {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "New Location: ${location.latitude}, ${location.longitude}")
                    synchronized(advertisingLock) {
                        currentLocation = location
                        // Only start a new advertising cycle if we're not currently advertising
                        if (!isAdvertising.get()) {
                            advertiseLocation(location, updateInterval, advertisePower, advertiseMode)
                        } else {
                            // Update the current advertising with new location
                            updateAdvertising(location, updateInterval, advertisePower, advertiseMode)
                        }
                    }
                }
            }
        }.also { callback ->
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
                .setMinUpdateIntervalMillis(updateInterval)
                .build()

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Missing location permissions")
                return
            }
            fusedLocationClient.requestLocationUpdates(
                request,
                callback,
                Looper.getMainLooper()
            )
        }
    }

    private fun updateAdvertising(location: Location, updateInterval: Long, advertisePower: Int, advertiseMode: Int) {
        Log.d(TAG, "Updating advertising with new location")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission")
            return
        }

        synchronized(advertisingLock) {
            try {
                // Stop current advertising
                bleAdvertiser.stopAdvertising(advertiseCallback)

                // Start advertising with new location
                val advertiseData = createAdvertiseData(location)

                 // PowerHigh: 3, PowerMedium: 2, PowerLow: 1, PowerUltraLow: 0
                // ModeLowLatency: 2, ModeBalanced: 1, ModeLowPower: 0


                val settings = createAdvertiseSettings(advertisePower, advertiseMode)




                bleAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)

                // Update notification with new location
                updateNotification(String.format(
                    Locale.getDefault(),
                    "Location: %.6f, %.6f\nAltitude: %.3fm, Accuracy: %.3fm",
                    location.latitude, location.longitude, location.altitude, location.accuracy
                ))
                // Reset timer for next update
                currentAdvertisingJob?.let { mainHandler.removeCallbacks(it) }
                currentAdvertisingJob = Runnable {
                    Log.d(TAG, "Stopping advertising after $updateInterval ms")
                    synchronized(advertisingLock) {
                        stopCurrentAdvertising()
                        isAdvertising.set(false)
                        // Use the most recent location when restarting
                        currentLocation?.let { latest ->
                            advertiseLocation(latest, updateInterval, advertisePower, advertiseMode)
                        }
                    }
                }.also {
                    Log.d(TAG, "Scheduling new advertising in $updateInterval ms")
                    mainHandler.postDelayed(it, updateInterval)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update advertising", e)
                // In case of failure, stop current advertising and restart
                stopCurrentAdvertising()
                isAdvertising.set(false)
                currentLocation?.let { latest ->
                    advertiseLocation(latest, updateInterval, advertisePower, advertiseMode)
                }
            }
        }
    }

    private fun advertiseLocation(location: Location, updateInterval: Long, advertisePower: Int, advertiseMode: Int) {
        Log.d(TAG, "Starting advertising")
        if (!isAdvertising.compareAndSet(false, true)) {
            return
        }

        synchronized(advertisingLock) {
            currentLocation = location
            val advertiseData = createAdvertiseData(location)
            val settings = createAdvertiseSettings(advertisePower, advertiseMode)

            try {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_ADVERTISE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission")
                    return
                }
                bleAdvertiser.startAdvertising(settings, advertiseData, advertiseCallback)
                updateNotification(String.format(
                    Locale.getDefault(),
                    "Location: %.6f, %.6f\nAltitude: %.3fm, Accuracy: %.3fm",
                    location.latitude, location.longitude, location.altitude, location.accuracy
                ))
                currentAdvertisingJob?.let { mainHandler.removeCallbacks(it) }
                currentAdvertisingJob = Runnable {
                    Log.d(TAG, "Stopping advertising after $updateInterval ms")
                    synchronized(advertisingLock) {
                        stopCurrentAdvertising()
                        isAdvertising.set(false)
                        // Use the most recent location when restarting
                        currentLocation?.let { latest ->
                            advertiseLocation(latest, updateInterval, advertisePower, advertiseMode)
                        }
                    }
                }.also {
                    Log.d(TAG, "Scheduling new advertising in $updateInterval ms")
                    mainHandler.postDelayed(it, updateInterval)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start advertising", e)
                isAdvertising.set(false)
            }
        }
    }

    private fun createAdvertiseData(location: Location): AdvertiseData {
        val buffer = ByteBuffer.allocate(16).apply {
            putFloat(location.latitude.toFloat())
            putFloat(location.longitude.toFloat())
            putFloat(location.altitude.toFloat())
            putFloat(location.accuracy)
        }

        return AdvertiseData.Builder()
            .addServiceUuid(ParcelUuid.fromString(SERVICE_UUID))
            .addServiceData(ParcelUuid.fromString(SERVICE_UUID), buffer.array())
            .build()
    }

    private fun createAdvertiseSettings( advertisePower: Int, advertiseMode: Int) = AdvertiseSettings.Builder()
        // AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
        .setAdvertiseMode(advertiseMode)
        // AdvertiseSettings.ADVERTISE_TX_POWER_ULTRA_LOW
        .setTxPowerLevel(advertisePower)
        .setConnectable(true)
        .build()

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "Advertising failed with error code: $errorCode")
            isAdvertising.set(false)
            updateNotification("Advertising failed: Error $errorCode")
        }
    }

    private fun stopCurrentAdvertising() {
        try {

            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission")
                return
            }
            bleAdvertiser.stopAdvertising(advertiseCallback)
            Log.d(TAG, "Advertising stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop advertising", e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "BLE Advertising Service",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun updateNotification(content: String) {
        // Create an intent to open the app
        val intent = Intent(this, MainActivity::class.java).apply {
            // This will bring the existing instance of the activity to the foreground if it exists
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Create a PendingIntent to wrap the intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Build the notification with the pending intent
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("BLE Advertising")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
//            .setContentIntent(pendingIntent)  // Set the intent that will fire when the user taps the notification
            .setAutoCancel(true)  // Remove notification when tapped
            .build()

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanup()
    }

    private fun cleanup() {
        currentAdvertisingJob?.let { mainHandler.removeCallbacks(it) }
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        if (isAdvertising.get()) {
            stopCurrentAdvertising()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}