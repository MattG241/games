#!/bin/sh
# Build the Y2K Circuit icon pack APK.
# Needs: aapt, zipalign, apksigner, keytool, and android.jar (framework stub,
# e.g. from https://repo1.maven.org/maven2/com/google/android/android/4.1.1.4/android-4.1.1.4.jar)
set -e
ANDROID_JAR="${ANDROID_JAR:-android.jar}"
KEYSTORE="${KEYSTORE:-release.keystore}"

if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias iconpack -keyalg RSA \
        -keysize 2048 -validity 10000 -storepass y2kcircuit -keypass y2kcircuit \
        -dname "CN=Y2K Circuit Icons"
fi

aapt package -f -M AndroidManifest.xml -S res -A assets -I "$ANDROID_JAR" -F unsigned.apk
zipalign -f 4 unsigned.apk aligned.apk
apksigner sign --ks "$KEYSTORE" --ks-pass pass:y2kcircuit \
    --out Y2KCircuit-IconPack.apk aligned.apk
rm -f unsigned.apk aligned.apk
echo "Built Y2KCircuit-IconPack.apk"
