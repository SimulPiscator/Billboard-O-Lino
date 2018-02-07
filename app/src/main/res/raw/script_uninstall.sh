# remove symlinks and restore original directory names
# rm will succeed on symlinks and fail on directories, so it's safe to use
mount -o remount,rw /system
if test -e $PATH_SYSTEM.$SUFFIX; then
  rm $PATH_SYSTEM && mv $PATH_SYSTEM.$SUFFIX $PATH_SYSTEM
fi
if test -e $PATH_EPUB.$SUFFIX; then
  rm $PATH_EPUB && mv $PATH_EPUB.$SUFFIX $PATH_EPUB
fi
mount -o remount,ro /system
sync
exit 0
