package com.gh182.blelocation

import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import android.app.PendingIntent
import android.content.Intent
import androidx.compose.ui.Alignment
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.gh182.blelocation.ui.theme.BLELocationTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import android.location.Location
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions

const val CHANNEL_ID = "ble_location_channel"
const val LOCATION_NOTIFICATION_ID = 1


const val PREF_NAME = "BLELocationPrefs"
const val PREF_KEY_SENDING_STATE = "sending_state"
const val PREF_KEY_RECEIVING_STATE = "receiving_state"




class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if the necessary permissions are granted
        if (!hasPermissions()) {
            requestPermissions()
        } else {
            startApp()
        }
    }

    private fun saveButtonState(value: Boolean, prefKey: String) {
        val sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean(prefKey, value)
        editor.apply()  // Asynchronous commit
    }



    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.all { it.value == true }
        if (allPermissionsGranted) {
            startApp() // Proceed if all permissions are granted
        } else {
            showPermissionDeniedMessage() // Handle denied permissions
        }
    }








    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "Allow all permissions in app settings", Toast.LENGTH_LONG).show()
        finish() // Close the app or you can navigate to another screen if needed
    }



    // Function to check if all required permissions are granted
    private fun hasPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Function to request permissions
    private fun requestPermissions() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            )
        }

        requestPermissionsLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.BLUETOOTH_PRIVILEGED
            )
        )
    }

    // Start the app's main functionality if permissions are granted
    private fun startApp() {
        createNotificationChannel()

        setContent {
            BLELocationTheme {
                MainScreen(
                    onStartReceiving = ::startReceiving,
                    onStopReceiving = ::stopReceiving,
                    onStartSending = { location, customLocationEnabled, frequency, advertiseMode, advertisePower ->
                        startSending(this, location, customLocationEnabled, frequency, advertiseMode, advertisePower)
                    },
                    onStopSending = ::stopSending
                )
            }
        }
    }

    private fun startReceiving() {
        Log.d(tag, "startReceiving")
        saveButtonState(true, PREF_KEY_RECEIVING_STATE)
        startService(Intent(this, BLEReceivingService::class.java))
        showLocationNotification()
    }

    private fun stopReceiving() {
        saveButtonState(false, PREF_KEY_RECEIVING_STATE)
        Log.d(tag, "stopReceiving")
        stopService(Intent(this, BLEReceivingService::class.java))
        clearLocationNotification()
    }

    private fun startSending(context: Context, location: Location, customLocationEnabled: Boolean, frequency: Int, advertiseMode: Int, advertisePower: Int) {
        Log.d(tag, "startSending")
        saveButtonState(true, PREF_KEY_SENDING_STATE)
        Log.d(tag, "startSending with freq: $frequency, custom location: $customLocationEnabled")
        Log.d(tag, "Sending with advertiseMode: $advertiseMode, advertisePower: $advertisePower")

        val intent = Intent(context, BLESendingService::class.java).apply {
            putExtra("customLocationEnabled", customLocationEnabled)
            putExtra("frequency",frequency)
            putExtra("advertiseMode", advertiseMode)
            putExtra("advertisePower", advertisePower)
            if (customLocationEnabled) {
                putExtra("latitude", location.latitude)
                putExtra("longitude", location.longitude)
                putExtra("altitude", location.altitude)
                putExtra("accuracy", location.accuracy)
            }
        }
        context.startService(intent)
        showLocationNotification()
    }

    private fun stopSending() {
        saveButtonState(false,PREF_KEY_SENDING_STATE)
        Log.d(tag, "stopSending")
        stopService(Intent(this, BLESendingService::class.java))
        clearLocationNotification()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "BLE Location Service Notifications", NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }


    private fun clearLocationNotification() {
        NotificationManagerCompat.from(this).cancel(LOCATION_NOTIFICATION_ID)
    }

    private fun showLocationNotification() {
        // Create an intent to open the app
        val intent = Intent(this, MainActivity::class.java).apply {
            // This will bring the existing instance of the activity to the foreground if it exists
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        // Create a PendingIntent to wrap the intent
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_new_foreground)
            .setContentTitle("Location Update")
            .setContentText("Waiting for location...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
//            .setContentIntent(pendingIntent)  // Set the intent that will fire when the user taps the notification
            .setAutoCancel(true)  // Remove notification when tapped
            .build()

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.d(tag, "Notification permission not granted")
            return
        }
        NotificationManagerCompat.from(this).notify(LOCATION_NOTIFICATION_ID, notification)
    }


    data class LocationDetails(
        val latitude: String = "0",
        val longitude: String = "0",
        val altitude: String = "0",
        val accuracy: String = "1"
    ){
        fun toLocation(): Location {
            val location = Location("")
            try{
                location.latitude = this@LocationDetails.latitude.toDouble()
                location.longitude = this@LocationDetails.longitude.toDouble()
                location.altitude = this@LocationDetails.altitude.toDouble()
                location.accuracy = this@LocationDetails.accuracy.toFloat()
            }catch (e: NumberFormatException){
                Log.e("LocationDetails", "NumberFormatException $e")
                return Location("")
            }
            return location
        }
    }



    @Composable
    fun MainScreen(
        onStartReceiving: () -> Unit,
        onStopReceiving: () -> Unit,
        onStartSending: (Location, Boolean, Int, Int, Int) -> Unit,
        onStopSending: () -> Unit
    ) {
        var mode by remember { mutableStateOf("Send") }
        var isRunning by remember { mutableStateOf(false) }
        var frequency by remember { mutableStateOf(TextFieldValue("5000")) }

        var customLocationEnabled by remember { mutableStateOf(false) }
        var locationDetails by remember { mutableStateOf(LocationDetails()) }

        var customPowerEnabled by remember { mutableStateOf(false) }
        var advertiseMode by remember { mutableIntStateOf(1) } // Default: Balanced
        var advertisePower by remember { mutableIntStateOf(1) } // Default: Low

        // Frequency validation
        val isFrequencyValid = frequency.text.toIntOrNull()?.let { it in 100..3600000 } == true

        // Location validation
        val isLatitudeValid = isLatitudeValid(locationDetails.latitude)
        val isLongitudeValid = isLongitudeValid(locationDetails.longitude)
        val isAltitudeValid = isAltitudeValid(locationDetails.altitude)
        val isAccuracyValid = isAccuracyValid(locationDetails.accuracy)

        val isLocationValid = isLatitudeValid && isLongitudeValid && isAltitudeValid && isAccuracyValid


        val isStartEnabled = isFrequencyValid && ((customLocationEnabled && isLocationValid) || !customLocationEnabled)

        // Track tab selection ability
        var isTabDisabled by remember { mutableStateOf(false) }


        // Watch for changes in validation, and stop sending if invalid
        LaunchedEffect(isFrequencyValid, isLocationValid) {
            if (!isFrequencyValid || !isLocationValid) {
                isRunning = false
            }
        }

        Scaffold(
            content = { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Header remains fixed
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text("BLE Location", style = MaterialTheme.typography.titleLarge)
                        ModeTabs(mode, onModeChange = {
                            if (!isTabDisabled) { // Prevent tab change when operation is running
                                mode = it
                            }
                        }, isTabDisabled = isTabDisabled)
                        FrequencyInput(frequency, { frequency = it }, isRunning)
                    }

                    if(mode == "Send"){
                        Button(
                            onClick = {
                                isRunning = !isRunning
                                isTabDisabled = isRunning
                                if (isRunning) {
                                    onStartSending(locationDetails.toLocation(), customLocationEnabled, frequency.text.toInt(), advertiseMode, advertisePower)
                                } else {
                                    onStopSending()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            enabled = isStartEnabled // Disable if frequency or location is invalid
                        ) {
                            Text(if (isRunning) "Stop Sending" else "Start Sending")
                        }
                    }

                    // Scrollable content starts here
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Scrollable content for "Send" mode
                            if (mode == "Send") {

                                // Location settings
                                item {
                                    CustomLocationToggle(customLocationEnabled, { customLocationEnabled = it },  isRunning = isRunning)
                                }

                                if (customLocationEnabled) {
                                    item {
                                        CustomLocationInput(locationDetails, { locationDetails = it }, isRunning = isRunning)
                                    }
                                }

                                item {
                                    CustomPowerToggle(customPowerEnabled, onToggle = { customPowerEnabled = it },  isRunning = isRunning)
                                }

                                // Dropdowns for power and mode only if custom power settings are enabled
                                if (customPowerEnabled) {
                                    item {
                                        PowerModeSelection(
                                            advertiseMode = advertiseMode,
                                            advertisePower = advertisePower,
                                            onAdvertiseModeChange = { advertiseMode = it },
                                            onAdvertisePowerChange = { advertisePower = it },
                                            isRunning = isRunning // Pass isRunning to disable inputs
                                        )
                                    }
                                }
                            }

                            // Scrollable content for "Receive" mode
                            if (mode == "Receive") {
                                item {
                                    Button(
                                        onClick = {
                                            if (isRunning) {
                                                onStopReceiving()
                                            } else {
                                                onStartReceiving()
                                            }
                                            isRunning = !isRunning
                                            isTabDisabled = isRunning
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        enabled = isStartEnabled // Disable if frequency or location is invalid
                                    ) {
                                        Text(if (isRunning) "Stop Receiving" else "Start Receiving")
                                    }
                                }
                            }
                        }
                    }
            }
        )
    }


    @Composable
    fun ModeTabs(selectedMode: String, onModeChange: (String) -> Unit, isTabDisabled: Boolean) {
        val tabs = listOf("Send", "Receive")
        TabRow(selectedTabIndex = tabs.indexOf(selectedMode)) {
            tabs.forEach { tab ->
                Tab(
                    text = { Text(tab) },
                    selected = selectedMode == tab,
                    onClick = { if (!isTabDisabled) onModeChange(tab) },
                    enabled = !isTabDisabled // Disable the tab if operation is running
                )
            }
        }
    }

    @Composable
    fun FrequencyInput(frequency: TextFieldValue, onFrequencyChange: (TextFieldValue) -> Unit, isRunning: Boolean) {
        val isValid = frequency.text.toIntOrNull()?.let { it in 100..3600000 } == true
        Column {
            OutlinedTextField(
                value = frequency,
                onValueChange = onFrequencyChange,
                label = { Text("Frequency (ms)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                isError = !isValid,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isRunning // Disable the input if operation is running

            )
            if (!isValid) {
                Text("Frequency must be integer between 100 and 3600000 ms", color = MaterialTheme.colorScheme.error)
            }
        }
    }



    @Composable
    fun CustomPowerToggle(enabled: Boolean, onToggle: (Boolean) -> Unit, isRunning: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use Custom Power Settings", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { if (!isRunning) onToggle(it) },
                enabled = !isRunning
            )
        }
    }

    @Composable
    fun PowerModeSelection(
        advertiseMode: Int,
        advertisePower: Int,
        onAdvertiseModeChange: (Int) -> Unit,
        onAdvertisePowerChange: (Int) -> Unit,
        isRunning: Boolean
    ) {
        // List for Advertise Mode options
        val advertiseModeOptions = listOf(
            0 to "Low Power",  // 0 is the value for Low Power
            1 to "Balanced",    // 1 is the value for Balanced
            2 to "Low Latency"  // 2 is the value for Low Latency
        )

        // List for Advertise Power options
        val advertisePowerOptions = listOf(
            0 to "Ultra Low",  // 0 is the value for Ultra Low
            1 to "Low",        // 1 is the value for Low
            2 to "Medium",     // 2 is the value for Medium
            3 to "High"        // 3 is the value for High
        )
        Column {
            // Advertise Mode Radio Buttons
            Text("Advertise Mode", style = MaterialTheme.typography.titleMedium)
            advertiseModeOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = advertiseMode == option.first,
                        onClick = { onAdvertiseModeChange(option.first) },
                        enabled = !isRunning
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.second)
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Space between the two sections

            // Advertise Power Radio Buttons
            Text("Advertise Power", style = MaterialTheme.typography.titleMedium)
            advertisePowerOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = advertisePower == option.first,
                        onClick = { onAdvertisePowerChange(option.first) },
                        enabled = !isRunning)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.second)
                }
            }
        }
    }









    @Composable
    fun CustomLocationToggle(enabled: Boolean, onToggle: (Boolean) -> Unit,isRunning: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use Custom Location", style = MaterialTheme.typography.titleLarge)

            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { if (!isRunning) onToggle(it) }, // Disable toggle if isRunning is true
                enabled = !isRunning // Disable the toggle when isRunning is true
            )
        }
    }

    @Composable
    fun CustomLocationInput(locationDetails: LocationDetails, onLocationChange: (LocationDetails) -> Unit, isRunning: Boolean) {
        Column {
            CustomLocationField(
                label = "Latitude",
                value = locationDetails.latitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(latitude = newValue))
                },
                isValid = isLatitudeValid(locationDetails.latitude),
                errorMessage = "Latitude must be between -90 and 90",
                isRunning = isRunning // Pass isRunning to disable inputs
            )
            CustomLocationField(
                label = "Longitude",
                value = locationDetails.longitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(longitude = newValue))
                },
                isValid = isLongitudeValid(locationDetails.longitude),
                errorMessage = "Longitude must be between -180 and 180",
                isRunning = isRunning // Pass isRunning to disable inputs
            )
            CustomLocationField(
                label = "Altitude (meters)",
                value = locationDetails.altitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(altitude = newValue))
                },
                isValid = isAltitudeValid(locationDetails.altitude),
                errorMessage = "Altitude must be a valid number",
                isRunning = isRunning // Pass isRunning to disable inputs
            )
            CustomLocationField(
                label = "Accuracy (meters)",
                value = locationDetails.accuracy.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(accuracy = newValue))
                },
                isValid = isAccuracyValid(locationDetails.accuracy),
                errorMessage = "Accuracy must be a positive number",
                isRunning = isRunning // Pass isRunning to disable inputs
            )
        }
    }


    @Composable
    fun CustomLocationField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        isValid: Boolean,
        errorMessage: String,
        isRunning: Boolean
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = !isValid,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            enabled = !isRunning // Disable the input if operation is running
        )
        if (!isValid) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }

    fun isLatitudeValid(latitude: String): Boolean {
        try{return latitude.toDouble() in -90.0..90.0
        }catch ( e: NumberFormatException){
            Log.d("isLatitudeValid", "NumberFormatException $e")
            return false
        }
    }

    fun isLongitudeValid(longitude: String): Boolean {
        try{
            return longitude.toDouble() in -180.0..180.0
        }catch ( e: NumberFormatException){
            Log.d("isLongitudeValid", "NumberFormatException $e")
            return false
        }
    }

    fun isAltitudeValid(altitude: String): Boolean {
        // Altitude cannot be negative
        try{
            return altitude.toDouble() >= 0.0
        }catch ( e: NumberFormatException){
            Log.d("isAltitudeValid", "NumberFormatException $e")
            return false
        }
    }

    fun isAccuracyValid(accuracy: String): Boolean {
        try {
            return accuracy.toFloat() > 0.0f // Accuracy should be positive
        }catch ( e: NumberFormatException){
            Log.d("isAccuracyValid", "NumberFormatException $e")
            return false
        }
    }
}
