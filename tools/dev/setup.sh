#!/bin/bash
# Downloads the tools needed to build the APK without the Android SDK
# (dl.google.com / maven.google.com are blocked in the cloud container).
set -e
D=$(cd "$(dirname "$0")" && pwd)
C=$D/.cache
mkdir -p $C
cd $C
[ -f android.jar ] || curl -sSL -o android.jar https://raw.githubusercontent.com/Sable/android-platforms/master/android-34/android.jar
[ -f bundletool.jar ] || curl -sSL -o bundletool.jar https://github.com/google/bundletool/releases/download/1.17.2/bundletool-all-1.17.2.jar
[ -f apktool.jar ] || curl -sSL -o apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar
[ -f aapt2 ] || { unzip -o -q -j apktool.jar prebuilt/linux/aapt2_64 -d . && mv aapt2_64 aapt2 && chmod +x aapt2; }
[ -f Sign.class ] || javac -cp bundletool.jar -d . $D/Sign.java
ls -la $C
