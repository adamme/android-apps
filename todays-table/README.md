# Today's Table

An Android Jetpack Compose app for rating weekly **HelloFresh** and **YellowPicnic** meals, backed by a Google Sheet and Google Apps Script Web App.

## Features

- **Unified Unrated Queue:** Displays all unrated meals across the `HelloFresh` and `YellowPicnic` tabs in a single filterable list (`All`, `HelloFresh`, `YellowPicnic`).
- **Two-Person Rating Sheet:** Pick a date (`Today` or calendar picker) and submit 1–10 (or `-`) scores for both diners. Submitting updates the Google Sheet, preserves formulas/hyperlinks, moves the rated row below the unrated queue, and dismisses the confirmation banner after 1 second.
- **Exact Meal Photography + Gemini Fallback:**
  - **HelloFresh:** Pulls recipe photos from the linked recipe URL (or search fallback) and includes a **View Recipe** button.
  - **YellowPicnic:** Uses the exact YellowPicnic product photo URL hyperlinked in Column B, falling back to Gemini image generation when an older meal has no photo URL.
- **Automated Weekly Sync (Apps Script):**
  - `HelloFresh Sync.js`: Uses a rotating OAuth refresh token (`HELLOFRESH_REFRESH_TOKEN`) to fetch the upcoming `RUNNING` delivery's selected meals and add-ons/sides/desserts and insert new entries with recipe links at the top of the `HelloFresh` tab.
  - `YellowPicnic Sync.js`: Logs into YellowPicnic to fetch the upcoming delivery's selected meals and image URLs and inserts new entries with photo hyperlinks at the top of the `YellowPicnic` tab.
- **Foreground Auto-Sync & Pull-to-Refresh:** Automatically stays in sync with changes made directly in the Google Sheet while the app is open.

## Project Structure

- `app/` — Android Jetpack Compose client (`Kotlin`, `Material 3`, `OkHttp`, `Coil`)
- `apps-script/` — Google Apps Script backend (`Rating App.js`, `HelloFresh Sync.js`, `YellowPicnic Sync.js`, `Add New Row.js`, `HelloFresh Recipe Search.js`)
- `web-app/` — Companion web UI prototype

## Configuration & Secrets

No API keys, tokens, passwords, or private spreadsheet/deployment IDs are committed to this repository.

### Android App

1. Copy `.env.example` or set `GEMINI_API_KEY` in `gradle.properties` / environment variables if you want a default fallback Gemini image-generation key compiled into `BuildConfig.GEMINI_API_KEY`.
2. In the app's **Backend Settings** sheet (gear icon), configure:
   - **Apps Script Web App URL** (`https://script.google.com/macros/s/<DEPLOYMENT_ID>/exec`)
   - **Google Sheet URL** (`https://docs.google.com/spreadsheets/d/<SPREADSHEET_ID>/edit`)
   - **Gemini API Key** (optional, used only as a fallback when a YellowPicnic meal has no product image URL)
3. Build and install:
   ```bash
   ./gradlew :app:assembleDebug
   ```

### Google Apps Script Backend

1. Create or open your Google Sheet with `HelloFresh` and `YellowPicnic` tabs (Columns A–E: `Date`, `Meal`, `Person 1`, `Person 2`, `Average`).
2. Copy `apps-script/.clasp.json.example` to `apps-script/.clasp.json` and insert your Apps Script `scriptId` (kept out of git).
3. Push scripts with `clasp push` and deploy `Rating App.js` as a Web App (`Execute as: Me`, `Who has access: Anyone`).
4. In **Project Settings -> Script Properties**, set:
   - `YELLOW_PICNIC_USERNAME` and `YELLOW_PICNIC_PASSWORD` (for `YellowPicnic Sync.js`)
   - `HELLOFRESH_REFRESH_TOKEN` and optionally `HELLOFRESH_SUBSCRIPTION_ID` (for `HelloFresh Sync.js`)
5. Optionally add weekly time-driven triggers in Apps Script for `syncHelloFreshUpcomingMeals` and `syncYellowPicnicUpcomingMeals`.
