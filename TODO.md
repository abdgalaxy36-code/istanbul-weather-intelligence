# Istanbul Weather Intelligence — Build Tracker

Last updated: 2026-11-22
Legend: `[x]` done · `[~]` in progress · `[ ]` not started · `[!]` blocked

---

## Current status (read this first)

**The app compiles cleanly**, **all 53 unit tests pass**, the **instrumented
tests compile**, the **debug and release APKs are built and signed**, and the
documentation is complete.

Remaining work: one pass of the instrumented test suite and the manual
verification checklist on real hardware (no device or emulator is available in
this environment).

Two things are blocked on the user:

- The **Met Office DataHub API key**. MGM works keyless; Met Office needs a
  free key (360 calls/day) pasted into Settings. Without it the app collects
  MGM only and says so on the Home page.
- A **device or emulator** to execute the instrumented tests and the manual
  checklist in `docs/TESTING.md` §3.

---

## Phase 1 — Provider verification (live, against real APIs)

- [x] MGM endpoints discovered and verified live (`/web/merkezler`, `/web/sondurumlar`,
      `/web/tahminler/saatlik`, `/web/tahminler/gunluk`, `/web/istasyonlar`)
- [x] MGM required headers confirmed (`Referer` + `Origin` + browser UA)
- [x] MGM units verified empirically (degC, %, hPa, **km/h**, degrees, m, mm, okta)
- [x] MGM `-9999` sentinel semantics confirmed → mapped to `null`, never `0`
- [x] MGM condition-code table extracted verbatim from MGM's own `convertHadise()`
- [x] MGM "saatlik" product confirmed to be **3-hour steps**, not hourly
- [x] MGM three-hourly product confirmed **does not exist** (404) → marked unsupported
- [x] MGM warning feed confirmed **does not exist** (404) → marked unsupported
- [x] Met Office DataHub endpoint shape verified live (`/sitespecific/v0/point/{hourly|three-hourly|daily}`)
- [x] Met Office 401 behaviour confirmed without a key
- [x] Met Office parameter catalogue (18 hourly / 21 three-hourly / 41 daily, v1.0.2) documented
- [x] Met Office free Global Spot quota confirmed (360 calls/day)
- [x] Met Office Global Spot = **forecast only**, never Istanbul observations
- [!] Met Office live data cannot be exercised until the user supplies a free DataHub API key

## Phase 2 — Project scaffold & build environment

- [x] Gradle 9.7.1 / AGP 8.7.3 / Kotlin 2.0.21 project created
- [x] Compose BOM 2024.10.00, Room 2.6.1 + KSP, DataStore, WorkManager, Retrofit/OkHttp,
      kotlinx-serialization, Vico declared
- [x] Termux/ARM `aapt2` override configured in `gradle.properties`
- [x] AndroidManifest — `INTERNET` + `ACCESS_NETWORK_STATE` only (no location permission)
- [x] Custom WorkManager `Configuration.Provider` (default init removed)
- [x] Light/dark themes, `values-night`, launcher icon vector
- [x] `:app:compileDebugKotlin` passes

## Phase 3 — Data layer (13 tables per spec §6)

- [x] 12 Room entities: providers, locations, stations, collection_runs,
      provider_collection_results, raw_responses, weather_observations, forecast_runs,
      forecast_values, weather_warnings, weather_metadata, collection_errors
- [x] 13th table `app_settings` implemented as DataStore Preferences
- [x] 12 DAOs with the required columns/indexes
- [x] `WeatherDatabase` v1 + `MIGRATION_1_2` + type converters
- [x] Domain models (`Measurement`, `Availability`, `ForecastRun`, `ComparisonRow`, …)
- [x] `WeatherRepository` — raw payload + normalized row persistence, provenance hashes
- [x] `SettingsRepository` (DataStore) + `CryptoManager` (Android Keystore encrypted credentials)
- [x] `ExportManager` — CSV + JSON with UTC and Istanbul local timestamps

## Phase 4 — Provider adapters

