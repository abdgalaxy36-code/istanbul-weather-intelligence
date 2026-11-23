# Field matrix

Every field the app can show, per provider and per product, with the unit as
**supplied by the provider**, whether it can be `null`, whether it needs a
subscription, and how it maps into the cross-provider comparison.

Legend:

- **Nullable** — the provider can legitimately omit the value. The app stores
  `null`, never `0`.
- **Comparable** — the crosswalk pairs this field with a field from the other
  provider, so a real difference can be calculated.
- **Canonical unit** — the unit the *difference* is expressed in. The stored
  source value is never modified; the source unit is shown under each value.

---

## 1. MGM — current observation

Source: `GET /web/sondurumlar?merkezid=93401` (Istanbul centre, Bakırköy).

| MGM key | Label | Unit | Nullable | Comparable | Canonical unit | Notes |
|---|---|---|---|---|---|---|
| `sicaklik` | Temperature | °C | yes (sentinel) | ✅ → `screenTemperature` | °C | |
| `hissedilenSicaklik` | Feels-like temperature | °C | yes | ✅ → `feelsLikeTemperature` | °C | |
| `nem` | Relative humidity | % | yes | ✅ → `screenRelativeHumidity` | % | |
| `aktuelBasinc` | Pressure (station) | hPa | yes | ❌ | — | Station pressure ≠ mean sea-level pressure. Shown under "Pressure". |
| `denizeIndirgenmisBasinc` | Pressure (sea level) | hPa | yes | ✅ → `mslp` | hPa | Met Office is Pa; converted. |
| `gorus` | Visibility | m | yes | ✅ → `visibility` | m | |
| `ruzgarHiz` | Wind speed | km/h | yes | ✅ → `windSpeed10m` | km/h | Met Office is m/s; converted. |
| `ruzgarYon` | Wind direction | degrees | yes | ✅ → `windDirectionFrom10m` | degrees | |
| `kapalilik` | Cloud cover | oktas | yes | ❌ | — | Met Office publishes no cloud-cover field in Global Spot. |
| `yagis00Now` | Precipitation (current) | mm | yes | ❌ | — | |
| `yagis10Dk` | Precipitation (10 min) | mm | yes | ❌ | — | Often `-9999` → `null`. |
| `yagis1Saat` | Precipitation (1 hour) | mm | yes | ❌ | — | |
| `yagis6Saat` | Precipitation (6 hours) | mm | yes | ❌ | — | |
| `yagis12Saat` | Precipitation (12 hours) | mm | yes | ❌ | — | |
| `yagis24Saat` | Precipitation (24 hours) | mm | yes | ❌ | — | Used by the Historical chart. |
| `denizSicaklik` | Sea temperature | °C | yes | ❌ | — | No Met Office equivalent in Global Spot. |
| `karYukseklik` | Snow depth | cm | yes | ❌ | — | |
| `hadiseKodu` | Weather code | — | yes | ❌ (text) | — | MGM codes (`CB`, `HY`, …). Shown beside the Met Office code, **no difference computed**. |
| `hadiseAdi` | Condition (decoded) | — | yes | ❌ (text) | — | Derived label from MGM's own table. |
| `veriZamani` | Observation time (UTC) | ISO-8601 | no | ❌ | — | Timestamp. |
| `denizVeriZamani` | Sea-data time (UTC) | ISO-8601 | yes | ❌ | — | Timestamp. |
| `rasatMetar` | METAR observation | — | yes | ❌ | — | Raw station report, kept verbatim. |
| `rasatTaf` | TAF forecast | — | yes | ❌ | — | Raw station report, kept verbatim. |
| `rasatSinoptik` | Synoptic observation | — | yes | ❌ | — | Raw station report, kept verbatim. |
| `istNo` | Station number | — | no | ❌ | — | Station provenance. |

---

## 2. MGM — "hourly" forecast (3-hour resolution)

Source: `GET /web/tahminler/saatlik?istno={17060 | 17061 | 17064}`.

