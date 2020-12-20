# Billboard-O-Lino
This app allows utilization of a Tolino ebook reader's sleep screen to display announcements or data from a web source.
Using the sleep screen means that the reader will still be available to serve its main purpose, and will go back to display 
useful information automatically when putting itself to sleep.

## Use cases include:
* Your OpenWRT-based router has a cron job running that changes the guest wifi passphrase in regular intervals.
After changing the passphrase, the script calls qrencode to produce a QR code with access information in SVG format,
places it into a location from where your Tolino can pick it up. Guests will be able to get wifi access by pointing 
their smart phone camera to the Tolino sleep screen.
* Your home is equipped with a number of environmental sensors attached to a headless Raspberry Pi.
Whenever you are not using your Tolino, it may serve as a temperature/humidity control display for your home.
* Display a web-based clock, or news feed while your Tolino is not in use.

## Basic operation
1. In regular intervals, the app renders a web location to an image.
2. When the device goes to sleep, the rendered image is drawn to the device's screen.

## Replace system sleep images
The app is notified of changes to the device's state only after the system has drawn its own sleep images to the screen.
For a smooth transition, it is possible to replace the Tolino's sleep images with an image that has no content, a
"transition image."
Paths to sleep images are hardcoded into the Tolino system, thus it is necessary to perform a minor modification to the 
system partition in order to have it display a different image when going to sleep.
The modification is harmless and may be reverted any time, but requires root access while being applied, or reverted.
Proceed as follows:
1. Make sure there is an su binary installed at `/system/bin/su` or `/system/xbin/su`, plus a compatible superuser app to grant
root access to Android apps.
2. You may want to temporarily enable both Window Animations and Transitions under Developer settings, to make sure the e-ink
display is updated often enough for dialog windows to appear properly during the next steps.
4. Start the Billboard-O-Lino app, and tap to enable "override system sleep images." The superuser app should display a dialog window,
asking you to confirm root privileges for the Billboard-O-Lino app.
5. If you answered "yes" in the previous step, the "override system sleep images" preference should now be checked.
If not, a pop-up notice ("Toast") should appear for a few seconds.
6. You may undo the modification by unchecking "override system sleep images" at a later time.

## Customization
When "override system sleep images" is checked in the app's preferences, `/data/sleep/images/` will contain the following files:
* `transition.jpg`
* `suspend.jpg`
* `suspend_charging.jpg`
* `suspend_batteryfull.jpg`

By default, `suspend*.jpg` files are symlinks to `transition.jpg,` which is updated by
the app whenever a different transition image is selected.
By replacing symlinks with actual files, or pointing them elsewhere, you may use custom transition images.
Make sure to leave `transition.jpg` alone, as it will be overwritten each time
a different transition image is selected in the preferences screen.

## Limitations
* Root access required. Guides how to root a Tolino via fastboot are available on the web.
