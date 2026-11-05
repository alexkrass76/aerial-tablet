#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
SDK_PLATFORM="${SDK_PLATFORM:-android-36}"
BUILD_TOOLS="${BUILD_TOOLS:-36.0.0}"
TOOLS_DIR="$ANDROID_HOME/build-tools/$BUILD_TOOLS"
ANDROID_JAR="$ANDROID_HOME/platforms/$SDK_PLATFORM/android.jar"
for tool in aapt2 d8 zipalign apksigner; do
    [[ -x "$TOOLS_DIR/$tool" ]] || { echo "Missing $TOOLS_DIR/$tool" >&2; exit 1; }
done
[[ -f "$ANDROID_JAR" ]] || { echo "Missing $ANDROID_JAR" >&2; exit 1; }
cd "$PROJECT_DIR"
mkdir -p build/resources build/classes build/dex build/generated signing
find build/resources -name '*.flat' -delete
"$TOOLS_DIR/aapt2" compile --dir res -o build/resources
"$TOOLS_DIR/aapt2" link -o build/resources.apk -I "$ANDROID_JAR" --manifest AndroidManifest.xml -A assets --java build/generated --auto-add-overlay build/resources/*.flat
find src build/generated -name '*.java' > build/sources.txt
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath "$ANDROID_JAR:$TOOLS_DIR/core-lambda-stubs.jar" -d build/classes @build/sources.txt
jar cf build/classes.jar -C build/classes .
"$TOOLS_DIR/d8" --release --min-api 29 --lib "$ANDROID_JAR" --output build/dex build/classes.jar
cp build/resources.apk build/unsigned.apk
(cd build/dex && zip -q -u ../unsigned.apk classes.dex)
"$TOOLS_DIR/zipalign" -f -p 4 build/unsigned.apk build/aligned.apk
if [[ ! -f signing/aerial-tablet.jks ]]; then
    keytool -genkeypair -keystore signing/aerial-tablet.jks -alias aerial-tablet -storepass android -keypass android -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Aerial Tablet Local Build" >/dev/null
    chmod 600 signing/aerial-tablet.jks
fi
"$TOOLS_DIR/apksigner" sign --ks signing/aerial-tablet.jks --ks-key-alias aerial-tablet --ks-pass pass:android --key-pass pass:android --out build/aerial-tablet.apk build/aligned.apk
"$TOOLS_DIR/apksigner" verify --verbose build/aerial-tablet.apk
echo "APK: $PROJECT_DIR/build/aerial-tablet.apk"
