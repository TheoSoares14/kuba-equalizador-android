@echo off
cd /d "%~dp0"
call npm install || goto erro
if not exist android call npx cap add android || goto erro
node patch-android.js || goto erro
call npx cap sync android || goto erro
echo.
echo PRONTO. Agora abra a pasta "android" no Android Studio.
pause
exit /b 0
:erro
echo ERRO. Mande o texto acima para o Claude.
pause
