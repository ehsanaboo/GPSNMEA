# GPS NMEA
GPS NMEA UART Serial reader to mockLocation for Android

This is a sample app to read data from GPS module with UART Serial interface and Standard NMEA Protocol. It selects essential parameters from the data stream and push it as mockLocation to LocationManager of Android. Therefore, Android would be able to detect the location and all the navigation app should work properly.

Initialization:
At the beginning of the code, tagged as "ToDo", just change the physical address of the serial port into what you have on your device.

To read the serial port the following package has been used:
https://code.google.com/archive/p/android-serialport-api/
