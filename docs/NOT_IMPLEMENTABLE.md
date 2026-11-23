# Not implementable due to provider restrictions

Some things the specification asks for **cannot be built** with the two
providers as they actually exist. This file records exactly what is missing,
what was tested to establish that, and what the app does instead.

Nothing here is an implementation shortcut. In every case the app shows the
truth rather than a plausible-looking fabrication.

---

## 1. MGM meteorological warnings

**Requested:** *"Meteorological warnings — show warnings only when an
authorized provider feed supplies them."*

**Status: MGM publishes no warning feed on its public service.**

### What was tested

| Attempt | Result |
|---|---|
| `GET /web/tahminler/…/uyari` | HTTP 404 `{"error":"NotFoundError","message":"Sayfa Bulunamadı"}` |
| `GET /web/uyarilar?il=istanbul` | HTTP 404 |
| `GET /web/uyari` | HTTP 404 |
| `GET /web/tahminler/saatlik?istno=…` | 200 — forecast only, no warning field |
| `GET /web/sondurumlar?merkezid=…` | 200 — observation only, no warning field |

MGM's real warning product (*"Uyarlar"*, the orange/red alert banners on
`mgm.gov.tr`) is served through a different, authenticated/licensed channel
that is not part of the public endpoint set this app can use.

### What the app does

- The `weather_warnings` table exists and the repository, DAO, model and
  export path are all implemented and ready.
- The MGM adapter declares `"warnings"` in `unsupportedProducts`.
- The Warnings screen shows **"No warnings received"** with the explanation
  that MGM has no public warning feed and the Met Office Global Spot product
  does not include warnings for Istanbul.
- **No warning is ever invented.** No synthetic severity, no synthetic area.

---

## 2. A true MGM 3-hourly product

**Requested:** separate *hourly* and *3-hourly* forecast screens.

**Status: MGM has one forecast product, `tahminler/saatlik`, whose steps are
3 hours apart. There is no separate 3-hourly product.**

### What was tested

| Attempt | Result |
|---|---|
| `GET /web/tahminler/3saatlik?istno=17060` | HTTP 404 |
| `GET /web/tahminler/3saatlik` | HTTP 404 |
| `GET /web/tahminler/saatlik?istno=17060` | 200 — valid times 09:00, 12:00, 15:00, 18:00, 21:00 UTC (**3-hour steps**) |

### What the app does

- MGM's data is labelled **"MGM hourly product — 3-hour resolution"** wherever
  it appears.
- The **Three-hourly** screen shows the **Met Office three-hourly** product,
  which *is* a genuine three-hourly product.
- On the Compare page, MGM's "hourly" product is aligned against the Met
  Office **three-hourly** product, because both use three-hour steps and both
  are genuinely comparable on valid time. The Met Office **hourly** product is
  listed separately, labelled as having no MGM counterpart.

### Why this is not a data loss

Every MGM forecast row is still stored and displayed. The app simply refuses
to pretend that MGM publishes a product it does not.

---

## 3. Met Office Istanbul observations

**Requested:** a side-by-side comparison of the two providers' *current*
conditions.

**Status: Met Office Global Spot publishes forecast data only. There is no
Istanbul ground-station observation product.**

### What was tested

| Attempt | Result |
|---|---|
| `GET /sitespecific/v0/point/hourly` (with a valid key) | 200 — forecast for 48 h ahead, `modelRunDate` in the past but every `time` value is a **future** valid time |
| `GET /sitespecific/v0/point/three-hourly` | 200 — forecast |
| `GET /sitespecific/v0/point/daily` | 200 — forecast |
| `GET /sitespecific/v0/point/observation` | Not a documented endpoint of the Global Spot site-specific API |
| The 18/21/41 verified parameter lists | **Not one** is an observation — no `screenTemperature` "at 14:00 today", no station id, no observation time |

The Global Spot product is a **model forecast on a grid**, resolved to the
nearest point to the requested coordinates. It reports `requestPointDistance`
so the app can show how far the grid point is from the requested point.

### What the app does

- The Met Office adapter declares `"observations"` in `unsupportedProducts`.
- The Compare page's observation section states explicitly:

  > *The Met Office Global Spot product publishes forecasts only. It has no
  > Istanbul ground-station observation, so there is no measured Met Office
  > value to compare against the MGM observation.*

- `metOfficeObservation` is only ever populated from a genuine observation.
- The Home page labels the Met Office entry **"Forecast"**, never
  "Observation".
- The Compare page never computes a difference between an MGM observation and
  a Met Office forecast.

---

## 4. Sunrise and sunset as provider data

**Requested:** sunrise / sunset on the Home screen.

**Status: neither provider publishes sunrise or sunset.**

- The verified Met Office parameter lists (18 hourly / 21 three-hourly /
  41 daily) contain no such field.
- MGM's payloads contain no such field.

### What the app does

The app **computes** them locally with the NOAA sunrise equation
(`data/solar/SunTimes.kt`) for 41.0082 / 28.9784, and labels them clearly:

> *Computed locally for Istanbul (41.0082, 28.9784) using the NOAA sunrise
> equation. Neither MGM nor the Met Office publishes sunrise or sunset, so
> these are not provider observations.*

The calculation is validated against an independent published ephemeris to
within two minutes across November and the winter solstice
(`SunTimesTest`).

---

## 5. Historical archive

**Requested:** historical data browsing.

**Status: neither provider offers a free historical archive through these
endpoints.**

### What the app does

The app builds its own history from the moment it starts collecting, and says
so on the Historical screen:

> *Historical collection begins when this app starts collecting. No licensed
> archive is imported.*

Records are retained indefinitely, so the history grows with use. Forecast
versions are kept separately from observations, which is what makes the
Forecast Change Analysis possible.

---

## 6. Per-station MGM observations

**Requested:** *"Do not assume there is only one suitable station. Make station
selection explicit and configurable if multiple relevant stations exist."*

**Status: partially implementable — and the app implements the part that is
real.**

- Istanbul has **45** MGM stations in the catalogue and **three** of them
  publish an hourly forecast (17060 `ISTANBUL`, 17061 `ISTANBUL_KUZEY`,
  17064 `ISTANBUL_ANADOLU`).
- The app lists **all 45** Istanbul stations (searchable, with their MGM
  identifiers and metadata) and lets the user choose the **hourly forecast
  station**. The three stations publish genuinely different forecasts.
- **Observations and daily forecasts cannot be switched**, because Istanbul
  has exactly **one** MGM centre (`merkezId` 93401). The app states this
  plainly in the station picker instead of pretending otherwise.

---

## 7. Summary

| Requested feature | Implemented? | Why |
|---|---|---|
| MGM warnings | **No** | No public warning endpoint exists |
| MGM true 3-hourly product | **No** | MGM's hourly product *is* 3-hourly; there is no second one |
| Met Office Istanbul observations | **No** | Global Spot is forecast-only |
| Sunrise / sunset | **Yes, as a computation** | Not published by either provider; clearly labelled as computed |
| Historical archive | **Yes, self-collected** | No free archive endpoint; the app builds its own |
| MGM station selection | **Yes, for the forecast** | Three stations publish forecasts; one centre publishes observations |
