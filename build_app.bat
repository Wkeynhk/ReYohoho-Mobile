@echo off
echo ========================================
echo Сборка приложения ReYohoho с FreeTorr
echo ========================================

echo.
echo Проверка наличия Java...
java -version >nul 2>&1
if errorlevel 1 (
    echo ОШИБКА: Java не установлена или не найдена в PATH
    echo Установите JDK 11 или выше
    pause
    exit /b 1
)

echo Java найдена
echo.

echo Проверка наличия Android SDK...
if not exist "%ANDROID_HOME%" (
    echo ОШИБКА: ANDROID_HOME не установлена
    echo Установите Android SDK и настройте переменную ANDROID_HOME
    pause
    exit /b 1
)

echo Android SDK найдена: %ANDROID_HOME%
echo.

echo Очистка предыдущей сборки...
if exist "app\build" (
    rmdir /s /q "app\build"
    echo Предыдущая сборка очищена
)

echo.
echo Синхронизация Gradle...
call gradlew clean
if errorlevel 1 (
    echo ОШИБКА: Не удалось очистить проект
    pause
    exit /b 1
)

echo.
echo Сборка APK...
call gradlew assembleDebug
if errorlevel 1 (
    echo ОШИБКА: Не удалось собрать APK
    pause
    exit /b 1
)

echo.
echo Проверка созданного APK...
if exist "app\build\outputs\apk\debug\app-debug.apk" (
    echo.
    echo ========================================
    echo СБОРКА УСПЕШНО ЗАВЕРШЕНА!
    echo ========================================
    echo.
    echo APK файл создан: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo Размер файла:
    for %%A in ("app\build\outputs\apk\debug\app-debug.apk") do echo %%~zA байт
    echo.
    echo Для установки:
    echo 1. Скопируйте APK на устройство
    echo 2. Установите приложение
    echo 3. Разрешите установку из неизвестных источников
    echo.
) else (
    echo ОШИБКА: APK файл не найден
    pause
    exit /b 1
)

echo Нажмите любую клавишу для выхода...
pause >nul

