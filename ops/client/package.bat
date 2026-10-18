@echo off
setlocal EnableExtensions

rem Adjust these two paths if Qt or MinGW is installed elsewhere.
set "QT_ROOT=D:\QT\6.11.1\mingw_64"
set "MINGW_ROOT=D:\QT\Tools\mingw1310_64"
set "CLIENT_DIR=%~dp0."
set "BUILD_DIR=%CLIENT_DIR%\build-package"
set "STAGE_DIR=%CLIENT_DIR%\package-staging"
set "PACKAGE_DIR=%CLIENT_DIR%\package"
set "ZIP_FILE=%~dp0yiuk-ops-windows-x64.zip"
set "ZIP_STAGE=%~dp0yiuk-ops-windows-x64.staging.zip"

if not exist "%QT_ROOT%\bin\windeployqt.exe" (
    echo ERROR: windeployqt.exe not found under "%QT_ROOT%\bin".
    goto :failed
)
if not exist "%MINGW_ROOT%\bin\g++.exe" (
    echo ERROR: MinGW g++.exe not found under "%MINGW_ROOT%\bin".
    goto :failed
)
where cmake.exe >nul 2>nul
if errorlevel 1 (
    echo ERROR: CMake was not found in PATH.
    goto :failed
)

set "PATH=%QT_ROOT%\bin;%MINGW_ROOT%\bin;%PATH%"

if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%STAGE_DIR%" rmdir /s /q "%STAGE_DIR%"
if exist "%ZIP_STAGE%" del /q "%ZIP_STAGE%"

echo [1/4] Configuring Qt client...
cmake -S "%CLIENT_DIR%" -B "%BUILD_DIR%" -G "MinGW Makefiles" -DCMAKE_PREFIX_PATH="%QT_ROOT%" -DCMAKE_CXX_COMPILER="%MINGW_ROOT%\bin\g++.exe"
if errorlevel 1 goto :failed

echo [2/4] Building release executable...
cmake --build "%BUILD_DIR%" --config Release --parallel
if errorlevel 1 goto :failed
if not exist "%BUILD_DIR%\yiuk-ops.exe" (
    echo ERROR: Build completed without yiuk-ops.exe.
    goto :failed
)

echo [3/4] Collecting Qt and MinGW runtime files...
mkdir "%STAGE_DIR%" >nul 2>nul
"%QT_ROOT%\bin\windeployqt.exe" --release --compiler-runtime --no-translations --dir "%STAGE_DIR%" "%BUILD_DIR%\yiuk-ops.exe"
if errorlevel 1 goto :failed
if not exist "%STAGE_DIR%\yiuk-ops.exe" copy /y "%BUILD_DIR%\yiuk-ops.exe" "%STAGE_DIR%\yiuk-ops.exe" >nul
if not exist "%STAGE_DIR%\Qt6Core.dll" (
    echo ERROR: Qt runtime deployment is incomplete: Qt6Core.dll is missing.
    goto :failed
)
if not exist "%STAGE_DIR%\platforms\qwindows.dll" (
    echo ERROR: Qt runtime deployment is incomplete: platforms\qwindows.dll is missing.
    goto :failed
)

echo [4/4] Creating distributable ZIP and cleaning temporary build files...
tar.exe -a -c -f "%ZIP_STAGE%" -C "%STAGE_DIR%" .
if errorlevel 1 goto :failed
if exist "%PACKAGE_DIR%" rmdir /s /q "%PACKAGE_DIR%"
move "%STAGE_DIR%" "%PACKAGE_DIR%" >nul
if errorlevel 1 goto :failed
if exist "%BUILD_DIR%" rmdir /s /q "%BUILD_DIR%"
if exist "%ZIP_FILE%" del /q "%ZIP_FILE%"
move "%ZIP_STAGE%" "%ZIP_FILE%" >nul
if errorlevel 1 goto :failed

echo.
echo Package ready: "%PACKAGE_DIR%\yiuk-ops.exe"
echo ZIP ready: "%ZIP_FILE%"
echo Distribute the entire package folder or ZIP, not only the EXE.
exit /b 0

:failed
echo.
echo Packaging failed. The previous package, if any, was kept.
exit /b 1
