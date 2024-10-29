package com.gh182.blelocationshareapp

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.gh182.blelocationshareapp.ui.theme.BLELocationShareAppTheme



class MainActivity : ComponentActivity() {

    private val requiredPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT
    )

    private val intentKeys = mapOf(
        "USE_CUSTOM_LOCATION" to "USE_CUSTOM_LOCATION",
        "LATITUDE" to "LATITUDE",
        "LONGITUDE" to "LONGITUDE",
        "ALTITUDE" to "ALTITUDE",
        "ACCURACY" to "ACCURACY",
        "TIME_FREQ" to "TIME_FREQ"
    )

    // BluetoothLeScanner instance for performing BLE scans
    private var bluetoothLeScanner: BluetoothLeScanner? = null

    // State variables to track whether services are running
    private var isSenderRunning by mutableStateOf(false)
    private var isReceiverRunning by mutableStateOf(false)

    // State variables for custom location usage
    private var useCustomLocation by mutableStateOf(false)
    private var latitude by mutableStateOf("")
    private var longitude by mutableStateOf("")
    private var altitude by mutableStateOf("")
    private var accuracy by mutableStateOf("")

    private var useCustomPower by mutableStateOf(false)
    private var timeFreq by mutableStateOf("")

    // Permission request launcher for handling runtime permissions
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            // Log permission results
            permissions.entries.forEach {
                Log.d("MainActivity", "${it.key} = ${it.value}")
            }
            // Start services if permissions are granted
            if (checkPermissions()) {
                startServices()
            } else {
                Log.e("MainActivity", "Permissions not granted")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize BluetoothLeScanner
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothLeScanner = bluetoothManager.adapter.bluetoothLeScanner

        // Set the content of the activity
        setContent {
            BLELocationShareAppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen() // Call the composable function for the main screen
                }
            }
        }

        // Check and request permissions
        if (checkPermissions()) {
            startServices() // Start services if permissions are granted
        } else {
            requestPermissions() // Request permissions if not granted
        }
    }

    // Function to check if all required permissions are granted
    //    private fun checkPermissions(): Boolean {
    //        val requiredPermissions = listOf(
    //            Manifest.permission.ACCESS_FINE_LOCATION,
    //            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    //            Manifest.permission.BLUETOOTH_SCAN,
    //            Manifest.permission.BLUETOOTH_ADVERTISE,
    //            Manifest.permission.BLUETOOTH_CONNECT
    //        )
    //        return requiredPermissions.all { permission ->
    //            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    //        }
    //    }

    private fun checkPermissions() = requiredPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }



    // Function to request the necessary permissions from the user
    private fun requestPermissions() {
        val missingPermissions = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    // Start the appropriate services based on their current running state
    private fun startServices() {
        Log.d("startServices", "Starting/stopping services")
        if (isSenderRunning) startLocationSenderService() else stopLocationSenderService()
        if (isReceiverRunning) startLocationReceiverService() else stopLocationReceiverService()
    }

    // Function to start the location receiver service
    private fun startLocationReceiverService() {
        if (checkPermissions()) { // Ensure permissions are granted
            val receiverServiceIntent = Intent(this, LocationReceiverService::class.java)
            startService(receiverServiceIntent) // Start the service
            isReceiverRunning = true // Update running state
            Log.d("MainActivity", "Location Receiver Service Started")

            try {
                bluetoothLeScanner?.startScan(scanCallback) // Start Bluetooth scanning
                Log.d("MainActivity", "Bluetooth scanning started")
            } catch (e: SecurityException) {
                Log.e("MainActivity", "Bluetooth scanning failed due to missing permissions", e)
            }
        } else {
            Log.e("MainActivity", "Permissions not granted. Cannot start Location Receiver Service.")
            requestPermissions() // Request permissions if not granted
        }
    }

    // Function to stop the location receiver service
    private fun stopLocationReceiverService() {
        val receiverServiceIntent = Intent(this, LocationReceiverService::class.java).apply {
            action = ACTION_STOP_LOCATION_UPDATES // Add this line
        }
        stopService(receiverServiceIntent) // Stop the service
        isReceiverRunning = false // Update running state
        Log.d("MainActivity", "Location Receiver Service Stopped")

        try {
            bluetoothLeScanner?.stopScan(scanCallback) // Stop Bluetooth scanning
            Log.d("MainActivity", "Bluetooth scanning stopped")
        } catch (e: SecurityException) {
            Log.e("MainActivity", "Failed to stop Bluetooth scanning due to missing permissions", e)
        }
    }

    // Function to start the location sender service
    private fun startLocationSenderService() {
        if (checkPermissions()) {
            //            val senderServiceIntent = Intent(this, LocationSenderService::class.java)
            //            // Pass custom location settings to the service
            //            senderServiceIntent.putExtra("USE_CUSTOM_LOCATION", useCustomLocation)
            //            senderServiceIntent.putExtra("LATITUDE", latitude)
            //            senderServiceIntent.putExtra("LONGITUDE", longitude)
            //            senderServiceIntent.putExtra("ALTITUDE", altitude)
            //            senderServiceIntent.putExtra("ACCURACY", accuracy)
            //            startService(senderServiceIntent) // Start the service
            val intent = Intent(this, LocationSenderService::class.java).apply {
                putExtra(intentKeys["USE_CUSTOM_LOCATION"], useCustomLocation)
                putExtra(intentKeys["LATITUDE"], latitude)
                putExtra(intentKeys["LONGITUDE"], longitude)
                putExtra(intentKeys["ALTITUDE"], altitude)
                putExtra(intentKeys["ACCURACY"], accuracy)
                putExtra(intentKeys["TIME_FREQ"], timeFreq)
            }
            startService(intent)
            isSenderRunning = true
            Log.d("MainActivity", "Location Sender Service Started")

        } else {
            Log.e("MainActivity", "Permissions not granted. Cannot start Location Sender Service.")
            requestPermissions() // Request permissions if not granted
        }
    }

    // Function to stop the location sender service
    private fun stopLocationSenderService() {
        val senderServiceIntent = Intent(this, LocationSenderService::class.java)
        stopService(senderServiceIntent) // Stop the service
        isSenderRunning = false // Update running state
        Log.d("MainActivity", "Location Sender Service Stopped")
    }

    // Bluetooth ScanCallback to handle scan results
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            if (checkPermissions()) {
                logDeviceDetails(result, "Scan Result (type: $callbackType)")
            } else {
                Log.e("MainActivity", "Permissions not granted. Cannot access device details.")
            }
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            super.onBatchScanResults(results)
            if (checkPermissions()) {
                results.forEach { result ->
                    logDeviceDetails(result, "Batch Scan Result")
                }
            } else {
                Log.e("MainActivity", "Permissions not granted. Cannot access device details.")
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            Log.e("MainActivity", "Bluetooth scan failed with error code: $errorCode")
        }

        private fun logDeviceDetails(result: ScanResult, source: String) {
            if (checkPermissions()) { // Check permission before accessing device details
                try {
                    val deviceName = result.device.name ?: "Unknown"
                    val deviceAddress = result.device.address
                    Log.d("MainActivity", "$source: $deviceName - $deviceAddress")
                } catch (e: SecurityException) {
                    Log.e("MainActivity", "Failed to access device details: ${e.message}")
                }
            } else {
                Log.e("MainActivity", "Permissions not granted. Cannot access device details.")
            }
        }
    }

    // Composable function for the main UI screen
    @Composable
    fun MainScreen() {
        var errorMessageLocation by remember { mutableStateOf("") }
        var errorMessagePower by remember { mutableStateOf("") }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Your other UI elements go here
                Spacer(modifier = Modifier.height(32.dp))

                // custom location toggle switch
                CustomLocationToggle(
                    isChecked = useCustomLocation,
                    onCheckedChange = { useCustomLocation = it }
                )

                // Custom location fields
                if (useCustomLocation) {
                    CustomLocationFields(
                        latitude = latitude,
                        onLatitudeChange = { latitude = it },
                        longitude = longitude,
                        onLongitudeChange = { longitude = it },
                        altitude = altitude,
                        onAltitudeChange = { altitude = it },
                        accuracy = accuracy,
                        onAccuracyChange = { accuracy = it },
                        isLatitudeValid = { isLatitudeValid(latitude) },
                        isLongitudeValid = { isLongitudeValid(longitude) },
                        isAccuracyValid = { isAccuracyValid(accuracy) },
                        errorMessageLocation = errorMessageLocation
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                // custom location toggle switch
                CustomPowerToggle(
                    isChecked = useCustomPower,
                    onCheckedChange = { useCustomPower = it }
                )

                // Custom location fields
                if (useCustomPower) {
                    CustomPowerFields(
                        timeFreq = timeFreq,
                        onTimeFreqChange = { timeFreq = it },
                        isTimeFreqValid = { isTimeFreqValid(timeFreq) },
                        errorMessagePower = errorMessagePower
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Start/Stop buttons for the location sender
                LocationSenderButton(
                    isRunning = isSenderRunning,
                    useCustomLocation = useCustomLocation,
                    onClick = {
                        if (isSenderRunning) {
                            stopLocationSenderService()
                        } else if ((isLocationValid() || !useCustomLocation)&&(isPowerValid() || !useCustomPower)) {
                            errorMessageLocation = ""
                            errorMessagePower = ""
                            startLocationSenderService()
                        } else {
                            if(!(isLocationValid()|| !useCustomLocation))
                            errorMessageLocation = "Invalid location values. Please check inputs."

                            if(!(isPowerValid()||!useCustomPower))
                                errorMessagePower = "Invalid location values. Please check inputs."

                        }
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

               // Start/Stop button for the location receiver
                LocationReceiverButton(isRunning = isReceiverRunning) {
                    if (isReceiverRunning) stopLocationReceiverService() else startLocationReceiverService()
                }
            }

            // Fixed location sharing text at the top of the screen
            Text(
                "Location Sharing",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }
    }





    // Composable for custom power toggle switch
    @Composable
    fun CustomPowerToggle(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Custom Power Settings:")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }

    // Composable for custom location fields
    @Composable
    fun CustomPowerFields(
        timeFreq: String,
        onTimeFreqChange: (String) -> Unit,
        isTimeFreqValid: () -> Boolean,
        errorMessagePower: String
    ) {
        TextField(
            value = timeFreq,
            onValueChange = onTimeFreqChange,
            label = { Text("sending/receiving time interval") },
            placeholder = { Text("positive number in milliseconds") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !isTimeFreqValid()
        )
        if (errorMessagePower.isNotEmpty()) {
            Text(errorMessagePower, color = MaterialTheme.colorScheme.error)
        }
    }

    // Composable for custom location toggle switch
    @Composable
    fun CustomLocationToggle(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use Custom Location:")
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    }

    // Composable for custom location fields
    @Composable
    fun CustomLocationFields(
        latitude: String,
        onLatitudeChange: (String) -> Unit,
        longitude: String,
        onLongitudeChange: (String) -> Unit,
        altitude: String,
        onAltitudeChange: (String) -> Unit,
        accuracy: String,
        onAccuracyChange: (String) -> Unit,
        isLatitudeValid: () -> Boolean,
        isLongitudeValid: () -> Boolean,
        isAccuracyValid: () -> Boolean,
        errorMessageLocation: String
    ) {
        TextField(
            value = latitude,
            onValueChange = onLatitudeChange,
            label = { Text("latitude") },
            placeholder = { Text("between -90 and 90") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !isLatitudeValid()
        )
        TextField(
            value = longitude,
            onValueChange = onLongitudeChange,
            label = { Text("longitude") },
            placeholder = { Text("between -180 and 180") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !isLongitudeValid()
        )
        TextField(
            value = altitude,
            onValueChange = onAltitudeChange,
            label = { Text("altitude (m)") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        TextField(
            value = accuracy,
            onValueChange = onAccuracyChange,
            label = { Text("accuracy (m)") },
            placeholder = { Text("between 0 and 10000") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = !isAccuracyValid()
        )
        if (errorMessageLocation.isNotEmpty()) {
            Text(errorMessageLocation, color = MaterialTheme.colorScheme.error)
        }
    }

    // Composable for location sender button
    @Composable
    fun LocationSenderButton(isRunning: Boolean, useCustomLocation: Boolean, onClick: () -> Unit) {
        Button(
            onClick = onClick,
            enabled = (!useCustomLocation || isLocationValid()) && (!useCustomPower || isPowerValid())
        ) {
            Text(if (isRunning) "Stop Location Sender" else "Start Location Sender")
        }
    }

    // Composable for location receiver button
    @Composable
    fun LocationReceiverButton(isRunning: Boolean, onClick: () -> Unit) {
        Button(onClick = onClick) {
            Text(if (isRunning) "Stop Location Receiver" else "Start Location Receiver")
        }
    }



    private fun isTimeFreqValid(tf: String) = tf.toLongOrNull()?.let { it > 0 } ?: false
    private fun isPowerValid(): Boolean {
        return isTimeFreqValid(timeFreq)
    }

    // Validation functions for latitude, longitude, and accuracy
    private fun isLatitudeValid(lat: String) = lat.toDoubleOrNull()?.let { it in -90.0..90.0 } ?: false
    private fun isLongitudeValid(lon: String) = lon.toDoubleOrNull()?.let { it in -180.0..180.0 } ?: false
    private fun isAccuracyValid(acc: String) = acc.toDoubleOrNull()?.let { it >= 0 } ?: false
    private fun isLocationValid(): Boolean {
        return isLatitudeValid(latitude) && isLongitudeValid(longitude) && isAccuracyValid(accuracy)
    }
}