#!/system/bin/sh
# MYFix GSI manual installer (no Magisk required)
# Usage (adb shell su or recovery): sh /sdcard/MYFix/install_gsi.sh

set -e

SRC_DIR="$(dirname "$0")"
LIB_SRC="$SRC_DIR/lib64/libfodfix.so"
RC_SRC="$SRC_DIR/init/fodfix.rc"
SE_SRC="$SRC_DIR/selinux"

echo "[MYFix] Checking source files..."
if [ ! -f "$LIB_SRC" ]; then
  echo "[MYFix] ERROR: Missing $LIB_SRC"
  exit 1
fi

echo "[MYFix] Remounting system partition read-write..."
mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null || {
  echo "[MYFix] ERROR: Unable to remount /system as rw (EROFS or shared_blocks)."
  exit 2
}

mkdir -p /system/lib64 /system/etc/init /system/etc/selinux/myfix

cp -f "$LIB_SRC" /system/lib64/libfodfix.so
chmod 0644 /system/lib64/libfodfix.so
chcon u:object_r:system_lib_file:s0 /system/lib64/libfodfix.so 2>/dev/null || true

if [ -f "$RC_SRC" ]; then
  cp -f "$RC_SRC" /system/etc/init/fodfix.rc
  chmod 0644 /system/etc/init/fodfix.rc
  chcon u:object_r:system_file:s0 /system/etc/init/fodfix.rc 2>/dev/null || true
fi

if [ -d "$SE_SRC" ]; then
  cp -f "$SE_SRC"/* /system/etc/selinux/myfix/
  chmod 0644 /system/etc/selinux/myfix/*
  chcon -R u:object_r:system_file:s0 /system/etc/selinux/myfix 2>/dev/null || true
fi

sync
echo "[MYFix] Installed successfully to /system/lib64/libfodfix.so"
