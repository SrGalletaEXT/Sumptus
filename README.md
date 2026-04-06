# Sumptus

Sumptus is a native Android expense tracker focused on one simple goal: making daily spending capture fast and easy.

This repository currently contains an early MVP built with Kotlin and Jetpack Compose. The app already supports local expense tracking, a lightweight dashboard, and a clean mobile-first flow for adding and reviewing entries.

## Overview

The current version of Sumptus is designed as a local-first personal finance app for Android.

At this stage, the app helps you:

- add expenses with a title, amount, category, and optional notes
- store data locally on device
- review a monthly summary and recent activity
- inspect category-based spending distribution
- remove entries from history

The in-app UI is currently in Spanish. This README is intentionally written in English for the public repository.

## Features

- Fast expense entry flow
- Monthly total, weekly total, and average expense summary
- Category breakdown for the current month
- Recent activity history
- Local persistence using DataStore
- Native Android UI built with Jetpack Compose

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android ViewModel
- Preferences DataStore
- Gradle Kotlin DSL

## Project Status

Sumptus is in MVP stage.

What is already implemented:

- core Android project structure
- expense creation flow
- local storage
- summary dashboard
- recent history list
- delete action for saved entries

What is not implemented yet:

- cloud sync
- user accounts
- budgets by category
- CSV export
- advanced filters and analytics

## Getting Started

### Prerequisites

- Android Studio
- JDK 17
- Android SDK installed locally

### Run Locally

1. Clone the repository:

```bash
git clone https://github.com/SrGalletaEXT/Sumptus.git
cd Sumptus
```

2. Open the project in Android Studio.
3. Let Gradle sync the project.
4. If Android Studio does not detect your SDK automatically, either:

```properties
sdk.dir=/path/to/Android/Sdk
```

Create that in `local.properties`, or configure `ANDROID_HOME` in your environment.

5. Run the app on an emulator or a physical Android device.

## Build Notes

- `./gradlew help --no-daemon` works with the current project setup
- `./gradlew :app:assembleDebug --no-daemon` requires a valid Android SDK path

## Roadmap

Planned next steps for the project:

- budgets by category
- date range filters
- CSV export
- backup and sync options
- stronger reporting and insights

## Repository Goal

Sumptus is being built as a practical, no-friction Android app for tracking personal expenses without unnecessary complexity. The current MVP is the foundation for a fuller product focused on clarity, speed, and everyday usefulness.