- [x] `MgmParser` — observation / hourly / daily, generic `*Gun<n>` capture, sentinel handling
- [x] `MetOfficeParser` — GeoJSON, every returned parameter captured (no whitelist)
- [x] `MgmWeatherDataSource` — headers, raw payload retention, no fabricated fields
- [x] `MetOfficeWeatherDataSource` — all three products, `apikey` header, error classification
- [x] Shared `WeatherDataSource` interface, per-provider independent success/failure
- [x] `CollectionEngine` — independent provider outcomes, no credentials in logs
- [x] `WeatherCollectionWorker` + `CollectionScheduler` (WorkManager, min 15 min, default 6 h)
- [x] `MetOfficeParams` catalogue (label + unit + category per parameter)
- [x] `MetOfficeWeatherCodes` 0–30 and `MgmCodes` official table

## Phase 5 — UI (3 always-visible top nav pages + 12 detail screens)

- [x] `AppRoot` — HorizontalPager Home → Compare → Settings, no bottom nav, no drawer
- [x] Always-visible Home / Compare / Settings buttons
- [x] First-use swipe hint (persisted dismissal)
- [x] Header sun/moon theme toggle (persisted) + Collect Now
- [x] **Home contains weather only — no MGM-vs-MetOffice comparison card** (final spec override)
- [x] Compare — expandable, grouped, uncapped, never zero-fills
- [x] Settings — interval, autos, Collect Now, provider toggles, theme, status, export, API key
- [x] Detail screens A–L: Dashboard, Current, Hourly, Three-hourly, Daily, Comparison,
      Historical, Forecast Change, Warnings, Collection History, Settings, Data Export
- [x] Detail screens opened from Home while the 3 top nav buttons stay visible
- [x] Met Office attribution "Powered by Met Office data" in Home + Settings

## Phase 6 — Comparison engine  ← **IN PROGRESS, CRITICAL**

- [x] **C1 — Semantic cross-provider parameter mapping.**
      New file `data/repository/ParameterCrosswalk.kt`. Canonical parameters
      (temperature, feels-like, min/max temperature, humidity, sea-level
      pressure, visibility, wind speed/gust/direction, condition) map MGM keys
      to Met Office keys per product scope. MGM daily keys carry a day-index
      suffix (`enDusukGun0`) and are matched on the base name. Any parameter
      without a counterpart gets a provider-specific key (`mgm:<code>` /
      `mo:<code>`) so it is still listed in its own column, never dropped.
      Unit conversion is explicit (wind m/s↔km/h, pressure Pa↔hPa) and the
      source value is never modified.
- [x] **C2 — Compare page lists real forecast values.**
      `ForecastComparisonSection` replaced by `periodItems`: one card per
      aligned valid time, with every parameter of both providers inside it,
      grouped by category. Three sections: three-hourly (MGM saatlik ↔ Met
      Office three-hourly), daily (MGM ↔ Met Office), hourly (Met Office
      only — MGM has no hourly-resolution product).
- [x] **C3 — Met Office treated correctly on Compare.**
      The observation section states explicitly that Met Office Global Spot
      publishes no Istanbul observation product; no measured Met Office value
      is implied anywhere.
- [x] **C4 — Unit-aware difference calculation** via `UnitConverter.convert`,
      with the canonical unit labelled on the difference and the source unit
      shown under each provider value.
- [x] **C5 — Tests for the crosswalk** (16 tests in `ComparisonBuilderTest`).
- [x] **C6 — Correct the Met Office parameter catalogue.**
      Rewritten with the exact live-API keys (hourly 18 / three-hourly 21 /
      daily 41). Fixed the wrong names (`dayMaxTemp` → `dayMaxScreenTemperature`,
      `nightMinTemp` → `nightMinScreenTemperature`, `feelsLikeTemperature` →
      `feelsLikeTemp` for three-hourly, `dayMaxUVIndex` → `maxUvIndex`, etc.).

## Phase 7 — Missing functionality

- [x] **F1 — Sunrise / sunset on Home** (final spec §3).
      New `data/solar/SunTimes.kt` computes sunrise, sunset, solar noon and
      daylight length with the NOAA sunrise equation for 41.0082 / 28.9784.
      The Home card states explicitly that the values are **computed locally**
      and are *not* provider observations (neither provider publishes them).
      Validated against an independent ephemeris to within 2 minutes
      (`SunTimesTest`, 8 tests).
- [x] **F2 — Chart library.** The specification allows *"a maintained chart
      library **or** custom Compose charts"*. The app uses custom Jetpack
      Compose `Canvas` charts (`ui/screens/Charts.kt`) so they follow the app
      theme exactly and add no dependency; the unused Vico declaration was
      removed. The deviation is recorded in `README.md`.
