#!/bin/bash
set -e

# 直接調用 Gradle Wrapper JAR，繞過有問題的 gradlew 腳本
java -jar gradle/wrapper/gradle-wrapper.jar assembleDebug
