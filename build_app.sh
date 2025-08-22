#!/bin/bash

echo "========================================"
echo "Сборка приложения ReYohoho с FreeTorr"
echo "========================================"

echo ""
echo "Проверка наличия Java..."
if ! command -v java &> /dev/null; then
    echo "ОШИБКА: Java не установлена или не найдена в PATH"
    echo "Установите JDK 11 или выше"
    exit 1
fi

echo "Java найдена: $(java -version 2>&1 | head -n 1)"
echo ""

echo "Проверка наличия Android SDK..."
if [ -z "$ANDROID_HOME" ]; then
    echo "ОШИБКА: ANDROID_HOME не установлена"
    echo "Установите Android SDK и настройте переменную ANDROID_HOME"
    exit 1
fi

echo "Android SDK найдена: $ANDROID_HOME"
echo ""

echo "Очистка предыдущей сборки..."
if [ -d "app/build" ]; then
    rm -rf app/build
    echo "Предыдущая сборка очищена"
fi

echo ""
echo "Синхронизация Gradle..."
./gradlew clean
if [ $? -ne 0 ]; then
    echo "ОШИБКА: Не удалось очистить проект"
    exit 1
fi

echo ""
echo "Сборка APK..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "ОШИБКА: Не удалось собрать APK"
    exit 1
fi

echo ""
echo "Проверка созданного APK..."
if [ -f "app/build/outputs/apk/debug/app-debug.apk" ]; then
    echo ""
    echo "========================================"
    echo "СБОРКА УСПЕШНО ЗАВЕРШЕНА!"
    echo "========================================"
    echo ""
    echo "APK файл создан: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "Размер файла: $(ls -lh app/build/outputs/apk/debug/app-debug.apk | awk '{print $5}')"
    echo ""
    echo "Для установки:"
    echo "1. Скопируйте APK на устройство"
    echo "2. Установите приложение"
    echo "3. Разрешите установку из неизвестных источников"
    echo ""
else
    echo "ОШИБКА: APK файл не найден"
    exit 1
fi

echo "Сборка завершена успешно!"

