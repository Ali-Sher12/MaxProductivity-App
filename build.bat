@echo off
title Max Productivity App Compiler
echo ===================================================
echo   Building Max Productivity Android App (Debug)
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