| MGM key | Label | Unit | Nullable | Comparable | Canonical unit | Notes |
|---|---|---|---|---|---|---|
| `tarih` | Forecast valid time | ISO-8601 | no | ❌ | — | Used to align valid times. |
| `hadise` | Weather code | — | yes | ❌ (text) | — | |
| `hadiseAdi` | Condition (decoded) | — | yes | ❌ (text) | — | |
| `sicaklik` | Temperature | °C | yes | ✅ → MO three-hourly temperature | °C | |
| `hissedilenSicaklik` | Feels-like temperature | °C | yes | ✅ → `feelsLikeTemp` | °C | |
| `nem` | Relative humidity | % | yes | ✅ → `screenRelativeHumidity` | % | |
| `ruzgarYonu` | Wind direction | degrees | yes | ✅ → `windDirectionFrom10m` | degrees | |
| `ruzgarHizi` | Wind speed | km/h | yes | ✅ → `windSpeed10m` | km/h | |
| `maksimumRuzgarHizi` | Wind gust | km/h | yes | ✅ → `windGustSpeed10m` | km/h | |

> The steps are **3 hours apart**. This is MGM's hourly product at 3-hour
> resolution; there is no separate MGM 3-hourly product.

---

## 3. MGM — daily forecast

Source: `GET /web/tahminler/gunluk?istno=93401`. The payload uses parallel
`*Gun<n>` arrays; the parser captures **every** `*Gun<n>` key generically.

| MGM key | Label | Unit | Nullable | Comparable | Canonical unit | Notes |
|---|---|---|---|---|---|---|
| `tarihGun<n>` | Forecast date | ISO-8601 | no | ❌ | — | |
| `enDusukGun<n>` | Minimum temperature | °C | yes | ✅ → `nightMinScreenTemperature` | °C | |
| `enYuksekGun<n>` | Maximum temperature | °C | yes | ✅ → `dayMaxScreenTemperature` | °C | |
| `enDusukNemGun<n>` | Minimum humidity | % | yes | ✅ → `midnightRelativeHumidity` | % | |
| `enYuksekNemGun<n>` | Maximum humidity | % | yes | ✅ → `middayRelativeHumidity` | % | |
| `ruzgarHizGun<n>` | Wind speed | km/h | yes | ✅ → `midday10MWindSpeed` | km/h | |
| `ruzgarYonGun<n>` | Wind direction | degrees | yes | ✅ → `midday10MWindDirection` | degrees | |
| `hadiseGun<n>` | Weather code | — | yes | ❌ (text) | — | |
| `hadiseGun<n>Adi` | Condition (decoded) | — | yes | ❌ (text) | — | |
| *any other `*Gun<n>`* | Captured generically | as supplied | yes | ❌ | — | Unknown MGM daily fields are stored and displayed without a code change. |

---

## 4. Met Office — hourly (18 parameters)

Source: `GET /sitespecific/v0/point/hourly?latitude=&longitude=`, header
`apikey`. Coverage: 48 hours.

| Met Office key | Label | Unit | Nullable | Comparable | Canonical unit | Notes |
|---|---|---|---|---|---|---|
| `screenTemperature` | Air temperature at screen level | °C | yes | ✅ → MGM `sicaklik` | °C | |
| `feelsLikeTemperature` | Feels-like temperature | °C | yes | ✅ → `hissedilenSicaklik` | °C | |
| `maxScreenAirTemp` | Maximum air temperature | °C | yes | ❌ | — | |
| `minScreenAirTemp` | Minimum air temperature | °C | yes | ❌ | — | |
| `screenDewPointTemperature` | Screen dew point temperature | °C | yes | ❌ | — | |
| `screenRelativeHumidity` | Relative humidity at screen level | % | yes | ✅ → `nem` | % | |
| `mslp` | Mean sea level pressure | **Pa** | yes | ✅ → `denizeIndirgenmisBasinc` | **hPa** | Converted for the difference. |
| `visibility` | Visibility | m | yes | ✅ → `gorus` | m | |
| `windSpeed10m` | Wind speed at 10 m | **m/s** | yes | ✅ → `ruzgarHiz` / `ruzgarHizi` | **km/h** | Converted for the difference. |
| `windDirectionFrom10m` | Wind direction from 10 m | degrees | yes | ✅ → `ruzgarYon` / `ruzgarYonu` | degrees | |
| `windGustSpeed10m` | Wind gust speed at 10 m | m/s | yes | ✅ → `maksimumRuzgarHizi` | km/h | |
| `max10mWindGust` | Maximum 10 m wind gust | m/s | yes | ❌ | — | |
| `precipitationRate` | Precipitation rate | mm/hr | yes | ❌ | — | |
| `totalPrecipAmount` | Total precipitation amount | mm | yes | ❌ | — | |
| `totalSnowAmount` | Total snow amount | cm | yes | ❌ | — | |
| `probOfPrecipitation` | Probability of precipitation | % | yes | ❌ | — | |
| `significantWeatherCode` | Significant weather code | — | yes | ❌ (text) | — | Numeric 0–30, different vocabulary from MGM. |
| `uvIndex` | UV index | 1 | yes | ❌ | — | |

