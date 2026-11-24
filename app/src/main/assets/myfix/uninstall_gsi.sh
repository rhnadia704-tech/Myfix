#!/system/bin/sh
# MYFix GSI clean rollback / uninstaller

set -e

mount -o rw,remount /system 2>/dev/null || mount -o rw,remount / 2>/dev/null || {
  echo "[MYFix] ERROR: Unable to remount /system as rw."
  exit 1
}

rm -f /system/lib64/libfodfix.so
rm -f /system/etc/init/fodfix.rc
rm -rf /system/etc/selinux/myfix
sync

echo "[MYFix] Uninstalled cleanly."