- [x] **F3 — Historical charts with real series.**
      `WeatherRepository.observeObservations("MGM", 500)` feeds
      `WeatherDataReader.observationSeries(...)`, which plots only values the
      provider actually supplied (a missing reading contributes no point, never
      a zero). Five charts: temperature, humidity, wind speed, sea-level
      pressure and 24-hour precipitation.
- [x] **F4 — Forecast Change Analysis** now compares the two most recent
      stored versions of a product. New `data/repository/ForecastChangeBuilder.kt`
      reports, per valid time and parameter, the previous value, the current
      value and the movement; parameters that appeared or disappeared are
      reported as such rather than treated as a change to zero. Issue time and
      valid time stay distinct. 8 unit tests.
- [x] **F5 — Station selection UI.**
      Verified live: Istanbul has **one** MGM centre (merkezId 93401, Bakirkoy)
      and therefore one observation/daily source, but **three** stations publish
      an hourly forecast — 17060 `ISTANBUL`, 17061 `ISTANBUL_KUZEY` (Sariyer)
      and 17064 `ISTANBUL_ANADOLU` (Kartal). The Settings page lists all 45
      Istanbul stations (searchable, from MGM's own catalogue) and lets the user
      pick the hourly forecast station; the data source falls back to the
      provincial centre when the chosen station publishes nothing and records
      which station actually produced the run.
- [x] **F6 — Raw-response viewer.**
      The Collection History screen lists every stored HTTP response with its
      endpoint, status, timestamp, size and SHA-256 hash; tapping one expands
      the verbatim payload. Request metadata excludes credentials.
- [x] **F7 — Error/retry UX.**
      Home shows a per-provider status card with the last attempt, the error
      text and an actionable next step (missing/invalid DataHub key → add a key
      in Settings; HTTP 429 → quota note; network → offline note). Partial
      success is stated explicitly: the successful provider's data stays usable
      and nothing from the failed provider was stored.

## Phase 8 — Tests (spec §12)

- [x] MGM parser tests — sentinel handling, units, hourly/daily arrays, code table
- [x] Met Office parser tests — GeoJSON, null handling, no-whitelist capture, day/night split
- [x] `ComparisonBuilder` tests — comparability, valid-time alignment, no zero-fill, uncapped
- [x] `UnitConverter` tests — wind + pressure conversions
- [x] `ForecastChangeBuilder` tests — changed / unchanged / added / removed, valid-time grouping
- [x] `SunTimes` tests — published sunrise/sunset times, polar day/night
- [x] `CollectionScheduler` policy tests — interval clamping, unique work names
- [x] **T1 — Room DAO / database tests** (`androidTest/.../DatabaseDaoTest.kt`)
- [x] **T2 — Repository tests** (`androidTest/.../WeatherRepositoryTest.kt`)
- [x] **T3 — Scheduler tests** (`androidTest/.../CollectionSchedulerTest.kt`)
- [x] **T4 — ViewModel tests** (`androidTest/.../WeatherViewModelTest.kt`)
- [x] **T5 — UI/Compose tests** (`androidTest/.../ui/AppShellUiTest.kt`)
- [x] Migration test file written (`androidTest/.../DatabaseMigrationTest.kt`)
- [x] **T6 — Room schema export enabled.** `ksp { arg("room.schemaLocation", …) }`
      exports `app/schemas/…/WeatherDatabase/{1,2}.json`; the schemas are
      packaged as androidTest assets and `MigrationTestHelper` validates the
      real exported schema. The database is now **version 2** (v1 → v2 adds the
      free-form `provider_notes` column) with `MIGRATION_1_2` declared.
      *Requires a device/emulator to execute.*
- [x] **T7 — Unit test run is green.**
      2026-11-22: `./gradlew :app:testDebugUnitTest` → **53 tests, 0 failed**
      (`MetOfficeParserTest` 7, `MgmParserTest` 9, `ComparisonBuilderTest` 16,
      `ForecastChangeBuilderTest` 8, `SunTimesTest` 8, `CollectionSchedulerPolicyTest` 5).
      - Fixed the MGM epoch assertion (`2026-11-22T03:12:00Z` = **1795317120000**).
      - Rewrote `ComparisonBuilderTest` around the crosswalk; added coverage for
        wind and pressure unit conversion, the daily day-index keys, the
        three-hourly `feelsLikeTemp` key, text-only condition codes, and
        valid-time alignment.
      - Fixed the forecast-change grouping test (two parameters per valid time).
- [!] **T8 — Instrumented tests have not been executed.** They compile
      (`compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL) but no device or
      emulator is available in this environment, so they still need one run on
      real hardware.

## Phase 9 — Documentation (spec §11)

- [x] **D1 — `README.md`** — architecture, build instructions (desktop + Termux),
      features, the 12 screens, the comparison engine and its crosswalk,
      the data model, verified limitations, attribution/licensing, project
      layout, and the manual verification checklist.
- [x] **D2 — `docs/API_SETUP.md`** — how to get a Met Office DataHub key, the
      360 calls/day quota, the HTTP 401 / 403 / 429 behaviour and exactly what
      the app does when the key is missing or invalid; MGM needs no key.
- [x] **D3 — `docs/MGM_NOTES.md`** — endpoint list, required headers, verified
      units (including the km/h proof), sentinel rules, the official condition
      code table, the station findings (45 Istanbul stations, 3 with hourly
      forecasts, 1 centre), and the unofficial-backend caveat.
- [x] **D4 — `docs/FIELD_MATRIX.md`** — per-parameter table: provider, product,
      unit, nullable?, comparable?, canonical unit, notes. Covers every MGM
      field and all 80 Met Office parameters.
- [x] **D5 — `docs/NOT_IMPLEMENTABLE.md`** — MGM warning feed, MGM true
      3-hourly product, Met Office Istanbul observations, sunrise/sunset as a
      computation, the historical archive, and per-station observations —
      each with the requests that were tested and what the app does instead.
- [x] **D6 — `docs/TESTING.md`** — how to run the unit and instrumentation
      suites, what each test covers, the Room schema-export setup, and a
      step-by-step manual verification checklist.

## Phase 10 — Build artifacts

- [x] **B1 — Debug APK** — `./gradlew :app:assembleDebug` →
      `dist/istanbul-weather-intelligence-debug.apk` (18.6 MB).
- [x] **B2 — Release APK** — `./gradlew :app:assembleRelease` →
      `dist/istanbul-weather-intelligence-release.apk` (1.8 MB, R8 minified and
      resource-shrunk). Signed with the local `debug.keystore` (generated with
      `keytool`; alias `androiddebugkey`, store/key password `android`) so it
      installs without extra setup. Verified with `apksigner verify` (v2
      scheme). **Replace the keystore before distributing.**
- [ ] **B3 — Verify on a device/emulator** — the checklist in
      `docs/TESTING.md` §3 still needs one pass on real hardware. No device or
      emulator was available in this environment.
- [x] **B4 — Release R8/ProGuard rules** — `app/proguard-rules.pro` keeps the
      Room entities and generated implementations, the kotlinx-serialization
      classes and their serializers, the Retrofit/OkHttp surface, the
      WorkManager worker and the Compose metadata, and strips verbose logging
      from release builds. The release build succeeds with R8 enabled.

### Convenience

`./build.sh [debug|release|all]` builds both variants and copies the signed
APKs to `dist/`. It sets the Termux `aapt2` override automatically.

---

## How to follow this

1. Work strictly top-to-bottom within a phase; phases are dependency-ordered.
2. **C1 is the blocker** — until cross-provider mapping exists, the Compare page
   (the app's headline feature) shows no differences at all.
3. After every change: `./gradlew :app:compileDebugKotlin --no-daemon`.
4. Before calling anything done: `./gradlew :app:testDebugUnitTest --no-daemon`.
5. Tick the box in this file in the same commit as the change.

### Build command (Termux)

```sh
cd ~/istanbul-weather-intelligence
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-21-openjdk
export ANDROID_HOME=/data/data/com.termux/files/usr/tmp/opencode/android-sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
export GRADLE_USER_HOME=$HOME/.gradle
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :app:compileDebugKotlin --no-daemon
```

### Verified non-goals (do not "fix" these)

- MGM's `tahminler/saatlik` returns **3-hour steps**. It is labelled "MGM hourly
  product — 3-hour resolution" in the UI. Do not fabricate hourly MGM data.
- MGM has **no warning endpoint** and **no three-hourly product**. Both are
  rendered as "not available", never invented.
- Met Office Global Spot provides **forecasts only** — it is never presented as
  an Istanbul observation.
- Missing values are **null**, never `0`. This is enforced by the parsers and the
  comparison builder.
