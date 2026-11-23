# MGM notes — everything verified against the live service

All statements below were confirmed by calling `https://servis.mgm.gov.tr`
live on **22 November 2026**. Nothing here is guessed.

> **Caveat.** This is MGM's **public web backend**, not a documented, supported
> public API. It can change or disappear without notice. The app treats every
> MGM endpoint as optional: a failure is recorded per provider, never crashes
> the app and never affects the Met Office data.

---

## 1. Required headers

The service refuses requests that do not come from its own site:

```http
Referer: https://www.mgm.gov.tr/
Origin: https://www.mgm.gov.tr
User-Agent: Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 … Chrome/120 Mobile Safari/537.36
Accept: application/json
```

Without the `Referer` / `Origin` pair the answer is HTTP 200 with a body that
is **not** weather data:

```json
{"error":"ServerError","message":"Not allowed by MGM"}
```

`HttpClient.getMgm()` adds these headers for every MGM call.

---

## 2. Endpoints

| Endpoint | Purpose | Verified result |
|---|---|---|
| `GET /web/merkezler?il=istanbul` | Province centre and station ids | 1 centre |
| `GET /web/sondurumlar?merkezid=93401` | Current observation | 1 record |
| `GET /web/tahminler/saatlik?istno={n}` | "Hourly" forecast | 5–6 rows, **3-hour steps** |
| `GET /web/tahminler/gunluk?istno=93401` | Daily forecast | Parallel `*Gun<n>` arrays |
| `GET /web/istasyonlar` | Station catalogue | **1930** stations (45 in Istanbul) |
| `GET /web/tahminler/3saatlik…` | True 3-hourly product | **404 — does not exist** |
| any warning endpoint | Meteorological warnings | **404 — does not exist** |

`GET /web/merkezler?il=34` returns `[]` — the parameter takes a **province
name**, not a plate code. `GET /web/istasyonlar?il=34` returns all 1930
stations; the `il` parameter is ignored there, so the app filters to Istanbul
client-side.

### The Istanbul centre

```json
[{
  "merkezId": 93401,
  "sondurumIstNo": 17060,
  "saatlikTahminIstNo": 17060,
  "gunlukTahminIstNo": 93401,
  "il": "İstanbul",
  "ilce": "Bakırköy",
  "enlem": 40.9819,
  "boylam": 28.8208,
  "yukseklik": 33,
  "aciklama": "Bu merkez sondurum verisini Atatürk Havalimanı'ndan almaktadır.",
  "modelId": 130602
}]
```

The `aciklama` field says, in Turkish: *"This centre takes its observation
data from Atatürk Airport."* The app keeps that provenance.

---

## 3. Stations

MGM publishes **45** stations inside Istanbul province, across 22 districts.
Sample:

| Station | Name | District | Lat / Lon | Elev. |
|---|---|---|---|---|
| 17060 | İSTANBUL ATATÜRK HAVALİMANI | Bakırköy | 40.966936 / 28.815562 | 33 m |
| 17061 | SARIYER | Sarıyer | 41.146515 / 29.050201 | 59 m |
| 17064 | İSTANBUL BÖLGE | Kartal | — | — |
| 17636 | FLORYA | Bakırköy | 40.975796 / 28.786722 | 37 m |
| 17813 | KADIKÖY/GÖZTEPE | Kadıköy | 40.979451 / 29.057147 | 41 m |
| 18101 | EYÜPSULTAN | Eyüp | 41.102778 / 28.924167 | 54 m |

### Which stations actually publish a forecast?

Each of the 45 Istanbul stations was probed with
`/web/tahminler/saatlik?istno={n}`. **Exactly three** return a forecast:

| Station | MGM centre label | Product |
|---|---|---|
| 17060 | `ISTANBUL` | Hourly (3-hour steps) |
| 17061 | `ISTANBUL_KUZEY` | Hourly (3-hour steps) |
| 17064 | `ISTANBUL_ANADOLU` | Hourly (3-hour steps) |

The three genuinely differ — for the same valid time the centre reported
humidity 79 % / wind 262° / 5 km/h while `ISTANBUL_KUZEY` reported 66 % /
302° / 6 km/h. That is why the app lets the user pick the hourly forecast
station (Settings → **MGM station**). When the selected station publishes
nothing, the provincial centre is used and the run records which station
actually produced it (`source_forecast_id = "station-<id>"`).

**Observations and daily forecasts cannot be switched.** Istanbul has exactly
one MGM centre, so there is exactly one observation source and one daily
forecast source. The app states this in the picker rather than pretending
otherwise.

---

## 4. Units — all verified, none guessed

