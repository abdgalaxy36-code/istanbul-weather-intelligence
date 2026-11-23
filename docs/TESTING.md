# Testing

Two suites: **JVM unit tests** (no device needed) and **instrumented tests**
(a device or emulator is required).

---

## 1. Unit tests

```sh
./gradlew :app:testDebugUnitTest
```

HTML report: `app/build/reports/tests/testDebugUnitTest/index.html`
XML results: `app/build/test-results/testDebugUnitTest/`

### Current status — 53 tests, 0 failures

| Suite | Tests | What it covers |
|---|---|---|
| `MgmParserTest` | 9 | Sentinel (`-9999`) → `null`, °C / % / hPa / km/h units, hourly `tahmin` array, daily parallel `*Gun<n>` arrays, generic daily capture, METAR/TAF text, UTC epoch parsing |
| `MetOfficeParserTest` | 7 | GeoJSON `features[0].properties.timeSeries`, no-whitelist capture of unknown parameters, null handling, day/night parameter split, `modelRunDate` parsing, location name and `requestPointDistance` |
| `ComparisonBuilderTest` | 16 | Crosswalk pairing (`sicaklik` ↔ `screenTemperature`), daily day-index keys, three-hourly `feelsLikeTemp`, wind m/s → km/h, pressure Pa → hPa, text-only condition codes, no zero-fill, valid-time alignment, provider-specific keys |
| `ForecastChangeBuilderTest` | 8 | CHANGED / UNCHANGED / ADDED / REMOVED, null values never treated as zero, valid-time grouping and ordering, "no comparison without two runs" |
| `SunTimesTest` | 8 | Sunrise, sunset and daylight length against published times for 22 Nov / 30 Nov / 21 Dec 2026, UTC epoch output, polar day / polar night |
| `CollectionSchedulerPolicyTest` | 5 | Interval clamped to the 15-minute WorkManager minimum, default 6 h, stable unique work names |

### Running one suite or one test

```sh
./gradlew :app:testDebugUnitTest --tests '*ComparisonBuilderTest'
./gradlew :app:testDebugUnitTest --tests '*SunTimesTest.sunrise*'
```

---

## 2. Instrumented tests

```sh
./gradlew :app:connectedDebugAndroidTest
```

Requires a connected device or a running emulator. Reports:
`app/build/reports/androidTests/connected/`.

> **Status in this environment:** the instrumented tests **compile**
> (`./gradlew :app:compileDebugAndroidTestKotlin` → BUILD SUCCESSFUL) but have
> **not been executed**, because no device or emulator was available. They
> need one run on real hardware before release.

### Suites

| Suite | Tests | What it covers |
|---|---|---|
| `DatabaseDaoTest` | 9 | Provider round-trip, run status/counts, independent per-provider results, forecast values per valid time, null values stored as null (not zero), FK cascade delete, observation payload retention, forecast run history ordering, station selection |
| `WeatherRepositoryTest` | 8 | Raw payload + hash retention, normalized rows, null preservation, forecast values with units, independent provider results, failures recorded as errors, forecast versions never overwritten, latest-observation selection, Met Office has no observation until it supplies one |
| `CollectionSchedulerTest` | 7 | Exactly one periodic work, re-applying replaces instead of duplicating, the requested interval is applied, sub-minimum intervals are clamped, disabling auto-collection cancels the work, manual work uses its own unique name, next-run time is exposed |
| `WeatherViewModelTest` | 9 | Page selection, theme mode persisted, header toggle persisted separately, interval persisted and clamped, provider toggles reach the database, API key stored encrypted and clearable, swipe hint dismissal, export produces content and a file name |
| `AppShellUiTest` | 6 | The three nav buttons are always visible, the header exposes the theme toggle and Collect Now, there is no bottom navigation bar, Home shows no comparison card, the first-use swipe hint appears, Compare opens |
| `DatabaseMigrationTest` | 1 | v1 → v2 migration applied against the **exported** Room schema, the new column exists, existing rows survive |

