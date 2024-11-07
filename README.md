

[![Github All Releases](https://img.shields.io/github/downloads/Gh182/BLELocationShareApp/total.svg)]()

<video  src="https://github.com/user-attachments/assets/d1e4211e-c793-4c91-bc60-1249e8d4663e" height="100px"></video>

<img src="https://github.com/user-attachments/assets/e45042fb-fa0d-4ce3-8328-c4dabe5062e5" height="400px">
<img src="https://github.com/user-attachments/assets/153c34b0-6cf3-49d9-9804-d28f05b367e2" height="400px">

# Why?
Boox made the Tab Ultra C Pro without GNSS module, so here it is an app to send location from another device through BLE and use it as mock location.
## Functionalities
The app can be used as both sender and receiver. Remember to allow all the permissions to make it work properly (thy're not always appearing on opening or after installing... W.I.P.)

The app uses these permissions:
- Notifications
- Nearby devices (allow)
- Location (allow all the time)
### Sender
The app can broadcast the actual position (latitude, longitude, altitude and accuracy) of the sender device or an arbitrary one.
### Receiver
The app scans periodically BLE messages in search for a new location message and use it as Mock Location.

It is needed to set the app as Mock Location Provider in Android developer Settings or through ADB:

to set as Mocking Location:
```adb

adb shell appops set com.gh182.blelocation android:mock_location allow
```

to undo it:
```adb

adb shell appops set com.gh182.blelocation android:mock_location deny
```

to ckeck for Mocking Permission:

```adb

adb shell appops get com.gh182.blelocation android:mock_location
```

## Current bugs
You tell me :) 
