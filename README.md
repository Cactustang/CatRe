# CatRe

CatRe is an Android app for tracking cat care records. It supports multiple cats, behavior check-ins, calendar records, behavior management, archived behaviors, and value-based records such as weight.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Room
- Gradle Android Plugin

## Requirements

- JDK 17
- Android SDK
- Gradle wrapper included in this repository

## Build

Run unit tests:

```powershell
.\gradlew.bat :app:testDebugUnitTest --no-daemon --max-workers=1
```

Build a debug APK:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --max-workers=1
```

The generated debug APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Release

Current release:

```text
v0.1.3
```

Debug APK download:

```text
https://github.com/Cactustang/CatRe/releases/download/v0.1.3/CatRe-v0.1.3-debug.apk
```
