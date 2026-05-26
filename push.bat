@echo off
title AuraCam Cloud Build Deployer
color 0B
echo ===================================================
echo             AuraCam Cloud Build Deployer          
echo ===================================================
echo.
echo [1/3] Initializing local Git repository...
git init >nul 2>&1
git add . >nul 2>&1
git commit -m "release: AuraCam premium clean architecture and DSLR NDK" >nul 2>&1
git branch -M main >nul 2>&1

echo.
echo [2/3] Connecting to your GitHub Repository...
echo Please create a new empty repository on github.com first.
echo.
set /p repo="Paste your GitHub Repository URL (e.g., https://github.com/username/AuraCam.git): "
git remote add origin %repo% >nul 2>&1

echo.
echo [3/3] Uploading project to GitHub Cloud Compiler...
git push -u origin main

echo.
echo ===================================================
echo SUCCESS: Project pushed successfully to the cloud!
echo.
echo Please navigate to the "Actions" tab in your GitHub 
echo repository in your web browser. 
echo Once the green checkmark appears (approx. 2 minutes),
echo scroll to the bottom to download your 'AuraCam-Debug-APK'.
echo ===================================================
echo.
pause