### Room schema export

The exported schemas live in `app/schemas/com.istanbul.weatherintel.data.db.WeatherDatabase/`:

```
1.json   initial schema
2.json   adds provider_notes to weather_observations
```

They are produced by the Room compiler:

```kotlin
// app/build.gradle.kts
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
```

and are packaged as androidTest assets so `MigrationTestHelper` validates
against the real schema instead of a hand-written approximation:

```kotlin
sourceSets {
    getByName("androidTest") {
        assets.srcDir("$projectDir/schemas")
    }
}
```

**If you change an entity, rebuild and commit the new schema JSON.**

---

## 3. Manual verification checklist

Run this on a real device after installing the APK:

### First launch

- [ ] The app opens on **Home** with the three nav buttons (Home / Compare /
      Settings) visible under the header.
- [ ] Swiping left moves to Compare, then Settings; the order is
      Home → Compare → Settings.
- [ ] The first-use hint *"Swipe left or right to move between pages"*
      appears and can be dismissed; it does not come back.
- [ ] There is **no bottom navigation bar** and **no drawer**.
- [ ] Home shows **no MGM-vs-Met-Office comparison card**.

### MGM collection (no key needed)

- [ ] Tap the refresh icon → the Home page shows MGM's current temperature,
      humidity, pressure, wind and condition.
- [ ] The sunrise/sunset card is present and says the values are **computed**.
- [ ] The hourly, three-hourly and daily forecast cards show real periods.
- [ ] The provider status card shows MGM **OK**.

### Met Office collection (needs a key)

- [ ] Without a key the status card says *"Met Office is enabled but no API key
      is saved"* and MGM data is still fully usable.
- [ ] Add a key in Settings → the pill turns to **Key configured**.
- [ ] Collect → the status card shows Met Office **OK**, and the Compare page
      now lists real Met Office forecast values.
- [ ] With an invalid key the status card shows the 401 message and MGM data
      is unaffected.

### Compare

- [ ] Every MGM and Met Office parameter appears — nothing is dropped.
- [ ] Paired rows show both values, their source units and a difference with
      the canonical unit (wind in km/h, pressure in hPa).
- [ ] A missing value reads **"Not provided"**, never `0`.
- [ ] MGM-only and Met-Office-only fields appear under their own headings.
- [ ] The observation section states that Met Office publishes no Istanbul
      observations.

### Station selection

- [ ] Settings → **MGM station** lists the three forecast-capable Istanbul
      stations (17060, 17061, 17064) and all 45 Istanbul stations.
- [ ] Selecting 17061 changes the hourly forecast after the next collection,
      and the provenance shows the new station.

### Offline

- [ ] Enable airplane mode → the app still shows the last collected data on
      every screen.
- [ ] A collection attempt while offline records a `network` error and shows
      an actionable message; stored data is untouched.

### Theme and settings

- [ ] The sun/moon button in the header switches light/dark immediately and
      the choice survives an app restart.
- [ ] Settings → Appearance → Light / Dark / System also works.
- [ ] Changing the interval updates the schedule without creating a second
      periodic job.

### Export

- [ ] Export CSV and Export JSON both produce a file with UTC *and* Istanbul
      local timestamps, units, original provider values and provenance
      references.
- [ ] Neither export contains the API key.

---

## 4. Test philosophy

The tests exist to protect the promises this app makes:

1. **No fabrication.** A missing value stays `null`. A `-9999` sentinel never
   becomes `0`. A parameter without a counterpart stays visible instead of
   being dropped.
2. **No silent unit errors.** Wind and pressure conversions are asserted
   explicitly.
3. **No false pairings.** The crosswalk tests pin down which MGM and Met
   Office fields may be compared and which may not.
4. **Honest provenance.** The raw payload, its hash, the source unit and the
   provider's own identifiers are all preserved and tested.
5. **Independent providers.** A failure in one provider must never remove or
   block the other provider's data.
