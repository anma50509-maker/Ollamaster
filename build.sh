#!/data/data/com.termux/files/usr/bin/bash
set -e

ROOT="$(cd "$(dirname "$0")" && pwd)"
B="$HOME/projects/Ollamaster-build"
W="$B/proj"
O="$B/out"
# 产物输出目录 = 项目根（source/ 的上一级）
OUT="$(dirname "$ROOT")"

# 版本水印：从 AndroidManifest 提取 versionName + 构建时间，生成带标记的副本
VERSION="$(sed -n 's/.*android:versionName="\([^"]*\)".*/\1/p' "$ROOT/AndroidManifest.xml" | head -1)"
[ -z "$VERSION" ] && VERSION="dev"
STAMP="$(date +%Y%m%d-%H%M)"
TAGGED="Ollamaster-${VERSION}-${STAMP}.apk"

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

# 输出：固定名（供脚本引用）+ 带版本水印副本（供人识别新旧）
# 先清理旧的带版本号副本，避免项目根堆积，但不动固定名 Ollamaster.apk
rm -f "$OUT"/Ollamaster-*.apk 2>/dev/null || true
cp "$B/Ollamaster.apk" "$OUT/Ollamaster.apk"
cp "$B/Ollamaster.apk" "$OUT/$TAGGED"
ls -la "$OUT/Ollamaster.apk" "$OUT/$TAGGED"
echo "BUILD DONE -> $OUT/Ollamaster.apk  (+$TAGGED)"