---

## 5. Met Office — three-hourly (21 parameters)

Source: `GET /sitespecific/v0/point/three-hourly`. Coverage: 168 hours.

| Met Office key | Label | Unit | Nullable | Comparable | Canonical unit | Notes |
|---|---|---|---|---|---|---|
| `feelsLikeTemp` | Feels-like temperature | °C | yes | ✅ → `hissedilenSicaklik` | °C | **Note:** this product uses `feelsLikeTemp`, not `feelsLikeTemperature`. |
| `probOfRain` | Probability of rain | % | yes | ❌ | — | |
| `probOfHeavyRain` | Probability of heavy rain | % | yes | ❌ | — | |
| `probOfSnow` | Probability of snow | % | yes | ❌ | — | |
| `probOfHeavySnow` | Probability of heavy snow | % | yes | ❌ | — | |
| `probOfHail` | Probability of hail | % | yes | ❌ | — | |
| `probOfSferics` | Probability of sferics (lightning) | % | yes | ❌ | — | |
| `screenTemperature` | Air temperature at screen level | °C | yes | ✅ → MGM `sicaklik` | °C | |
| `maxScreenAirTemp` | Maximum air temperature | °C | yes | ❌ | — | |
| `minScreenAirTemp` | Minimum air temperature | °C | yes | ❌ | — | |
| `screenRelativeHumidity` | Relative humidity at screen level | % | yes | ✅ → `nem` | % | |
| `mslp` | Mean sea level pressure | Pa | yes | ✅ → `denizeIndirgenmisBasinc` | hPa | |
| `visibility` | Visibility | m | yes | ✅ → `gorus` | m | |
| `windSpeed10m` | Wind speed at 10 m | m/s | yes | ✅ → `ruzgarHizi` | km/h | |
| `windDirectionFrom10m` | Wind direction from 10 m | degrees | yes | ✅ → `ruzgarYonu` | degrees | |
| `windGustSpeed10m` | Wind gust speed at 10 m | m/s | yes | ✅ → `maksimumRuzgarHizi` | km/h | |
| `max10mWindGust` | Maximum 10 m wind gust | m/s | yes | ❌ | — | |
| `totalPrecipAmount` | Total precipitation amount | mm | yes | ❌ | — | |
| `totalSnowAmount` | Total snow amount | cm | yes | ❌ | — | |
| `uvIndex` | UV index | 1 | yes | ❌ | — | |
| `significantWeatherCode` | Significant weather code | — | yes | ❌ (text) | — | |

This is the product MGM's "hourly" forecast is compared against, because both
use three-hour steps.

---

## 6. Met Office — daily (41 parameters)

Source: `GET /sitespecific/v0/point/daily`. Coverage: 7 days.

