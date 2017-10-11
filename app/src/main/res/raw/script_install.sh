su system
    mkdir -p $PATH_IMAGES
    mkdir -p $PATH_TEMPLATES_USER
    mkdir -p $PATH_TEMPLATES_SYSTEM
    busybox cp $PATH_SYSTEM/* $PATH_TEMPLATES_SYSTEM/
    busybox cp $PATH_EPUB/* $PATH_TEMPLATES_SYSTEM/
    busybox cp $PATH_TEMPLATES_SYSTEM/* $PATH_IMAGES/
    if test -e $PATH_USERIMG; then
      busybox cp $PATH_USERIMG $PATH_TEMPLATES_USER/
      mv $PATH_USERIMG $PATH_USERIMG.$SUFFIX
    fi

    chmod 0666 $PATH_IMAGES/*
    chmod 0644 $PATH_TEMPLATES_SYSTEM/*
    chmod 0755 $PATH_IMAGES
    chmod 0755 $PATH_TEMPLATES_SYSTEM
    chmod 0755 $PATH_TEMPLATES_USER
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
