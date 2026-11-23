# Istanbul Weather Intelligence

An offline-first Android app that collects weather data for Istanbul from **two
independent providers**, stores everything locally, and compares the two
providers field by field — with real values, real units and honest gaps.

| | |
|---|---|
| **Provider 1** | MGM — Meteoroloji Genel Müdürlüğü (Turkish State Met Service) |
| **Provider 2** | UK Met Office Weather DataHub — Global Spot |
| **Storage** | Room (SQLite) + DataStore Preferences. Nothing leaves the device. |
| **Credentials** | The Met Office key is encrypted with an Android Keystore key. Never logged, never exported, never in the APK. |
| **Permissions** | The app declares `INTERNET` and `ACCESS_NETWORK_STATE`. **No location permission** — the app is fixed to Istanbul. WorkManager contributes `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`, which it needs in order to run and reschedule background collection. |

---

## Table of contents

1. [What the app does](#what-the-app-does)
2. [Screens](#screens)
3. [Architecture](#architecture)
4. [Data model](#data-model)
5. [The comparison engine](#the-comparison-engine)
6. [Build and run](#build-and-run)
7. [Provider setup](#provider-setup)
8. [Testing](#testing)
9. [Verified limitations](#verified-limitations)
10. [Attribution and licensing](#attribution-and-licensing)
11. [Project layout](#project-layout)

---

## What the app does

- **Collects on a schedule.** WorkManager runs a periodic collection
  (default every 6 hours, minimum 15 minutes — the WorkManager hard limit).
  A **Collect Now** button runs a one-off collection at any time. The two
  providers are collected **independently**: if Met Office fails, MGM data is
  still stored and usable, and the app says exactly what happened.
- **Keeps everything.** Every HTTP response is stored verbatim with a SHA-256
  hash, every normalized value is stored next to the original provider value,
  and nothing is ever deleted. Records are retained indefinitely.
- **Never invents data.** A value the provider did not supply is `null`, never
  `0`. MGM's `-9999` sentinel becomes `null`. Unmatched parameters are shown
  in their own column instead of being dropped or zero-filled.
- **Compares honestly.** MGM and Met Office use completely different parameter
  names (`sicaklik` vs `screenTemperature`). The app maps them through a
  verified crosswalk, converts units explicitly, aligns forecast valid times
  and shows the real difference — or states plainly that the two are not
  comparable (e.g. weather-condition codes use different vocabularies).
- **Works offline.** Every screen reads from the local database. With no
  network the app still shows the last collected data.

---

## Screens

Three always-visible top navigation buttons — **Home → Compare → Settings** —
plus horizontal swiping between the same three pages. **There is no bottom
navigation bar and no drawer.** A one-time hint explains the swipe gesture.

> **Home contains weather only.** Per the final UI specification the
> MGM-vs-Met-Office comparison card was *removed* from Home; comparison lives
> on its own page.

| # | Screen | Where |
|---|---|---|
| A | Dashboard | Home → *All screens* → Dashboard |
| B | Current Weather | Home → *All screens* → Current |
| C | Hourly Forecast | Home → *All screens* → Hourly |
| D | Three-hourly Forecast | Home → *All screens* → 3-hourly |
| E | Daily Forecast | Home → *All screens* → Daily |
| F | Source Comparison | The **Compare** tab |
| G | Historical Data | Home → *All screens* → History |
| H | Forecast Change Analysis | Home → *All screens* → Changes |
| I | Weather Warnings | Home → *All screens* → Warnings |
| J | Collection History | Home → *All screens* → Runs |
| K | Settings | The **Settings** tab |
| L | Data Export | Home → *All screens* → Export |

Detail screens open over the current page while the three top navigation
buttons stay visible and usable.

---

## Architecture

```
UI (Jetpack Compose)
  └─ WeatherViewModel            StateFlow<WeatherUiState>
       └─ WeatherRepository       single access point to local storage
            ├─ WeatherDatabase    Room (12 tables)
            ├─ SettingsRepository DataStore Preferences (app_settings)
            └─ ExportManager      CSV / JSON
Provider layer
  ├─ WeatherDataSource           shared interface
  ├─ MgmWeatherDataSource        ─┐
  ├─ MetOfficeWeatherDataSource   ├─ adapters
  ├─ MgmParser                   │  pure, unit-tested parsers
  └─ MetOfficeParser            ─┘
Scheduling
  └─ WeatherCollectionWorker → CollectionEngine → repository
```

- **Manual DI** (`AppContainer`) — one object graph, no DI framework needed.
- **Repository pattern** — the UI never touches the network or the database
  directly.
- **Pure parsers** — `MgmParser` and `MetOfficeParser` have no Android or
  network dependencies, so they are unit tested against captured payloads.
- **Shared domain model** — `Measurement`, `ForecastRun`, `ComparisonRow`, … —
  with provider-specific payloads retained alongside the normalized values.

### Chart library

The specification allows *"a maintained chart library **or** custom Compose
charts"*. This app uses **custom Jetpack Compose `Canvas` charts**
(`ui/screens/Charts.kt`) so they follow the app theme exactly and add no
third-party dependency. The charts plot only values the provider actually
supplied.

---

## Data model

Thirteen tables, exactly as specified. `app_settings` is DataStore
Preferences; the other twelve are Room entities.

| Table | Contents |
|---|---|
| `providers` | MGM and Met Office, licence, attribution, enabled flag |
| `locations` | Name, country, region/district, lat/long, timezone, provider id |
| `stations` | External station id, name, lat/long, elevation, verbatim `metadata_json` |
| `collection_runs` | Start/finish, configured interval, status, app version, trigger |
| `provider_collection_results` | Per-provider status, counts, error code/message |
| `raw_responses` | Verbatim body, HTTP status, content type, SHA-256, safe request metadata |
| `weather_observations` | Observed/collected times, normalized JSON, provider-specific JSON |
| `forecast_runs` | Product, issued/collected times, provider forecast id |
| `forecast_values` | Valid time, parameter code/name, numeric **or** text value, unit, original value, availability |
| `weather_warnings` | Issue time, validity, severity, title, description, raw data |
| `weather_metadata` | Per-parameter name, unit, description, mapping metadata |
| `collection_errors` | Time, error type, message, retryable, endpoint, HTTP status |
| `app_settings` | DataStore Preferences: interval, toggles, theme, encrypted key, coordinates |

The database is **version 2**. `MIGRATION_1_2` adds the free-form
`provider_notes` column; the exported schemas for v1 and v2 live in
`app/schemas/` and the migration is covered by an instrumentation test.

---

## The comparison engine

`data/repository/ParameterCrosswalk.kt` maps MGM keys onto Met Office keys per
product scope:

| Canonical | MGM | Met Office (hourly) | Met Office (three-hourly) | Met Office (daily) |
|---|---|---|---|---|
| Temperature | `sicaklik` | `screenTemperature` | — | `dayMaxScreenTemperature` / `nightMinScreenTemperature` |
| Feels like | `hissedilenSicaklik` | `feelsLikeTemperature` | `feelsLikeTemp` | `dayMaxFeelsLikeTemp` / `nightMinFeelsLikeTemp` |
| Humidity | `nem` | `screenRelativeHumidity` | `screenRelativeHumidity` | `middayRelativeHumidity` / `midnightRelativeHumidity` |
| Sea-level pressure | `denizeIndirgenmisBasinc` | `mslp` | `mslp` | `middayMslp` / `midnightMslp` |
| Visibility | `gorus` | `visibility` | `visibility` | `middayVisibility` / `midnightVisibility` |
| Wind speed | `ruzgarHiz` / `ruzgarHizi` | `windSpeed10m` | `windSpeed10m` | `midday10MWindSpeed` |
| Wind gust | `maksimumRuzgarHizi` | `windGustSpeed10m` | `windGustSpeed10m` | `midday10MWindGust` |
| Wind direction | `ruzgarYon` / `ruzgarYonu` | `windDirectionFrom10m` | `windDirectionFrom10m` | `midday10MWindDirection` |
| Condition | `hadiseKodu` / `hadise` | `significantWeatherCode` | `significantWeatherCode` | `daySignificantWeatherCode` |

Rules the engine enforces:

- **Units are converted explicitly, never in place.** Wind: Met Office m/s →
  km/h (the canonical unit, matching MGM). Pressure: Met Office Pa → hPa. The
  stored source value is untouched and its unit is shown underneath.
- **Unmatched parameters stay visible** under a provider-specific key
  (`mgm:<code>` / `mo:<code>`) — never dropped, never zero-filled.
- **Forecast values are aligned by valid time.** MGM's "hourly" product is
  matched against the Met Office **three-hourly** product because both are
  three-hour steps; the Met Office **hourly** product is listed on its own
  because MGM has no hourly-resolution product.
- **Weather codes are not comparable.** MGM uses `CB`/`HY`/… and Met Office
  uses 0–30 numeric codes; the app shows both side by side without inventing
  a difference.

---

## Build and run

### Requirements

- JDK 17+ (the project targets Java 17)
- Android SDK with **platform 35** and build-tools 35
- Gradle wrapper included (Gradle 9.7.1, AGP 8.7.3, Kotlin 2.0.21)

### Desktop / CI

```sh
git clone <this repo>
cd istanbul-weather-intelligence
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Termux / ARM (the environment this app was built in)

The Android Gradle Plugin's `aapt2` binary does not run on Termux, so the
project overrides it with the local build:

```properties
# gradle.properties
android.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2
```

```sh
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=/data/data/com.termux/files/usr/tmp/opencode/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_USER_HOME=$HOME/.gradle
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew :app:assembleDebug --no-daemon
```

### Signing

The release build is signed with a local keystore (`debug.keystore` in the
project root) so that a release APK can be produced and installed without
extra setup. **Replace it with your own keystore before distributing the app.**

---

## Provider setup

### MGM — no key required

MGM's public web service is used read-only. The app sets the `Referer`,
`Origin` and a browser `User-Agent` header, which the service requires.
See [`docs/MGM_NOTES.md`](docs/MGM_NOTES.md).

### Met Office — free API key required

1. Create a DataHub account at <https://datahub.metoffice.gov.uk/>.
2. Request access to the **Global Spot** dataset.
3. Copy your API key.
4. Open the app → **Settings** → *API configuration* → paste the key → **Save key**.

The key is encrypted with an Android Keystore key before it is stored. Without
a key the app collects MGM only and shows an actionable message on the Home
page. See [`docs/API_SETUP.md`](docs/API_SETUP.md).

---

## Testing

```sh
./gradlew :app:testDebugUnitTest      # 53 JVM unit tests
./gradlew :app:connectedDebugAndroidTest   # instrumented tests (needs a device)
```

| Suite | Count | What it covers |
|---|---|---|
| `MgmParserTest` | 9 | sentinel handling, units, hourly/daily arrays, code table |
| `MetOfficeParserTest` | 7 | GeoJSON parsing, null handling, no-whitelist capture |
| `ComparisonBuilderTest` | 16 | crosswalk, unit conversion, valid-time alignment, no zero-fill |
| `ForecastChangeBuilderTest` | 8 | changed / unchanged / added / removed parameters |
| `SunTimesTest` | 8 | sunrise/sunset against published times, polar day/night |
| `CollectionSchedulerPolicyTest` | 5 | interval clamping, unique work names |
| `DatabaseDaoTest` | 9 | Room DAOs, FK cascade, null preservation |
| `WeatherRepositoryTest` | 8 | raw + normalized persistence, independent providers |
| `CollectionSchedulerTest` | 7 | unique periodic work, interval, cancel on disable |
| `WeatherViewModelTest` | 9 | page selection, theme, interval, provider toggles, encrypted key |
| `AppShellUiTest` | 6 | nav buttons always visible, no bottom bar, no Home comparison card |
| `DatabaseMigrationTest` | 1 | v1 → v2 migration against the exported schema |

More detail in [`docs/TESTING.md`](docs/TESTING.md).

---

## Verified limitations

These are **provider restrictions confirmed against the live services**, not
implementation gaps. Full detail in
[`docs/NOT_IMPLEMENTABLE.md`](docs/NOT_IMPLEMENTABLE.md).

| Requested | Status |
|---|---|
| MGM meteorological warnings | **Not available.** No warning endpoint exists on the public service (verified 404). The app shows "no warnings received" — it never invents one. |
| MGM true 3-hourly product | **Does not exist.** MGM's `tahminler/saatlik` *is* its hourly product, but it returns **3-hour steps**. The app labels it "MGM hourly product — 3-hour resolution" instead of pretending to have a separate product. |
| Met Office Istanbul observations | **Forecast only.** Global Spot publishes no ground-station observation product for Istanbul. The Compare page says so explicitly and never implies a measured Met Office value. |
| Sunrise / sunset | **Not published by either provider.** The app computes them locally with the NOAA sunrise equation and labels them *"computed"*. |

Other honest caveats:

- MGM's public service is **not an officially documented public API**. It may
  change without notice. The app handles endpoint changes and unavailable
  services gracefully and records every failure.
- The Met Office free Global Spot plan allows **360 calls per day**. With
  three products per collection, that is about 120 collections per day.
- The app **never** claims one provider is more accurate than the other.
- The station catalogue endpoint returns **all 1930** Turkish stations; the
  `il` query parameter is ignored by the service, so the app filters to the
  45 Istanbul stations client-side.

---

## Attribution and licensing

- **MGM** — Meteoroloji Genel Müdürlüğü (Turkish State Met Service).
  *Data source: MGM.* The public web service is used read-only.
- **Met Office** — Weather DataHub, Global Spot. **Powered by Met Office data.**
  The Met Office Weather DataHub licence applies to the Met Office data.
- All weather data is stored **only on this device**. No weather history is
  ever sent to any server. The app collects **no personal data** and requests
  **no location permission**.

The app itself is provided as-is for personal use.

---

## Project layout

```
app/src/main/java/com/istanbul/weatherintel/
├── WeatherApplication.kt        Application + AppContainer (manual DI)
├── WeatherViewModel.kt          All screen state
├── MainActivity.kt
├── data/
│   ├── collection/              CollectionEngine
│   ├── db/                      Entities, DAOs, WeatherDatabase, migrations
│   ├── export/                  ExportManager (CSV / JSON)
│   ├── model/                   Shared domain models
│   ├── net/                     HttpClient (Retrofit / OkHttp)
│   ├── prefs/                   SettingsRepository (DataStore)
│   ├── provider/                MGM + Met Office adapters and parsers
│   ├── repository/              WeatherRepository, ComparisonBuilder,
│   │                            ParameterCrosswalk, ForecastChangeBuilder
│   └── solar/                   SunTimes (computed sunrise / sunset)
├── security/                    CryptoManager (Android Keystore)
├── ui/                          Theme, header, Home, Compare, Settings,
│                                detail screens A–L, charts
└── worker/                      WeatherCollectionWorker, CollectionScheduler

app/src/test/                    53 JVM unit tests
app/src/androidTest/             Room, repository, scheduler, ViewModel, UI tests
app/schemas/                     Exported Room schemas (v1, v2)
docs/                            API setup, MGM notes, field matrix, testing
```
