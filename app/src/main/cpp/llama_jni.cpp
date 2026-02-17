name: Android Build

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]
  workflow_dispatch:

jobs:
  build-android:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          submodules: recursive
          fetch-depth: 0

      - name: Initialize & update submodules
        run: git submodule update --init --recursive

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Set up Android SDK
        uses: android-actions/setup-android@v3

      - name: Install NDK 25.1.8937393 explicitly
        run: |
          sdkmanager "ndk;25.1.8937393"
          echo "NDK_HOME=${ANDROID_HOME}/ndk/25.1.8937393" >> $GITHUB_ENV

      - name: Install ImageMagick
        run: sudo apt-get update && sudo apt-get install -y imagemagick

      - name: Generate App Icons
        run: |
          mkdir -p app/src/main/res/mipmap-xxxhdpi
          convert -size 512x512 xc:"#2196F3" -gravity center -fill white -font DejaVu-Sans-Bold -pointsize 120 -draw "text 0,0 'ACA'" app/src/main/res/mipmap-xxxhdpi/ic_launcher.png

      - name: Build Setup
        run: |
          chmod +x gradlew
          grep "llama_memory_clear" external/llama.cpp/include/llama.h || (echo "API Mismatch: llama_memory_clear not found!" && exit 1)

      - name: Build Debug APK
        run: |
          ./gradlew :app:assembleDebug \
            -Pandroid.ndkVersion="25.1.8937393" \
            -Pandroid.ndkDirectory="${ANDROID_HOME}/ndk/25.1.8937393" \
            --no-daemon --stacktrace

      - name: Upload Debug APK
        uses: actions/upload-artifact@v4
        with:
          name: LLMCodeAssistant-debug-apk
          path: app/build/outputs/apk/debug/*.apk
