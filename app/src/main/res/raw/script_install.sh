su system
    mkdir -p $PATH_IMAGES
    busybox cp $PATH_SYSTEM/suspend.jpg $PATH_IMAGES/transition.jpg
    ln -s $PATH_IMAGES/transition.jpg $PATH_IMAGES/suspend.jpg
    ln -s $PATH_IMAGES/transition.jpg $PATH_IMAGES/suspend_charging.jpg
    ln -s $PATH_IMAGES/transition.jpg $PATH_IMAGES/suspend_batteryfull.jpg
    chmod 0666 $PATH_IMAGES/*
    chmod 0755 $PATH_IMAGES
exit

# rename original image directories and create symlinks instead
mount -o remount,rw /system
test -e $PATH_SYSTEM.$SUFFIX || mv $PATH_SYSTEM $PATH_SYSTEM.$SUFFIX
ln -s $PATH_IMAGES $PATH_SYSTEM
test -e $PATH_EPUB.$SUFFIX || mv $PATH_EPUB $PATH_EPUB.$SUFFIX
ln -s $PATH_IMAGES $PATH_EPUB
mount -o remount,ro /system
sync
exit 0
