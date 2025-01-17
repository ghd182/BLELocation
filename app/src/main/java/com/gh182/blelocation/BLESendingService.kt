package com.gh182.blelocation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "BLEAdvertisingService"
private const val NOTIFICATION_CHANNEL_ID = "ble_advertising_channel"
private const val NOTIFICATION_ID = 1
private const val SERVICE_UUID = "0000180F-0000-1000-8000-00805F9B34FB"
private const val DEFAULT_UPDATE_INTERVAL = 10000L

class BLESendingService : Service() {
    private lateinit var bleAdvertiser: BluetoothLeAdvertiser
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val isAdvertising = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var currentAdvertisingJob: Runnable? = null
    private var currentLocation: Location? = null
    private val advertisingLock = Any()

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
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val customLocationEnabled = intent?.getBooleanExtra("customLocationEnabled", false) == true
        val updateInterval = intent?.getLongExtra("frequency", DEFAULT_UPDATE_INTERVAL)
            ?: DEFAULT_UPDATE_INTERVAL
        val bleOptionPower = intent?.getIntExtra("bleOptionPower", 0) ?: 0
        val bleOptionMode = intent?.getIntExtra("bleOptionMode", 0) ?: 0
        Log.d(TAG, "Starting service with customLocationEnabled=$customLocationEnabled, updateInterval=$updateInterval")
        Log.d(TAG, "bleOptionPower=$bleOptionPower, bleOptionMode=$bleOptionMode")

        if (customLocationEnabled) {
            handleCustomLocation(intent, updateInterval, bleOptionPower, bleOptionMode)
        } else {
            startLocationUpdates(updateInterval, bleOptionPower, bleOptionMode)
        }

        return START_STICKY
    }

    private fun handleCustomLocation(intent: Intent?, updateInterval: Long, bleOptionPower: Int, bleOptionMode: Int) {
        val location = Location("custom").apply {
            latitude = intent?.getDoubleExtra("latitude", 0.0) ?: 0.0
            longitude = intent?.getDoubleExtra("longitude", 0.0) ?: 0.0
            altitude = intent?.getDoubleExtra("altitude", 0.0) ?: 0.0
            accuracy = intent?.getFloatExtra("accuracy", 0f) ?: 0f
        }
        advertiseLocation(location, updateInterval, bleOptionPower, bleOptionMode)
    }

    private fun startLocationUpdates(updateInterval: Long, bleOptionPower: Int, bleOptionMode: Int) {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    Log.d(TAG, "New Location: ${location.latitude}, ${location.longitude}")
                    synchronized(advertisingLock) {
                        currentLocation = location
                        if (!isAdvertising.get()) {
                            advertiseLocation(location, updateInterval, bleOptionPower, bleOptionMode)
                        } else {
                            updateAdvertising(location, updateInterval, bleOptionPower, bleOptionMode)
                        }
                    }
                }
            }
        }.also { callback ->
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, updateInterval)
                .setMinUpdateIntervalMillis(updateInterval)
                .build()

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing location permissions")
                return
            }
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }
    }

    private fun updateAdvertising(location: Location, updateInterval: Long, bleOptionPower: Int, bleOptionMode: Int) {
        Log.d(TAG, "Updating advertising with new location")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Missing BLUETOOTH_ADVERTISE permission")
            return
        }

        synchronized(advertisingLock) {
            try {
                bleAdvertiser.stopAdvertising(advertiseCallback)
                val advertiseData = createAdvertiseData(location)
                val settings = createAdvertiseSettings(bleOptionPower, bleOptionMode)
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
                        currentLocation?.let { latest ->
                            advertiseLocation(latest, updateInterval, bleOptionPower, bleOptionMode)
                        }
                    }
                }.also {
                    Log.d(TAG, "Scheduling new advertising in $updateInterval ms")
                    mainHandler.postDelayed(it, updateInterval)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update advertising", e)
                stopCurrentAdvertising()
                isAdvertising.set(false)
                currentLocation?.let { latest ->
                    advertiseLocation(latest, updateInterval, bleOptionPower, bleOptionMode)
                }
            }
        }
    }

    private fun advertiseLocation(location: Location, updateInterval: Long, bleOptionPower: Int, bleOptionMode: Int) {
        Log.d(TAG, "Starting advertising")
        if (!isAdvertising.compareAndSet(false, true)) {
            return
        }

        synchronized(advertisingLock) {
            currentLocation = location
            val advertiseData = createAdvertiseData(location)
            val settings = createAdvertiseSettings(bleOptionPower, bleOptionMode)

            try {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
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
                        currentLocation?.let { latest ->
                            advertiseLocation(latest, updateInterval, bleOptionPower, bleOptionMode)
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

    private fun createAdvertiseSettings(bleOptionPower: Int, bleOptionMode: Int) = AdvertiseSettings.Builder()
        .setAdvertiseMode(bleOptionMode)
        .setTxPowerLevel(bleOptionPower)
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
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun updateNotification(content: String) {
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("BLE Advertising")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setAutoCancel(true)
            .build()

        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
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