@echo off
set "WFT_INSTALLER=%TEMP%\WearForToyota-install.ps1"
powershell.exe -NoProfile -Command "$ErrorActionPreference = 'Stop'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $file = Invoke-RestMethod 'https://api.github.com/repos/Poiki/wear-for-toyota/contents/scripts/install.ps1'; [IO.File]::WriteAllBytes($env:WFT_INSTALLER, [Convert]::FromBase64String($file.content))"
set "WFT_EXIT=%ERRORLEVEL%"
if not "%WFT_EXIT%"=="0" goto done
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%WFT_INSTALLER%" %*
set "WFT_EXIT=%ERRORLEVEL%"
:done
pause
exit /b %WFT_EXIT%
