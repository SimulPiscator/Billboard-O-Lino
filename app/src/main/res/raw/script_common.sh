export APP=billboardolino
export PATH_SYSTEM=/system/usr/sleep/drawable-nodpi
export PATH_EPUB=/data/data/de.telekom.epub/files
export PATH_USERIMG=/mnt/sdcard/suspend.jpg
export PATH_IMAGES=/data/sleep/images
export PATH_TEMPLATES_USER=/data/sleep/templates/user
export PATH_TEMPLATES_SYSTEM=/data/sleep/templates/system
export SUFFIX=renamed_by_${APP}

# tolino will unmount/hide sdcard when attached to computer
if ! test -e /mnt/sdcard/DCIM; then
  echo "sdcard not mounted" >&2
  exit 1
fi
