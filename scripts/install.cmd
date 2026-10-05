@echo off
set "WFT_INSTALLER=%TEMP%\WearForToyota-install.ps1"
powershell.exe -NoProfile -Command "$ErrorActionPreference = 'Stop'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://raw.githubusercontent.com/Poiki/wear-for-toyota/main/scripts/install.ps1' -OutFile $env:WFT_INSTALLER -UseBasicParsing"
if errorlevel 1 goto done
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%WFT_INSTALLER%" %*
:done
pause
