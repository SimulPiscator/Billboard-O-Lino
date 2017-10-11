# Billboard-O-Lino
This app allows utilization of a Tolino ebook reader's sleep screen to display announcements or data from a web source.
Using the sleep screen means that the reader will still be available to save its main purpose while going back to display 
useful information automatically when putting itself to sleep.

## Use cases include:
* Your OpenWRT-based router has a cron job running that changes the guest wifi passphrase in regular intervals.
After changing the passphrase, the script calls qrencode to produce a QR code with access information in SVG format,
places it into a location from where your Tolino can pick it up. Guests will be able to get wifi access by pointing 
their smart phone camera to the Tolino sleep screen.
* Your home is equipped with a number of environmental sensors attached to a headless Raspberry Pi.
Whenever you are not using your Tolino, it may serve as a temperature/humidity control display for your home.

## Basic operation
1. The app downloads an image file from a web location (svg, png, jpeg formats supported).
2. Sleep screen templates are obtained from locations in a search path (png, jpeg supported).
3. The downloaded image is rendered on top of each template (transparency supported).
4. The result is saved where the Tolino is looking for its sleep images.

## System modification
By default, the Tolino only allows for customizing the main sleep image by placing a file "suspend.jpg" on the sdcard.
When connected to a charger, different images will be displayed, which cannot be overridden. When connected to a PC,
the sdcard will not be accessible to Android apps, posing another problem for updating sleep screens.

Thus, it is necessary to perform a minor modification to the Tolino's system partition in order to allow the app to work
properly. The modification is harmless an may be reverted any time, but requires root access.
Proceed as follows:
1. Make sure there is an su binary installed at /system/bin/su or /system/xbin/su, plus a compatible superuser app to grant
root access to Android apps.
2. You may want to temporarily enable both Window Animations and Transitions under Developer settings, to make sure the e-ink
display is updated often enough for dialog windows to appear properly during the next steps.
3. Unplug the Tolino if connected to a computer's USB port. The modification cannot be performed while connected to a PC.
4. Start the Billboard-O-Lino app, and tap to enable "System modification". The superuser app should display a dialog window,
asking you to confirm root privileges for the Billboard-O-Lino app.
5. If you answered "yes" in the previous step, the "System modification" preference should now be checked.
If not, a pop-up notice ("Toast") should appear for a few seconds.
6. You may undo the modification by unchecking "System modification" at a later time. Don't forget to unplug the USB cable from
a PC if connected.

## Customization
When "System modification" is checked in the app's preferences, all related files are located inside /data/sleep as follows:
* System sleep screens are copied to /data/sleep/templates/system to be used as templates.
* If a custom sleep screen exists at /sdcard/suspend.jpg, it will be moved to /data/sleep/templates/user.
* Resulting images are put into /data/sleep/images, and are overwritten each time a synchronization is performed.
* To override a system template, put a file with the same name into /data/sleep/templates/user.
Without "System modification" being checked, the app will use the system's suspend.jpg as a template, and put the resulting file to /sdcard/suspend.jpg.

## Limitations
* Root access required. Guides how to root a Tolino via fastboot are available on the web.
