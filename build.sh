#!/data/data/com.termux/files/usr/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
B="$HOME/projects/Ollamaster-build"
W="$B/proj"
O="$B/out"

rm -rf "$W" "$O"
mkdir -p "$W" "$O/gen" "$O/classes" "$O/dex"
cp -r "$ROOT/src" "$ROOT/res" "$ROOT/AndroidManifest.xml" "$W/"

cd "$W"

echo "[1/6] aapt2 compile resources..."
aapt2 compile --dir res -o "$O/res.zip"

echo "[2/6] aapt2 link (generate R.java)..."
aapt2 link -o "$O/base.apk" \
    -I "$B/android.jar" \
    --manifest AndroidManifest.xml \
    --min-sdk-version 26 --target-sdk-version 35 \
    --java "$O/gen" \
    "$O/res.zip"

echo "[3/6] javac..."
find "$W/src" "$O/gen" -name '*.java' > "$O/sources.txt"
javac -source 8 -target 8 -nowarn \
    -bootclasspath "$B/android.jar:$B/core-lambda-stubs.jar" \
    -d "$O/classes" \
    @"$O/sources.txt"

echo "[4/6] d8 dex..."
d8 --release --min-api 26 --lib "$B/android.jar" \
    --output "$O/dex" $(find "$O/classes" -name '*.class')

echo "[5/6] package & align..."
cp "$O/base.apk" "$O/unsigned.apk"
( cd "$O/dex" && zip -q "$O/unsigned.apk" classes.dex )
zipalign -f 4 "$O/unsigned.apk" "$O/aligned.apk"

echo "[6/6] sign..."
if [ ! -f "$B/keystore.jks" ]; then
    keytool -genkeypair -keystore "$W/../keystore.jks" -alias ollamaster \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -storepass ollamaster2024 -keypass ollamaster2024 \
        -dname "CN=Ollamaster, OU=Dev, O=Ollamaster, C=CN"
fi
apksigner sign --ks "$B/keystore.jks" \
    --ks-pass pass:ollamaster2024 --key-pass pass:ollamaster2024 \
    --out "$B/Ollamaster.apk" "$O/aligned.apk"

apksigner verify "$B/Ollamaster.apk" && echo "VERIFY OK"
cp "$B/Ollamaster.apk" "$ROOT/../Ollamaster.apk"
ls -la "$ROOT/Ollamaster.apk"
echo "BUILD DONE -> $ROOT/Ollamaster.apk"
