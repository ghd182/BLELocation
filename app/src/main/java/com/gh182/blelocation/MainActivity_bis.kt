//package com.gh182.blelocation
//
//import com.google.android.gms.location.LocationCallback
//import com.google.android.gms.location.LocationResult
//import com.google.android.gms.location.FusedLocationProviderClient
//import android.os.Looper
//import android.os.Bundle
//import androidx.activity.ComponentActivity
//import androidx.activity.compose.setContent
//import androidx.compose.foundation.layout.fillMaxSize
//import androidx.compose.foundation.layout.padding
//import androidx.compose.material3.Scaffold
//import androidx.compose.material3.Text
//import androidx.compose.runtime.Composable
//import androidx.compose.ui.Modifier
//import com.gh182.blelocation.ui.theme.BLELocationTheme
//import kotlinx.coroutines.Job
//import androidx.compose.foundation.layout.*
//import androidx.compose.material3.*
//import androidx.compose.runtime.*
//import androidx.compose.ui.unit.dp
//import androidx.core.app.ActivityCompat
//import kotlinx.coroutines.delay
//import kotlinx.coroutines.launch
//import android.location.Location
//import android.util.Log
//import android.Manifest
//import android.content.pm.PackageManager
//import android.os.Build
//import android.widget.Toast
//import androidx.activity.result.contract.ActivityResultContracts
//import androidx.compose.ui.platform.LocalContext
//import androidx.compose.ui.text.input.TextFieldValue
//import androidx.core.content.ContextCompat
//import androidx.compose.ui.text.input.KeyboardType
//import android.app.NotificationChannel
//import android.app.NotificationManager
//import android.content.Context
//import androidx.core.app.NotificationCompat
//import androidx.core.app.NotificationManagerCompat
//import androidx.compose.runtime.mutableStateOf
//import androidx.compose.runtime.remember
//import com.google.android.gms.location.LocationServices
//import com.google.android.gms.location.LocationRequest.Builder
//import com.google.android.gms.location.Priority
//import android.bluetooth.BluetoothAdapter
//import android.bluetooth.le.AdvertiseCallback
//import android.bluetooth.le.AdvertiseSettings
//import android.bluetooth.BluetoothManager
//import android.bluetooth.le.*
//import java.nio.ByteBuffer
//import android.bluetooth.le.AdvertiseData
//import android.os.ParcelUuid
//
//import android.bluetooth.le.BluetoothLeScanner
//import android.bluetooth.le.ScanCallback
//import android.bluetooth.le.ScanResult
//import android.location.LocationManager
//
//
//// Define the request code constant for POST_NOTIFICATIONS
//const val POST_NOTIFICATION_REQUEST_CODE = 1002
//const val CHANNEL_ID = "ble_location_channel"
//const val START_NOTIFICATION_ID = 1
//const val LOCATION_NOTIFICATION_ID = 2
//
//
//
//class MainActivity : ComponentActivity() {
//    private var bleAdvertiser: BluetoothLeAdvertiser? = null
//    private lateinit var bleScanner: BluetoothLeScanner
//    private var isScanning = false
//    private val serviceUuid = "0000180F-0000-1000-8000-00805F9B34FB"
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        val bluetoothAdapter: BluetoothAdapter? = (getSystemService(BLUETOOTH_SERVICE) as BluetoothManager).adapter
//        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
//            Toast.makeText(this, "Please enable Bluetooth.", Toast.LENGTH_SHORT).show()
//            // Bluetooth is not supported or not enabled
//            Log.e("BLEAdvertising", "Bluetooth is not supported or not enabled.")
//            return
//        }
//
//
//        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
//            Log.e("BLEAdvertising", "Device does not support BLE.")
//            return
//        }
//
//
//
//        requestPermissionsIfNeeded()
//        createNotificationChannel(this)
//        bleAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
//        bleScanner = bluetoothAdapter.bluetoothLeScanner
//
//        // Request necessary permissions
//        if (ContextCompat.checkSelfPermission(
//                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
//        ) {
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//                ActivityCompat.requestPermissions(
//                    this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), POST_NOTIFICATION_REQUEST_CODE
//                )
//            }
//        }
//
//        setContent {
//            BLELocationTheme {
//                MainScreen(
//                    onStartScanning = { startScanning() },
//                    onStopScanning = { stopScanning() }
//                )
//            }
//        }
//    }
//
//
//
//
//    private val scanCallback = object : ScanCallback() {
//
//        override fun onScanResult(callbackType: Int, result: ScanResult) {
//            val serviceData = result.scanRecord?.getServiceData(ParcelUuid.fromString(serviceUuid))
//
//            Log.d("BLEReceiver", "Processing scan result with UUID: ${result.scanRecord}")
//
//            if (serviceData == null) {
//                Log.d("LocationReceiverService", "No valid data received. Skipping...")
//                return
//            }
//
//            // Check if the advertisement contains the service UUID you are interested in
//            Log.i("BLEReceiver", "Checking for: $serviceUuid")
//            serviceData.let { data ->
//                if (data.size >= 16) { // Ensure we have enough data for 4 floats
//                    // Convert the byte array back to location parameters
//                    val latitude = ByteBuffer.wrap(data, 0, 4).float.toDouble()
//                    val longitude = ByteBuffer.wrap(data, 4, 4).float.toDouble()
//                    val altitude = ByteBuffer.wrap(data, 8, 4).float.toDouble()
//                    val accuracy = ByteBuffer.wrap(data, 12, 4).float.toDouble()
//
//                    // Use the received location data (e.g., set as mock location)
//                    Log.i("BLEReceiver", "Received Location - Lat: $latitude, Lon: $longitude, Alt: $altitude, Acc: $accuracy")
//
//                    // Here you would implement logic to set the mock location using the received data
//                    setMockLocation(latitude, longitude, altitude, accuracy)
//                } else {
//                    Log.e("BLEReceiver", "Received service data does not contain enough bytes.")
//                }
//            }
//        }
//
//        override fun onScanFailed(errorCode: Int) {
//            Log.e("BLEReceiver", "Scan failed with error code: $errorCode")
//        }
//    }
//
//    private fun startScanning() {
//        try {
//            bleScanner.startScan(scanCallback)
//            Log.d("LocationReceiverService", "Started scanning for BLE devices.")
//        } catch (e: SecurityException) {
//            Log.e("LocationReceiverService", "Bluetooth scan permission error: ${e.message}")
//        }
//    }
//
//    private fun stopScanning() {
//        if (!isScanning) return
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.BLUETOOTH_SCAN
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            Log.e("BLEReceiver", "Permission denied: BLUETOOTH_SCAN")
//            return
//        }
//        bleScanner.stopScan(scanCallback)
//        isScanning = false
//        Log.i("BLEReceiver", "Stopped scanning for BLE devices")
//    }
//
//
//    private fun setMockLocation(latitude: Double, longitude: Double, altitude: Double, accuracy: Double) {
//        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
//
//        // Create a new Location object
//        val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
//            this.latitude = latitude
//            this.longitude = longitude
//            this.altitude = altitude
//            this.accuracy = accuracy.toFloat() // set accuracy as float
//            this.time = System.currentTimeMillis()
//            this.elapsedRealtimeNanos = System.nanoTime() // Optional, set elapsed real-time
//        }
//
//
//        try {
//            // Add a test provider
//            locationManager.addTestProvider(
//                LocationManager.GPS_PROVIDER,
//                false, false, false, false, true, true, true,
//                android.location.provider.ProviderProperties.POWER_USAGE_LOW,
//                android.location.provider.ProviderProperties.ACCURACY_FINE
//            )
//            // Enable the test provider
//            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
//
//            // Set the mock location
//            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
//
//            Log.d("LocationReceiverService", "Mock location set: $latitude, $longitude, Altitude: $altitude, Accuracy: $accuracy")
//        } catch (e: SecurityException) {
//            Log.e("LocationReceiverService", "Failed to set mock location: ${e.message}")
//        }
//    }
//
//
//    private fun requestPermissionsIfNeeded() {
//        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
//            arrayOf(
//                Manifest.permission.ACCESS_FINE_LOCATION,
//                Manifest.permission.BLUETOOTH_SCAN,
//                Manifest.permission.BLUETOOTH_CONNECT,
//                Manifest.permission.POST_NOTIFICATIONS,
//                Manifest.permission.BLUETOOTH_ADVERTISE
//            )
//        } else {
//            // Handle lower versions if needed
//            arrayOf(
//                Manifest.permission.ACCESS_FINE_LOCATION,
//                Manifest.permission.BLUETOOTH,
//                Manifest.permission.BLUETOOTH_ADMIN
//            )
//        }
//
//        permissions.forEach { permission ->
//            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
//                requestPermissionLauncher.launch(permission)
//            }
//        }
//    }
//
//    private val requestPermissionLauncher = registerForActivityResult(
//        ActivityResultContracts.RequestPermission()
//    ) { isGranted: Boolean ->
//        if (!isGranted) {
//            Toast.makeText(this, "Permission is required for BLE functionality", Toast.LENGTH_LONG).show()
//        }
//    }
//
//    private var isAdvertising = false
//
//    fun startBLEAdvertising(latitude: Double, longitude: Double, altitude: Double, accuracy: Double) {
//        // Validate latitude and longitude
//        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
//            Log.e("BLEAdvertising", "Invalid latitude or longitude values. Not advertising.")
//            return
//        }
//
//        if (isAdvertising) {
//            Log.e("BLEAdvertising", "Already advertising. Stopping current advertising.")
//            stopBLEAdvertising()
//        }
//
//        val settings = AdvertiseSettings.Builder()
//            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
//            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
//            .setConnectable(true)
//            .build()
//
//        // Prepare the byte buffer with the location data
//        val buffer = ByteBuffer.allocate(16)
//            .putFloat(latitude.toFloat())
//            .putFloat(longitude.toFloat())
//            .putFloat(altitude.toFloat())
//            .putFloat(accuracy.toFloat())
//
//        val advertisement = AdvertiseData.Builder()
//            .addServiceUuid(ParcelUuid.fromString(serviceUuid)) // Adding service UUID for identification
//            .addServiceData(ParcelUuid.fromString(serviceUuid), buffer.array()) // Add the location data
//            .setIncludeDeviceName(true) // Optional: include device name in advertisement
//            .build()
//
//        Log.i("BLEAdvertising", "Starting BLE advertising with UUID: $serviceUuid")
//        Log.i("BLEAdvertising", "Advertisement Data: ${advertisement.serviceData}")
//
//        // Check if Bluetooth advertising is supported
//        bleAdvertiser?.let { advertiser ->
//            try {
//                Log.i("BLEAdvertising", "Starting BLE advertising...")
//                advertiser.startAdvertising(settings, advertisement, advertiseCallback)
//                Toast.makeText(this, "BLE advertising started.", Toast.LENGTH_SHORT).show()
//            } catch (e: SecurityException) {
//                Log.e("BLEAdvertising", "Permission denied: ${e.message}")
//                Toast.makeText(this, "BLE advertising requires permission.", Toast.LENGTH_SHORT).show()
//            }
//        } ?: run {
//            Log.e("BLEAdvertising", "BLE advertising is not supported on this device.")
//        }
//        isAdvertising = true
//    }
//
//    fun stopBLEAdvertising() {
//        if (ActivityCompat.checkSelfPermission(
//                this,
//                Manifest.permission.BLUETOOTH_ADVERTISE
//            ) != PackageManager.PERMISSION_GRANTED
//        ) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            Log.e("BLEAdvertising", "Permission denied: BLUETOOTH_ADVERTISE")
//            return
//        }
//        bleAdvertiser?.stopAdvertising(advertiseCallback)
//        isAdvertising = false
//    }
//
//    private val advertiseCallback = object : AdvertiseCallback() {
//        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
//            Log.d("LocationSenderService", "Advertising started successfully")
//        }
//
//        override fun onStartFailure(errorCode: Int) {
//            Log.e("LocationSenderService", "Advertising failed with error code: $errorCode")
//        }
//    }
//
//    @Composable
//    fun MainScreen(onStartScanning: () -> Unit, onStopScanning: () -> Unit) {
//        val context = LocalContext.current
//        var mode by remember { mutableStateOf("Send") }
//        var frequency by remember { mutableStateOf(TextFieldValue("1000")) }
//        var customLocationEnabled by remember { mutableStateOf(false) }
//        var customLatitude by remember { mutableStateOf(TextFieldValue("0")) }
//        var customLongitude by remember { mutableStateOf(TextFieldValue("0")) }
//        var customAltitude by remember { mutableStateOf(TextFieldValue("0")) }
//        var customAccuracy by remember { mutableStateOf(TextFieldValue("1")) }
//        var isRunning by remember { mutableStateOf(false) }
//        var job by remember { mutableStateOf<Job?>(null) }
//
//        // Validation checks
//        val isFrequencyValid = frequency.text.toIntOrNull()?.let { it in 100..3600000 } == true
//        val isLatitudeValid = customLatitude.text.toDoubleOrNull()?.let { it in -90.0..90.0 } == true
//        val isLongitudeValid = customLongitude.text.toDoubleOrNull()?.let { it in -180.0..180.0 } == true
//        val isAccuracyValid = customAccuracy.text.toDoubleOrNull()?.let { it > 0 } == true
//        val areCustomParamsValid = isLatitudeValid && isLongitudeValid && isAccuracyValid
//
//        // Coroutine scope and job management
//        val coroutineScope = rememberCoroutineScope()
//
//
//        Scaffold(
//            modifier = Modifier.fillMaxSize(),
//            content = { innerPadding ->
//                Column(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .padding(innerPadding)
//                        .padding(16.dp),
//                    verticalArrangement = Arrangement.spacedBy(8.dp)
//                ) {
//                    Text("BLE Location Sender/Receiver", style = MaterialTheme.typography.titleLarge)
//
//                    ModeTabs(selectedMode = mode, onModeChange = { mode = it })
//                    FrequencyInput(
//                        frequency = frequency,
//                        onFrequencyChange = { frequency = it },
//                        isValid = isFrequencyValid,
//                        enabled = !isRunning // Disable when running
//                    )
//
//                    if (mode == "Send") {
//                        CustomLocationToggle(
//                            enabled = customLocationEnabled,
//                            onToggle = { customLocationEnabled = it }
//                        )
//
//                        if (customLocationEnabled) {
//                            CustomLocationInput(
//                                latitude = customLatitude,
//                                longitude = customLongitude,
//                                altitude = customAltitude,
//                                accuracy = customAccuracy,
//                                onLatitudeChange = { customLatitude = it },
//                                onLongitudeChange = { customLongitude = it },
//                                onAltitudeChange = { customAltitude = it },
//                                onAccuracyChange = { customAccuracy = it },
//                                isLatitudeValid = isLatitudeValid,
//                                isLongitudeValid = isLongitudeValid,
//                                isAccuracyValid = isAccuracyValid
//                            )
//                        }
//                    }
//
//                    if (mode == "Receive") {
//                        Button(
//                            onClick = {
//                                if (isRunning) {
//                                    onStopScanning()
//                                } else {
//                                    onStartScanning()
//                                }
//                                isRunning = !isRunning
//                            },
//                            modifier = Modifier.fillMaxWidth()
//                        ) {
//                            Text(if (isRunning) "Stop Scanning" else "Start Scanning")
//                        }
//                    }else if(mode == "Send"){
//                        Button(
//                            onClick = {
//                                isRunning = !isRunning
//                                if (isRunning) {
//                                    showStartNotification(context)
//                                    job = coroutineScope.launch {
//                                        while (isRunning) {
//                                            if (customLocationEnabled) {
//                                                // Use custom location parameters
//                                                val latitude =
//                                                    customLatitude.text.toDoubleOrNull() ?: 0.0
//                                                val longitude =
//                                                    customLongitude.text.toDoubleOrNull() ?: 0.0
//                                                val altitude =
//                                                    customAltitude.text.toDoubleOrNull() ?: 0.0
//                                                val accuracy =
//                                                    customAccuracy.text.toDoubleOrNull() ?: 1.0
//                                                startBLEAdvertising(
//                                                    latitude,
//                                                    longitude,
//                                                    altitude,
//                                                    accuracy
//                                                )
//                                                showLocationNotification(
//                                                    context,
//                                                    latitude,
//                                                    longitude,
//                                                    altitude,
//                                                    accuracy
//                                                )
//                                            } else {
//                                                // Use current location parameters
//                                                retrieveCurrentLocation(
//                                                    context,
//                                                    frequency.text.toLongOrNull() ?: 1000
//                                                ) { location ->
//                                                    location?.let {
//                                                        startBLEAdvertising(
//                                                            it.latitude,
//                                                            it.longitude,
//                                                            it.altitude,
//                                                            it.accuracy.toDouble()
//                                                        )
//                                                        showLocationNotification(
//                                                            context,
//                                                            it.latitude,
//                                                            it.longitude,
//                                                            it.altitude,
//                                                            it.accuracy.toDouble()
//                                                        )
//                                                    } ?: run {
//                                                        Toast.makeText(
//                                                            context,
//                                                            "Unable to retrieve current location",
//                                                            Toast.LENGTH_SHORT
//                                                        ).show()
//                                                    }
//                                                }
//                                            }
//                                            // Wait for the specified frequency before the next update
//                                            delay(frequency.text.toLongOrNull() ?: 1000)
//                                        }
//                                    }
//                                } else {
//                                    job?.cancel() // Cancel the job when stopping
//                                    // Remove the notifications when stopping
//                                    cancelNotification(context, START_NOTIFICATION_ID)
//                                    cancelNotification(context, LOCATION_NOTIFICATION_ID)
//                                }
//                            },
//                            enabled = isFrequencyValid && (!customLocationEnabled || areCustomParamsValid),
//                            modifier = Modifier.fillMaxWidth()
//                        ) {
//                            Text(if (isRunning) "Stop" else "Start")
//                        }
//                    }
//                }
//            }
//        )
//    }
//
//
//
//    fun retrieveCurrentLocation(context: Context, frequencyMillis: Long, onLocationRetrieved: (Location?) -> Unit) {
//        val fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
//        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            return
//        }
//        // Check permission before requesting location updates
//        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            Log.e("LocationRequest", "Location permission not granted")
//            onLocationRetrieved(null)
//            return
//        }
//
//        // Create a LocationRequest using the frequencyMillis value
//        val locationRequest = Builder(Priority.PRIORITY_HIGH_ACCURACY, frequencyMillis)
//            .setMinUpdateIntervalMillis(frequencyMillis / 2) // Fastest interval is half the frequency
//            .build()
//
//        // Create a LocationCallback
//        val locationCallback = object : LocationCallback() {
//            override fun onLocationResult(locationResult: LocationResult) {
//                if (locationResult.locations.isNotEmpty()) {
//                    val location: Location? = locationResult.lastLocation // Nullable Location
//                    if (location != null) {
//                        onLocationRetrieved(location) // Call the function only if location is not null
//
//                        // Cancel the start notification after receiving the first location update
//                        cancelNotification(context, START_NOTIFICATION_ID)
//
//                        // Remove location updates after getting the location
//                        fusedLocationClient.removeLocationUpdates(this)
//                    } else {
//                        Log.e("LocationCallback", "Received null location")
//                        onLocationRetrieved(null)
//                    }
//                }
//            }
//        }
//
//        // Request location updates
//        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
//    }
//
//    private fun createNotificationChannel(context: Context) {
//        val channel = NotificationChannel(
//            CHANNEL_ID,
//            "BLE Location Service Notifications",
//            NotificationManager.IMPORTANCE_HIGH
//        )
//        val manager = context.getSystemService(NotificationManager::class.java)
//        manager?.createNotificationChannel(channel)
//    }
//
//    private fun cancelNotification(context: Context, notificationId: Int) {
//        NotificationManagerCompat.from(context).cancel(notificationId)
//    }
//
//    private fun showStartNotification(context: Context) {
//        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
//            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your drawable
//            .setContentTitle("Service Started")
//            .setContentText("Waiting for location...")
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//
//        try {
//            with(NotificationManagerCompat.from(context)) {
//                notify(START_NOTIFICATION_ID, builder.build())
//            }
//        } catch (e: SecurityException) {
//            e.printStackTrace() // Log the exception or handle it as needed
//        }
//    }
//
//    fun showLocationNotification(
//        context: Context, latitude: Double, longitude: Double,
//        altitude: Double, accuracy: Double
//    ) {
//        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
//            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your drawable
//            .setContentTitle("Location Update")
//            .setContentText("Lat: ${latitude}, Lon: ${longitude},\nAlt: ${altitude}m (accuracy: ${accuracy}m)")
//            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setAutoCancel(true)
//        try {
//            with(NotificationManagerCompat.from(context)) {
//                notify(LOCATION_NOTIFICATION_ID, builder.build())
//            }
//            // Start BLE advertising with the location data
//        } catch (e: SecurityException) {
//            e.printStackTrace() // Log the exception or handle it as needed
//        }
//    }
//
//
//
//
//
//
//
//
//
//
//    @Composable
//    fun ModeTabs(selectedMode: String, onModeChange: (String) -> Unit) {
//        val tabs = listOf("Send", "Receive")
//        TabRow(selectedTabIndex = tabs.indexOf(selectedMode)) {
//            tabs.forEach { tab ->
//                Tab(
//                    text = { Text(tab) },
//                    selected = selectedMode == tab,
//                    onClick = { onModeChange(tab) }
//                )
//            }
//        }
//    }
//    @Composable
//    fun FrequencyInput(
//        frequency: TextFieldValue,
//        onFrequencyChange: (TextFieldValue) -> Unit,
//        isValid: Boolean,
//        enabled: Boolean // New parameter to control enabled state
//    ) {
//        Column {
//            OutlinedTextField(
//                value = frequency,
//                onValueChange = onFrequencyChange,
//                label = { Text("Frequency (ms)") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//                isError = !isValid,
//                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
//                enabled = enabled // Set enabled state
//            )
//            if (!isValid) {
//                Text("Frequency must be between 100 and 3600000 ms", color = MaterialTheme.colorScheme.error)
//            }
//        }
//    }
//
//
//    @Composable
//    fun CustomLocationToggle(enabled: Boolean, onToggle: (Boolean) -> Unit) {
//        Row(
//            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
//            modifier = Modifier.fillMaxWidth()
//        ) {
//            Text("Use Custom Location")
//            Spacer(modifier = Modifier.weight(1f))
//            Switch(
//                checked = enabled,
//                onCheckedChange = onToggle
//            )
//        }
//    }
//
//    @Composable
//    fun CustomLocationInput(
//        latitude: TextFieldValue,
//        longitude: TextFieldValue,
//        altitude: TextFieldValue,
//        accuracy: TextFieldValue,
//        onLatitudeChange: (TextFieldValue) -> Unit,
//        onLongitudeChange: (TextFieldValue) -> Unit,
//        onAltitudeChange: (TextFieldValue) -> Unit,
//        onAccuracyChange: (TextFieldValue) -> Unit,
//        isLatitudeValid: Boolean,
//        isLongitudeValid: Boolean,
//        isAccuracyValid: Boolean
//    ) {
//        Column {
//            OutlinedTextField(
//                value = latitude,
//                onValueChange = onLatitudeChange,
//                label = { Text("Latitude") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//                isError = !isLatitudeValid,
//                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
//            )
//            if (!isLatitudeValid) {
//                Text("Latitude must be between -90 and 90", color = MaterialTheme.colorScheme.error)
//            }
//
//            OutlinedTextField(
//                value = longitude,
//                onValueChange = onLongitudeChange,
//                label = { Text("Longitude") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//                isError = !isLongitudeValid,
//                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
//
//                )
//            if (!isLongitudeValid) {
//                Text("Longitude must be between -180 and 180", color = MaterialTheme.colorScheme.error)
//            }
//
//            OutlinedTextField(
//                value = altitude,
//                onValueChange = onAltitudeChange,
//                label = { Text("Altitude (meters)") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
//            )
//
//            OutlinedTextField(
//                value = accuracy,
//                onValueChange = onAccuracyChange,
//                label = { Text("Accuracy (meters)") },
//                singleLine = true,
//                modifier = Modifier.fillMaxWidth(),
//                isError = !isAccuracyValid,
//                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
//            )
//            if (!isAccuracyValid) {
//                Text("Accuracy must be a positive number", color = MaterialTheme.colorScheme.error)
//            }
//        }
//    }
//}