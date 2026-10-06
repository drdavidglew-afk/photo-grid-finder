#!/bin/bash
set -x
OUT=diag-out; mkdir -p $OUT
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb shell am start -W -n com.photogridfinder.app/.MainActivity
sleep 10
adb exec-out screencap -p > $OUT/1-launch.png
adb shell uiautomator dump /sdcard/ui.xml; adb pull /sdcard/ui.xml $OUT/1-ui.xml
B=$(grep -o 'resource-id="com.photogridfinder.app:id/pick"[^>]*bounds="[^"]*"' $OUT/1-ui.xml | grep -o 'bounds="[^"]*"' | grep -o '[0-9]\+' | tr '\n' ' ')
set -- $B
if [ -n "$4" ]; then adb shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); fi
sleep 6
adb exec-out screencap -p > $OUT/2-after-tap.png
adb shell uiautomator dump /sdcard/ui2.xml; adb pull /sdcard/ui2.xml $OUT/2-ui.xml
adb shell dumpsys activity activities | grep -i "mResumedActivity\|topResumed" > $OUT/activities.txt
adb logcat -d > $OUT/logcat.txt
adb shell pidof com.photogridfinder.app > $OUT/pid.txt
echo done
