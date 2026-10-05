param([string]$Serial, [switch]$CheckOnly)
$ErrorActionPreference = 'Stop'
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $repo = 'Poiki/wear-for-toyota'
    $folder = Join-Path $env:LOCALAPPDATA 'WearForToyota'
    New-Item -ItemType Directory -Path $folder -Force | Out-Null
    Write-Host 'Downloading the latest official Wear for Toyota release...'
    $release = Invoke-RestMethod "https://api.github.com/repos/$repo/releases/latest"
    $asset = @($release.assets | Where-Object { $_.name -match '^wear-for-toyota-watch.*\.apk$' })
    if ($asset.Count -ne 1 -or $asset[0].digest -notmatch '^sha256:[a-f0-9]{64}$') { throw 'No unique watch APK with SHA-256 in this release.' }
    if ($asset[0].browser_download_url -notlike "https://github.com/$repo/releases/download/*") { throw 'Unexpected APK source.' }
    $apk = Join-Path $folder 'watch.apk'
    Invoke-WebRequest $asset[0].browser_download_url -OutFile $apk -UseBasicParsing
    if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant() -ne $asset[0].digest.Substring(7)) { throw 'APK checksum mismatch.' }
    if ($CheckOnly) { Write-Host "OK: $($release.tag_name), APK SHA-256 verified. No device changes."; exit 0 }
    $adb = Join-Path $folder 'platform-tools/adb.exe'
    if (!(Test-Path -LiteralPath $adb)) {
        Write-Host 'Installing Google Android Platform Tools locally (no administrator needed)...'
        $zip = Join-Path $folder 'platform-tools.zip'
        Invoke-WebRequest 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile $zip -UseBasicParsing
        Expand-Archive -LiteralPath $zip -DestinationPath $folder -Force
        Remove-Item -LiteralPath $zip
    }
    function Run-Adb {
        & $adb @args
        if ($LASTEXITCODE -ne 0) { throw 'ADB failed. Check the watch connection and authorization.' }
    }
    Run-Adb start-server
    if (!$Serial) {
        $devices = @((& $adb devices) | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
        if ($devices.Count -eq 0) {
            Write-Host 'Enable developer options and Wireless debugging on the watch. Use the same Wi-Fi network.'
            $pair = Read-Host 'Pairing address IP:port (watch: Pair using pairing code)'
            Run-Adb pair $pair
            $connect = Read-Host 'Connection address IP:port (different from the pairing port)'
            Run-Adb connect $connect
            $devices = @((& $adb devices) | Where-Object { $_ -match '^\S+\s+device$' } | ForEach-Object { ($_ -split '\s+')[0] })
        }
        $watches = @($devices | Where-Object { $_ -notlike 'emulator-*' -and ((& $adb -s $_ shell pm list features) -match '^feature:android.hardware.type.watch$') })
        if ($watches.Count -eq 0) { throw 'No authorized Wear OS watch found. For USB, accept the debugging prompt and install the manufacturer driver if needed.' }
        if ($watches.Count -eq 1) { $Serial = $watches[0] } else {
            for ($i = 0; $i -lt $watches.Count; $i++) { Write-Host "$($i + 1): $($watches[$i])" }
            $choice = Read-Host 'Watch number'
            if ($choice -notmatch '^\d+$' -or [int]$choice -lt 1 -or [int]$choice -gt $watches.Count) { throw 'Invalid watch number.' }
            $Serial = $watches[[int]$choice - 1]
        }
    }
    if ($Serial -like 'emulator-*' -or !((& $adb -s $Serial shell pm list features) -match '^feature:android.hardware.type.watch$')) { throw 'The selected device is not a physical Wear OS watch.' }
    $sdk = (& $adb -s $Serial shell getprop ro.build.version.sdk).Trim()
    if ($sdk -notmatch '^\d+$' -or [int]$sdk -lt 30) { throw 'Wear OS 3 / Android 11 or newer is required.' }
    Run-Adb -s $Serial install -r $apk
    Run-Adb -s $Serial shell am start -n 'com.poiki.toyotawear/.MainActivity'
    Write-Host "Installed $($release.tag_name). Sign in on your watch; existing app data is preserved."
} catch {
    Write-Host "Installation stopped: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host 'The installer never uninstalls the app. For signature conflicts, use the same signing key as your previous APK.'
    exit 1
}
