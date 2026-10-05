@echo off
set "WFT_INSTALLER=%TEMP%\WearForToyota-install.ps1"
powershell.exe -NoProfile -Command "$ErrorActionPreference = 'Stop'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest 'https://api.github.com/repos/Poiki/wear-for-toyota/contents/scripts/install.ps1' -Headers @{Accept='application/vnd.github.raw+json'} -OutFile $env:WFT_INSTALLER -UseBasicParsing"
set "WFT_EXIT=%ERRORLEVEL%"
if not "%WFT_EXIT%"=="0" goto done
powershell.exe -NoProfile -ExecutionPolicy RemoteSigned -File "%WFT_INSTALLER%" %*
set "WFT_EXIT=%ERRORLEVEL%"
:done
pause
exit /b %WFT_EXIT%
