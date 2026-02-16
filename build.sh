#!/bin/bash
set -e
chmod +x gradlew
# 先構建 Debug APK（不需要簽名，可以直接使用）
./gradlew assembleDebug
