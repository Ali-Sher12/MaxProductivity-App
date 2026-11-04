:<<"::WINDOWS_ONLY"
@echo off
title LongTerm App Compiler
echo ===================================================
echo   Building LongTerm Android App (Debug)
echo ===================================================
echo.
echo Running Gradle Wrapper assembleDebug...
echo.

call gradlew.bat assembleDebug

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ===================================================
    echo   SUCCESS! Build completed successfully.
    echo ===================================================
    echo.
    echo The APK file has been created at:
    echo   app\build\outputs\apk\debug\app-debug.apk
    echo.
) else (
    echo.
    echo ===================================================
    echo   ERROR: Build failed! Check the log output above.
    echo ===================================================
    echo.
)

echo.
pause
exit /b %ERRORLEVEL%
::WINDOWS_ONLY

set -e

echo "==================================================="
echo "  Building LongTerm Android App (Debug)"
echo "==================================================="
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

# Ensure ANDROID_HOME is set if not already present
if [ -z "$ANDROID_HOME" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    fi
fi

# Ensure gradlew has execute permission
chmod +x "$SCRIPT_DIR/gradlew"

echo "Running Gradle Wrapper assembleDebug..."
echo ""

if ./gradlew assembleDebug; then
    echo ""
    echo "==================================================="
    echo "  SUCCESS! Build completed successfully."
    echo "==================================================="
    echo ""
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    if [ -f "$APK_PATH" ]; then
        APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
        echo "The APK file has been created at:"
        echo "  $APK_PATH ($APK_SIZE)"
    else
        echo "The APK file has been created at:"
        echo "  $APK_PATH"
    fi
    echo ""
    
    # Check if a device is connected
    if command -v adb >/dev/null 2>&1; then
        CONNECTED_DEVICES=$(adb devices 2>/dev/null | grep -v "List of devices" | grep "device$" || true)
        if [ -n "$CONNECTED_DEVICES" ]; then
            echo "Device detected via ADB:"
            echo "$CONNECTED_DEVICES"
            echo ""
            echo "Tip: Run './gradlew installDebug' to install directly to your device."
        fi
    fi
else
    echo ""
    echo "==================================================="
    echo "  ERROR: Build failed! Check the log output above."
    echo "==================================================="
    echo ""
    exit 1
fi
