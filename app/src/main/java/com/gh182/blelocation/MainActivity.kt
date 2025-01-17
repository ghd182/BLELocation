package com.gh182.blelocation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.gh182.blelocation.ui.theme.BLELocationTheme
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

const val CHANNEL_ID = "ble_location_channel"
const val LOCATION_NOTIFICATION_ID = 1
const val PREF_NAME = "BLELocationPrefs"
const val PREF_KEY_SENDING_STATE = "sending_state"
const val PREF_KEY_RECEIVING_STATE = "receiving_state"

class MainActivity : ComponentActivity() {
    private val tag = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
        editor.apply()
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allPermissionsGranted = permissions.all { it.value == true }
        if (allPermissionsGranted) {
            startApp()
        } else {
            showPermissionDeniedMessage()
        }
    }

    private fun showPermissionDeniedMessage() {
        Toast.makeText(this, "Allow all permissions in app settings", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun hasPermissions(): Boolean {
        val requiredPermissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        return requiredPermissions.all {
            ActivityCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        requestPermissionsLauncher.launch(permissions)
    }

    private fun startApp() {
        createNotificationChannel()

        setContent {
            BLELocationTheme {
                MainScreen(
                    onStartReceiving = { scanMode -> startReceiving(this, scanMode) },
                    onStopReceiving = ::stopReceiving,
                    onStartSending = { location, customLocationEnabled, frequency, bleOptionMode, bleOptionPower ->
                        startSending(this, location, customLocationEnabled, frequency, bleOptionMode, bleOptionPower)
                    },
                    onStopSending = ::stopSending
                )
            }
        }
    }

    private fun startReceiving(context: Context, scanMode: Int) {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(context, "Device does not support Bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            Toast.makeText(context, "Bluetooth is not enabled", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(context, BLEReceivingService::class.java)
        intent.putExtra("bleOptionMode", scanMode)
        startService(intent)

        Log.d(tag, "startReceiving")
        saveButtonState(true, PREF_KEY_RECEIVING_STATE)
        showLocationNotification()
    }

    private fun stopReceiving() {
        saveButtonState(false, PREF_KEY_RECEIVING_STATE)
        Log.d(tag, "stopReceiving")
        stopService(Intent(this, BLEReceivingService::class.java))
        clearLocationNotification()
    }

    private fun startSending(context: Context, location: Location, customLocationEnabled: Boolean, frequency: Int, bleOptionMode: Int, bleOptionPower: Int) {
        Log.d(tag, "startSending")
        saveButtonState(true, PREF_KEY_SENDING_STATE)
        Log.d(tag, "startSending with freq: $frequency, custom location: $customLocationEnabled")
        Log.d(tag, "Sending with bleOptionMode: $bleOptionMode, bleOptionPower: $bleOptionPower")

        val intent = Intent(context, BLESendingService::class.java).apply {
            putExtra("customLocationEnabled", customLocationEnabled)
            putExtra("frequency", frequency)
            putExtra("bleOptionMode", bleOptionMode)
            putExtra("bleOptionPower", bleOptionPower)
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
        saveButtonState(false, PREF_KEY_SENDING_STATE)
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
            .setSmallIcon(R.drawable.ic_launcher_new_foreground)
            .setContentTitle("Location Update")
            .setContentText("Waiting for location...")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
    ) {
        fun toLocation(): Location {
            val location = Location("")
            try {
                location.latitude = this.latitude.toDouble()
                location.longitude = this.longitude.toDouble()
                location.altitude = this.altitude.toDouble()
                location.accuracy = this.accuracy.toFloat()
            } catch (e: NumberFormatException) {
                Log.e("LocationDetails", "NumberFormatException $e")
                return Location("")
            }
            return location
        }
    }

    @Composable
    fun MainScreen(
        onStartReceiving: (Int) -> Unit,
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
        var bleOptionMode by remember { mutableIntStateOf(1) }
        var bleOptionPower by remember { mutableIntStateOf(1) }

        val isFrequencyValid = frequency.text.toIntOrNull()?.let { it in 100..3600000 } == true
        val isLatitudeValid = isLatitudeValid(locationDetails.latitude)
        val isLongitudeValid = isLongitudeValid(locationDetails.longitude)
        val isAltitudeValid = isAltitudeValid(locationDetails.altitude)
        val isAccuracyValid = isAccuracyValid(locationDetails.accuracy)
        val isLocationValid = isLatitudeValid && isLongitudeValid && isAltitudeValid && isAccuracyValid
        val isStartEnabled = isFrequencyValid && ((customLocationEnabled && isLocationValid) || !customLocationEnabled)
        var isTabDisabled by remember { mutableStateOf(false) }

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
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                    ) {
                        Text("BLE Location", style = MaterialTheme.typography.titleLarge, modifier = Modifier.align(Alignment.CenterHorizontally), color = MaterialTheme.colorScheme.primary)
                        ModeTabs(mode, onModeChange = {
                            if (!isTabDisabled) {
                                mode = it
                            }
                        }, isTabDisabled = isTabDisabled)
                        FrequencyInput(frequency, { frequency = it }, isRunning)
                    }

                    if (mode == "Send") {
                        Button(
                            onClick = {
                                isRunning = !isRunning
                                isTabDisabled = isRunning
                                if (isRunning) {
                                    onStartSending(locationDetails.toLocation(), customLocationEnabled, frequency.text.toInt(), bleOptionMode, bleOptionPower)
                                } else {
                                    onStopSending()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp, 0.dp),
                            enabled = isStartEnabled
                        ) {
                            Text(if (isRunning) "Stop Sending" else "Start Sending", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    if (mode == "Receive") {
                        Button(
                            onClick = {
                                if (isRunning) {
                                    onStopReceiving()
                                } else {
                                    onStartReceiving(1)
                                }
                                isRunning = !isRunning
                                isTabDisabled = isRunning
                            },
                            modifier = Modifier.fillMaxWidth().padding(16.dp, 0.dp),
                            enabled = isStartEnabled
                        ) {
                            Text(if (isRunning) "Stop Receiving" else "Start Receiving", style = MaterialTheme.typography.titleMedium)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            HorizontalDivider(modifier = Modifier.height(16.dp))
                            CustomPowerToggle(customPowerEnabled, onToggle = { customPowerEnabled = it }, isRunning = isRunning)

                            if (customPowerEnabled) {
                                PowerModeSelection(
                                    bleOptionMode = bleOptionMode,
                                    bleOptionPower = bleOptionPower,
                                    onAdvertiseModeChange = { bleOptionMode = it },
                                    onBleOptionPowerChange = { bleOptionPower = it },
                                    isRunning = isRunning
                                )
                            }
                        }
                        if (mode == "Send") {
                            item {
                                HorizontalDivider(modifier = Modifier.height(16.dp))
                                CustomLocationToggle(customLocationEnabled, { customLocationEnabled = it }, isRunning = isRunning)
                            }

                            if (customLocationEnabled) {
                                item {
                                    CustomLocationInput(locationDetails, { locationDetails = it }, isRunning = isRunning)
                                }
                            }
                        }
                        if (mode == "Receive") {
                            item {
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
                    text = { Text(tab, style = MaterialTheme.typography.titleMedium) },
                    selected = selectedMode == tab,
                    onClick = { if (!isTabDisabled) onModeChange(tab) },
                    enabled = !isTabDisabled
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
                enabled = !isRunning
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
        bleOptionMode: Int,
        bleOptionPower: Int,
        onAdvertiseModeChange: (Int) -> Unit,
        onBleOptionPowerChange: (Int) -> Unit,
        isRunning: Boolean
    ) {
        val advertiseModeOptions = listOf(
            0 to "Low Power",
            1 to "Balanced",
            2 to "Low Latency"
        )

        val bleOptionPowerOptions = listOf(
            0 to "Ultra Low",
            1 to "Low",
            2 to "Medium",
            3 to "High"
        )
        Column {
            Text("Bluetooth Mode", style = MaterialTheme.typography.titleMedium)
            advertiseModeOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = bleOptionMode == option.first,
                        onClick = { onAdvertiseModeChange(option.first) },
                        enabled = !isRunning
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.second)
                }
            }
            HorizontalDivider(modifier = Modifier.height(16.dp))

            Text("Bluetooth Power", style = MaterialTheme.typography.titleMedium)
            bleOptionPowerOptions.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = bleOptionPower == option.first,
                        onClick = { onBleOptionPowerChange(option.first) },
                        enabled = !isRunning
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(option.second)
                }
            }
        }
    }

    @Composable
    fun CustomLocationToggle(enabled: Boolean, onToggle: (Boolean) -> Unit, isRunning: Boolean) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Use Custom Location", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { if (!isRunning) onToggle(it) },
                enabled = !isRunning
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
                isRunning = isRunning
            )
            CustomLocationField(
                label = "Longitude",
                value = locationDetails.longitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(longitude = newValue))
                },
                isValid = isLongitudeValid(locationDetails.longitude),
                errorMessage = "Longitude must be between -180 and 180",
                isRunning = isRunning
            )
            CustomLocationField(
                label = "Altitude (meters)",
                value = locationDetails.altitude.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(altitude = newValue))
                },
                isValid = isAltitudeValid(locationDetails.altitude),
                errorMessage = "Altitude must be a valid number",
                isRunning = isRunning
            )
            CustomLocationField(
                label = "Accuracy (meters)",
                value = locationDetails.accuracy.toString(),
                onValueChange = { newValue ->
                    onLocationChange(locationDetails.copy(accuracy = newValue))
                },
                isValid = isAccuracyValid(locationDetails.accuracy),
                errorMessage = "Accuracy must be a positive number",
                isRunning = isRunning
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
            enabled = !isRunning
        )
        if (!isValid) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
        }
    }

    fun isLatitudeValid(latitude: String): Boolean {
        return try {
            latitude.toDouble() in -90.0..90.0
        } catch (e: NumberFormatException) {
            Log.d("isLatitudeValid", "NumberFormatException $e")
            false
        }
    }

    fun isLongitudeValid(longitude: String): Boolean {
        return try {
            longitude.toDouble() in -180.0..180.0
        } catch (e: NumberFormatException) {
            Log.d("isLongitudeValid", "NumberFormatException $e")
            false
        }
    }

    fun isAltitudeValid(altitude: String): Boolean {
        return try {
            altitude.toDouble() >= 0.0
        } catch (e: NumberFormatException) {
            Log.d("isAltitudeValid", "NumberFormatException $e")
            false
        }
    }

    fun isAccuracyValid(accuracy: String): Boolean {
        return try {
            accuracy.toFloat() > 0.0f
        } catch (e: NumberFormatException) {
            Log.d("isAccuracyValid", "NumberFormatException $e")
            false
        }
    }
}