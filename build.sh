#!/bin/bash
set -e
chmod +x gradlew
# 構建 Release APK（正式版本）
./gradlew assembleRelease
