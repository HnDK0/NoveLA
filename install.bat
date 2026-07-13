@echo off
echo Installing NovelDokushaTT APK...

REM Add ADB to PATH
set "ADB_PATH=%USERPROFILE%\AppData\Local\Android\Sdk\platform-tools"
set "PATH=%ADB_PATH%;%PATH%"

REM Check if device is connected
echo Checking device connection...
adb devices

REM Install APK
echo.
echo Installing APK...
adb install -r -t "app\build\intermediates\apk\debug\WebnovelReader_v1.0.1-debug.apk"

if %errorlevel% equ 0 (
    echo.
    echo APK installed successfully!

    REM Try to launch app
    echo Launching app...
    adb shell monkey -p my.noveldokusha -c android.intent.category.LAUNCHER 1

    echo.
    echo Done! Check your emulator.
) else (
    echo.
    echo Failed to install APK.
    echo Make sure the APK exists and device is connected.
)

pause