| Met Office key | Label | Unit | Nullable | Comparable | Canonical unit |
|---|---|---|---|---|---|
| `dayMaxScreenTemperature` | Day maximum screen temperature | °C | yes | ✅ → `enYuksekGun<n>` | °C |
| `nightMinScreenTemperature` | Night minimum screen temperature | °C | yes | ✅ → `enDusukGun<n>` | °C |
| `dayMaxFeelsLikeTemp` | Day maximum feels-like temperature | °C | yes | ❌ | — |
| `nightMinFeelsLikeTemp` | Night minimum feels-like temperature | °C | yes | ❌ | — |
| `dayUpperBoundMaxTemp` | Day max temperature upper bound | °C | yes | ❌ | — |
| `dayLowerBoundMaxTemp` | Day max temperature lower bound | °C | yes | ❌ | — |
| `nightUpperBoundMinTemp` | Night min temperature upper bound | °C | yes | ❌ | — |
| `nightLowerBoundMinTemp` | Night min temperature lower bound | °C | yes | ❌ | — |
| `dayUpperBoundMaxFeelsLikeTemp` | Day max feels-like upper bound | °C | yes | ❌ | — |
| `dayLowerBoundMaxFeelsLikeTemp` | Day max feels-like lower bound | °C | yes | ❌ | — |
| `nightUpperBoundMinFeelsLikeTemp` | Night min feels-like upper bound | °C | yes | ❌ | — |
| `nightLowerBoundMinFeelsLikeTemp` | Night min feels-like lower bound | °C | yes | ❌ | — |
| `maxUvIndex` | Maximum UV index | 1 | yes | ❌ | — |
| `middayRelativeHumidity` | Midday relative humidity | % | yes | ✅ → `enYuksekNemGun<n>` | % |
| `midnightRelativeHumidity` | Midnight relative humidity | % | yes | ✅ → `enDusukNemGun<n>` | % |
| `middayMslp` | Midday mean sea level pressure | Pa | yes | ❌ | — |
| `midnightMslp` | Midnight mean sea level pressure | Pa | yes | ❌ | — |
| `middayVisibility` | Midday visibility | m | yes | ❌ | — |
| `midnightVisibility` | Midnight visibility | m | yes | ❌ | — |
| `midday10MWindSpeed` | Midday wind speed at 10 m | m/s | yes | ✅ → `ruzgarHizGun<n>` | km/h |
| `midday10MWindDirection` | Midday wind direction at 10 m | degrees | yes | ✅ → `ruzgarYonGun<n>` | degrees |
| `midday10MWindGust` | Midday wind gust at 10 m | m/s | yes | ❌ | — |
| `midnight10MWindSpeed` | Midnight wind speed at 10 m | m/s | yes | ❌ | — |
| `midnight10MWindDirection` | Midnight wind direction at 10 m | degrees | yes | ❌ | — |
| `midnight10MWindGust` | Midnight wind gust at 10 m | m/s | yes | ❌ | — |
| `daySignificantWeatherCode` | Day significant weather code | — | yes | ❌ (text) | — |
| `nightSignificantWeatherCode` | Night significant weather code | — | yes | ❌ (text) | — |
| `dayProbabilityOfPrecipitation` | Day probability of precipitation | % | yes | ❌ | — |
| `nightProbabilityOfPrecipitation` | Night probability of precipitation | % | yes | ❌ | — |
| `dayProbabilityOfRain` | Day probability of rain | % | yes | ❌ | — |
| `nightProbabilityOfRain` | Night probability of rain | % | yes | ❌ | — |
| `dayProbabilityOfHeavyRain` | Day probability of heavy rain | % | yes | ❌ | — |
| `nightProbabilityOfHeavyRain` | Night probability of heavy rain | % | yes | ❌ | — |
| `dayProbabilityOfSnow` | Day probability of snow | % | yes | ❌ | — |
| `nightProbabilityOfSnow` | Night probability of snow | % | yes | ❌ | — |
| `dayProbabilityOfHeavySnow` | Day probability of heavy snow | % | yes | ❌ | — |
| `nightProbabilityOfHeavySnow` | Night probability of heavy snow | % | yes | ❌ | — |
| `dayProbabilityOfHail` | Day probability of hail | % | yes | ❌ | — |
| `nightProbabilityOfHail` | Night probability of hail | % | yes | ❌ | — |
| `dayProbabilityOfSferics` | Day probability of sferics | % | yes | ❌ | — |
| `nightProbabilityOfSferics` | Night probability of sferics | % | yes | ❌ | — |

> The Met Office parser captures **every** parameter the API returns, not just
> the ones listed above. A parameter added to the API later is stored,
> displayed and included in the comparison automatically.

---

## 7. Provider metadata (not weather values)

| Provider | Field | Source | Notes |
|---|---|---|---|
| MGM | Station name, id, province, district, lat/long, elevation | `/web/istasyonlar` | Kept verbatim in `metadata_json`. 45 Istanbul stations. |
| MGM | Centre id, forecast station ids, centre note | `/web/merkezler` | Includes MGM's own note that the centre takes observations from Atatürk Airport. |
| MGM | Raw METAR / TAF / synoptic reports | `/web/sondurumlar` | Kept as text. |
| Met Office | Resolved location name | `features[0].properties.location.name` | The point the forecast grid actually resolved to. |
| Met Office | `requestPointDistance` | `features[0].properties.requestPointDistance` | Distance between the requested point and the grid point, in metres. |
| Met Office | `modelRunDate` | `features[0].properties.modelRunDate` | The forecast **issue** time. |
| Met Office | `elevation` | `features[0].properties.elevation` | Grid elevation. |

---

## 8. Field counts

| Provider | Product | Parameters |
|---|---|---|
| MGM | Observation | 24 captured (from a live payload) |
| MGM | Hourly (3-hour steps) | 8 captured + generic daily capture |
| MGM | Daily | 8 known + every other `*Gun<n>` captured generically |
| Met Office | Hourly | 18 |
| Met Office | Three-hourly | 21 |
| Met Office | Daily | 41 |
| **Met Office total** | | **80** |