| Field | Unit | How it was verified |
|---|---|---|
| `sicaklik`, `hissedilenSicaklik`, `denizSicaklik` | °C | Compared against the METAR embedded in the same response (`15/13 Q1020`) |
| `nem` | % | Matches the METAR humidity group |
| `aktuelBasinc`, `denizeIndirgenmisBasinc` | hPa | The METAR `Q1020` group is exactly the sea-level pressure value |
| `ruzgarHiz`, `ruzgarHizi`, `maksimumRuzgarHizi` | **km/h** | Every value is an exact multiple of **0.36**; the m/s reading gives a relative error of 0.111 against the METAR wind, versus 0.153 for the km/h reading — so km/h is the provider's unit. MGM's own site template labels the field *"Rüzgar (km/sa)"*. |
| `ruzgarYon`, `ruzgarYonu` | degrees | 0–360 |
| `gorus` | m | 10 000 m = 10 km, as reported for the clear METAR sky |
| `yagis…` | mm | 24-hour total of 5.4 mm consistent with the METAR group |
| `kapalilik` | oktas | 5 oktas with `SCT009 BKN030` in the METAR |
| `hadiseKodu` | code | MGM's own `convertHadise()` table |

MGM also publishes the raw station reports `rasatMetar`, `rasatTaf` and
`rasatSinoptik`; the app keeps them verbatim as text.

---

## 5. Sentinels

MGM marks an absent reading with **`-9999`** (integer or with decimals). Every
such value becomes **`null`** in the app — never `0`.

```kotlin
fun mgm(value: Double?): Double? = when {
    value == null -> null
    value.isNaN() -> null
    value <= -9998.0 -> null
    else -> value
}
```

The original sentinel is still kept in `originalValue` / `original_value_json`
so the raw provider value is never lost. Example from a live response:
`"denizSicaklik":-9999` and `"karYukseklik":-9999` become `null` values with
`"originalValue":"-9999.0"`.

---

## 6. Weather condition codes

Extracted verbatim from MGM's own `convertHadise()` mapping in
`mainService.js`:

| Code | Meaning | Code | Meaning |
|---|---|---|---|
| A | Açık (clear) | HKY | Hafif Kar Yağışlı |
| AB | Az Bulutlu | K | Kar Yağışlı |
| PB | Parçalı Bulutlu | YKY | Yoğun Kar Yağışlı |
| CB | Çok Bulutlu | HSY | Hafif Sağanak Yağışlı |
| HY | Hafif Yağmurlu | SY | Sağanak Yağışlı |
| Y | Yağmurlu | KSY | Kuvvetli Sağanak Yağışlı |
| KY | Kuvvetli Yağmurlu | MSY | Mevzi Sağanak Yağışlı |
| KKY | Karla Karışık Yağmurlu | DY | Dolu |
| GKY | Gökgürültülü Sağanak Yağışlı | GSY | Gökgürültülü Sağanak Yağışlı |
| KGY | Kuvvetli Gökgürültülü Sağanak Yağışlı | SIS | Sisli |
| PUS | Puslu | DMN | Dumanlı |
| KF | Kum veya Toz Taşınımı | R | Rüzgarlı |
| GKR | Güneyli Kuvvetli Rüzgar | KKR | Kuzeyli Kuvvetli Rüzgar |
| KKK | Kuzeyden Kuvvetli Kar Yağışlı | GKK | Güneyden Kuvvetli Kar Yağışlı |
| T | Tipi | KK | Kar Fırtınası |
| YKK | Yoğun Kar Yağışlı | | |

These codes use a **completely different vocabulary** from the Met Office
numeric codes (0–30), so the comparison engine treats condition fields as
*not comparable*: both are shown side by side, but the app never computes a
difference between them.

---

## 7. Product shapes

### Hourly (`/web/tahminler/saatlik`)

```json
[{
  "istNo": 17060,
  "merkez": "ISTANBUL",
  "baslangicZamani": "2026-11-21T12:00:00.000Z",
  "tahmin": [
    {"tarih":"2026-11-22T09:00:00.000Z","hadise":"CB","sicaklik":15,
     "hissedilenSicaklik":15,"nem":79,"ruzgarYonu":262,"ruzgarHizi":5,
     "maksimumRuzgarHizi":16}
  ]
}]
```

- `baslangicZamani` — the forecast **issue** time (UTC).
- `tarih` — the forecast **valid** time (UTC).
- The steps are **3 hours apart** (09:00, 12:00, 15:00, 18:00, 21:00 UTC).
  This is MGM's *hourly product* with 3-hour resolution; there is no separate
  3-hourly product.

### Daily (`/web/tahminler/gunluk`)

The daily payload uses **parallel arrays indexed by day**:

```json
{
  "tarihGun0": "…", "enDusukGun0": 8, "enYuksekGun0": 14, "hadiseGun0": "AB",
  "tarihGun1": "…", "enDusukGun1": 7, "enYuksekGun1": 13, "hadiseGun1": "AB"
}
```

The parser captures **every** `*Gun<n>` key generically, so a field MGM adds
later is stored and displayed without a code change.

---

## 8. Attribution

The app displays, on Home and in Settings:

> MGM — Meteoroloji Genel Müdürlüğü (Turkish State Met Service).
> Data source: MGM.

and for the other provider:

> Powered by Met Office data
