package com.gh182.blelocation


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
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.input.KeyboardType
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.app.ActivityCompat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions

const val CHANNEL_ID = "ble_location_channel"
const val LOCATION_NOTIFICATION_ID = 1


const val PREF_NAME = "BLELocationPrefs"
const val PREF_KEY_ADVERTISING_STATE = "advertising_state"




class MainActivity : ComponentActivity() {
    companion object {
        const val PERMISSION_REQUEST_CODE = 100
    }

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

    private fun saveAdvertisingState(isAdvertising: Boolean) {
        val sharedPreferences = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean(PREF_KEY_ADVERTISING_STATE, isAdvertising)
        editor.apply()  // Asynchronous commit
    }








    // Function to check if all required permissions are granted
    private fun hasPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
    }

    // Function to request permissions
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.BLUETOOTH_CONNECT
            ),
            PERMISSION_REQUEST_CODE
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
        startService(Intent(this, BLEReceivingService::class.java))
        showLocationNotification()
    }

    private fun stopReceiving() {
        Log.d(tag, "stopReceiving")
        stopService(Intent(this, BLEReceivingService::class.java))
        clearLocationNotification()
    }

    private fun startSending(context: Context, location: Location, customLocationEnabled: Boolean, frequency: Int, advertiseMode: Int, advertisePower: Int) {
        Log.d(tag, "startSending")
        saveAdvertisingState(true)
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
        saveAdvertisingState(false)
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
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Location Update")
            .setContentText("Waiting for location...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
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
        var frequency by remember { mutableStateOf(TextFieldValue("1000")) }

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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("BLE Location Sender/Receiver", style = MaterialTheme.typography.titleLarge)

                    ModeTabs(mode, onModeChange = {
                        if (!isTabDisabled) { // Prevent tab change when operation is running
                            mode = it
                        }
                    }, isTabDisabled = isTabDisabled)

                    FrequencyInput(frequency) { frequency = it }

                    if (mode == "Send") {
                        CustomPowerToggle(customPowerEnabled, onToggle = { customPowerEnabled = it }, isRunning)

                        // Dropdowns for power and mode only if custom power settings are enabled
                        if (customPowerEnabled) {
                            PowerModeSelection(
                                advertiseMode = advertiseMode,
                                advertisePower = advertisePower,
                                onAdvertiseModeChange = { advertiseMode = it },
                                onAdvertisePowerChange = { advertisePower = it }
                            )
                        }

                        // Location settings
                        CustomLocationToggle(customLocationEnabled, { customLocationEnabled = it }, isRunning)
                        if (customLocationEnabled) {
                            CustomLocationInput(locationDetails) { locationDetails = it }
                        }

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
                            modifier = Modifier.fillMaxWidth(),
                            enabled = isStartEnabled // Disable if frequency or location is invalid
                        ) {
                            Text(if (isRunning) "Stop Sending" else "Start Sending")
                        }
                    }

                    // Actions for "Receive" mode
                    if (mode == "Receive") {
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
    fun FrequencyInput(frequency: TextFieldValue, onFrequencyChange: (TextFieldValue) -> Unit) {
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
                enabled = true
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
            Text("Use Custom Power Settings")
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
        onAdvertisePowerChange: (Int) -> Unit
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
            Text("Advertise Mode")
            advertiseModeOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = advertiseMode == option.first,
                        onClick = { onAdvertiseModeChange(option.first) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.second)
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Space between the two sections

            // Advertise Power Radio Buttons
            Text("Advertise Power")
            advertisePowerOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = advertisePower == option.first,
                        onClick = { onAdvertisePowerChange(option.first) }
                    )
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
            Text("Use Custom Location")
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { if (!isRunning) onToggle(it) }, // Disable toggle if isRunning is true
                enabled = !isRunning // Disable the toggle when isRunning is true
            )
        }
    }

    @Composable
    fun CustomLocationInput(locationDetails: LocationDetails, onLocationChange: (LocationDetails) -> Unit) {
        Column {
            CustomLocationField(
                label = "Latitude",
                value = locationDetails.latitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(latitude = newValue))
                },
                isValid = isLatitudeValid(locationDetails.latitude),
                errorMessage = "Latitude must be between -90 and 90"
            )
            CustomLocationField(
                label = "Longitude",
                value = locationDetails.longitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(longitude = newValue))
                },
                isValid = isLongitudeValid(locationDetails.longitude),
                errorMessage = "Longitude must be between -180 and 180"
            )
            CustomLocationField(
                label = "Altitude (meters)",
                value = locationDetails.altitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(altitude = newValue))
                },
                isValid = isAltitudeValid(locationDetails.altitude),
                errorMessage = "Altitude must be a valid number"
            )
            CustomLocationField(
                label = "Accuracy (meters)",
                value = locationDetails.accuracy.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(accuracy = newValue))
                },
                isValid = isAccuracyValid(locationDetails.accuracy),
                errorMessage = "Accuracy must be a positive number"
            )
        }
    }


    @Composable
    fun CustomLocationField(
        label: String,
        value: String,
        onValueChange: (String) -> Unit,
        isValid: Boolean,
        errorMessage: String
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            isError = !isValid,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
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
