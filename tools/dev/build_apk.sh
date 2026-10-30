#!/bin/bash
# Offline APK build: kotlinc (gradle) -> aapt2 (from Apktool) -> d8 (bundletool) -> apksig.
# Usage: tools/dev/build_apk.sh <versionCode> <versionName>   (run tools/dev/setup.sh first)
set -e
D=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$D/../.." && pwd)
C=$D/.cache
R=$ROOT/app/src/main
O=$D/out
VC=${1:-1}; VN=${2:-1.0}
rm -rf $O; mkdir -p $O/dex
(cd $D/compile && gradle compileKotlin -q --console=plain)
sed 's#<manifest xmlns:android="http://schemas.android.com/apk/res/android">#<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.khongvinh.carsurvival">#' $R/AndroidManifest.xml > $O/Manifest.xml
$C/aapt2 compile --dir $R/res -o $O/res.zip
$C/aapt2 link -o $O/base.apk -I $C/android.jar $( [ -d $R/assets ] && echo "-A $R/assets" ) --manifest $O/Manifest.xml --min-sdk-version 24 --target-sdk-version 34 --version-code $VC --version-name $VN $O/res.zip
STD=$(find ~/.gradle/caches -name "kotlin-stdlib-2.0.21.jar" | head -1)
java -cp $C/bundletool.jar com.android.tools.r8.D8 --release --min-api 24 --lib $C/android.jar --output $O/dex $(find $D/compile/build/classes/kotlin/main -name "*.class") $STD 2>&1 | grep -vE "JAVA_TOOL|^Info" || true
java -cp $C/bundletool.jar:$C Sign $O/base.apk $O/dex/classes.dex $ROOT/app/debug.keystore $O/DuaXeSinhTon.apk 2>&1 | grep -v JAVA_TOOL
echo "APK: $O/DuaXeSinhTon.apk"
