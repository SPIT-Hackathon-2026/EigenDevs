# GitLane 🚀

A fully offline, native Android Git client built with **Kotlin + JGit**.

## Features
- Create & manage local Git repositories
- Stage files and commit changes
- View commit history with author, date & SHA
- 100% offline — no internet required

## Tech Stack
- **Language:** Kotlin
- **Git Engine:** JGit 6.10 (Eclipse)
- **UI:** Material Design 3, AndroidX, RecyclerView
- **Async:** Kotlin Coroutines
- **Storage:** Android internal storage (`filesDir/GitLane/`)

## Requirements
- Android Studio Ladybug or newer
- Android SDK 26+ (minSdk 26)
- JDK: Use the **Embedded JDK** that ships with Android Studio

## Getting Started
1. Clone this repo
2. Open the `android/` folder in Android Studio (`File → Open`)
3. Wait for Gradle sync to complete
4. Click ▶ Run

> **Note:** No `npm install` or Node.js needed — this is a pure native Android project.
