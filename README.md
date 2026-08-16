# Expenses Tracker

A fast, offline-first expense tracker built with Jetpack Compose. Log a purchase in a couple of taps, keep fixed and recurring costs on autopilot, and see exactly where a monthly budget is going — broken down by category, in whatever currency you actually spent it in.

## Features

- **Quick add** — amount, currency, category and date in one bottom sheet, built for speed
- **Recurring expenses** — define a monthly or weekly fixed cost once (rent, a subscription, a season pass) and it's automatically logged when it's due, with catch-up generation if the app wasn't opened for a while
- **Budgets** — a total monthly budget plus optional per-category limits, with live progress on the home screen
- **Multi-currency** — a fixed EUR base currency with per-currency exchange rates that can be edited by hand or refreshed live from the free [Frankfurter](https://www.frankfurter.app/) API (no key required); every expense keeps its original currency and amount alongside the converted value
- **Light / Dark / System theme** — a custom Material 3 color scheme, switchable from Settings and persisted across launches
- **Fully offline** — everything lives in a local database; no account, no login, no data leaving the device except for the optional exchange-rate refresh

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Room** for local persistence
- **DataStore Preferences** for app settings (budget, theme, etc.)
- **Navigation Compose** for the bottom-tab navigation
- **Kotlin Coroutines / Flow** throughout the data layer
- Plain manual dependency injection via the `Application` class — no DI framework

## Project structure

```
app/src/main/java/com/example/expensestracker/
├── data/
│   ├── local/          # Room entities, DAOs, database, seed data
│   ├── remote/          # Exchange rate API client
│   ├── repository/      # Single ExpenseRepository the UI talks to
│   └── settings/        # DataStore-backed app preferences
├── domain/               # Recurring-expense generation logic
├── ui/
│   ├── dashboard/        # Home screen
│   ├── addexpense/       # Quick-add bottom sheet
│   ├── categories/       # Categories & budgets screen
│   ├── recurring/        # Fixed/recurring expenses screen
│   ├── settings/         # Currencies & theme screen
│   ├── navigation/       # Bottom-nav routes
│   └── theme/            # Color scheme, typography, shapes
└── util/                 # Formatting helpers
```

## Getting started

**Requirements:** Android Studio (Ladybug or newer), JDK 17, Android SDK Platform 36.

```bash
git clone https://github.com/Ferro98/ExpensesTracker.git
```

Open the project in Android Studio and run it on a device or emulator with API 26+, or from the command line:

```bash
./gradlew assembleDebug
```

The base currency is fixed to EUR; additional currencies (DKK is seeded by default) can be added from Settings.
