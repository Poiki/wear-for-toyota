#!/bin/bash
set -euo pipefail
trap 'status=$?; if [ "$status" -ne 0 ]; then echo "Installation stopped. Check debugging authorization and the APK signing key."; fi; [ -t 0 ] && read -r -p "Press Enter to close..." || true' EXIT
[ "$(uname -s)" = Darwin ] || { echo 'This installer is for macOS.'; exit 1; }
folder="$HOME/Library/Application Support/WearForToyota"
mkdir -p "$folder"
metadata="$folder/release.json"
echo 'Downloading the latest official Wear for Toyota release...'
curl --fail --location --silent --show-error 'https://api.github.com/repos/Poiki/wear-for-toyota/releases/latest' -o "$metadata"
asset=$(osascript -l JavaScript -e 'ObjC.import("Foundation"); function run(argv) {
    var r = JSON.parse($.NSString.stringWithContentsOfFileEncodingError(argv[0], $.NSUTF8StringEncoding, null).js);
    var assets = r.assets.filter(function(a) { return /^wear-for-toyota-watch.*\.apk$/.test(a.name); });
    if (assets.length !== 1 || !/^sha256:[a-f0-9]{64}$/.test(assets[0].digest || "")) throw Error("No unique watch APK with SHA-256");
    if (!/^https:\/\/github\.com\/Poiki\/wear-for-toyota\/releases\/download\//.test(assets[0].browser_download_url)) throw Error("Unexpected APK source");
    return assets[0].browser_download_url + "\n" + assets[0].digest.slice(7);
}' "$metadata")
url=$(printf '%s\n' "$asset" | head -n 1)
expected=$(printf '%s\n' "$asset" | tail -n 1)
apk="$folder/watch.apk"
curl --fail --location --silent --show-error "$url" -o "$apk"
actual=$(shasum -a 256 "$apk" | awk '{print $1}')
[ "$actual" = "$expected" ] || { echo 'APK checksum mismatch.'; exit 1; }
if [ "${1:-}" = --check ]; then echo 'OK: APK SHA-256 verified. No device changes.'; exit 0; fi
adb="$folder/platform-tools/adb"
if [ ! -x "$adb" ]; then
    echo 'Installing Google Android Platform Tools locally (no administrator needed)...'
    curl --fail --location --silent --show-error 'https://dl.google.com/android/repository/platform-tools-latest-darwin.zip' -o "$folder/platform-tools.zip"
    unzip -oq "$folder/platform-tools.zip" -d "$folder"
    rm -f "$folder/platform-tools.zip"
    chmod u+x "$adb"
fi
"$adb" start-server
devices=$("$adb" devices | awk '$2 == "device" {print $1}')
if [ -z "$devices" ]; then
    echo 'Enable developer options and Wireless debugging on the watch. Use the same Wi-Fi network.'
    read -r -p 'Pairing address IP:port (watch: Pair using pairing code): ' pair
    "$adb" pair "$pair"
    read -r -p 'Connection address IP:port (different from pairing port): ' connect
    "$adb" connect "$connect"
    devices=$("$adb" devices | awk '$2 == "device" {print $1}')
fi
watches=()
count=0
while IFS= read -r device; do
    [ -n "$device" ] || continue
    case "$device" in emulator-*) continue ;; esac
    if "$adb" -s "$device" shell pm list features | tr -d '\r' | grep -qx 'feature:android.hardware.type.watch'; then watches[count]="$device"; count=$((count+1)); fi
done <<< "$devices"
[ "$count" -gt 0 ] || { echo 'No authorized Wear OS watch found. Accept the debugging prompt on the watch.'; exit 1; }
if [ "$count" -eq 1 ]; then serial="${watches[0]}"; else
    for ((i=0; i<count; i++)); do echo "$((i+1)): ${watches[$i]}"; done
    read -r -p 'Watch number: ' choice
    [[ "$choice" =~ ^[0-9]+$ ]] && [ "$choice" -ge 1 ] && [ "$choice" -le "$count" ] || { echo 'Invalid watch number.'; exit 1; }
    serial="${watches[$((choice-1))]}"
fi
sdk=$("$adb" -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')
[[ "$sdk" =~ ^[0-9]+$ ]] && [ "$sdk" -ge 30 ] || { echo 'Wear OS 3 / Android 11 or newer is required.'; exit 1; }
"$adb" -s "$serial" install -r "$apk"
"$adb" -s "$serial" shell am start -n com.poiki.toyotawear/.MainActivity
echo 'Installed. Sign in on your watch; existing app data is preserved.'
