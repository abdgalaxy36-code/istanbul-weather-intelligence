# API setup

The app talks to two providers. Only one of them needs credentials.

| Provider | Credentials | Base URL |
|---|---|---|
| MGM (Turkish State Met Service) | **None** | `https://servis.mgm.gov.tr` |
| UK Met Office Weather DataHub | **API key required** | `https://data.hub.api.metoffice.gov.uk` |

---

## 1. Met Office Weather DataHub (required for the Met Office features)

### 1.1 Create an account

1. Go to <https://datahub.metoffice.gov.uk/>.
2. Create a DataHub account and sign in.
3. Open **Account → API keys** and create a new key.

### 1.2 Request the Global Spot dataset

1. Go to **Data → Global Spot**.
2. Request access. The free tier is granted automatically for Global Spot.
3. Wait for the access request to show as *active* — a key without the dataset
   attached returns HTTP 403 rather than data.

> Only **Global Spot** is needed. It is a global, point-based forecast product
> that works for any latitude/longitude, including Istanbul.

### 1.3 Enter the key in the app

1. Open the app and go to the **Settings** tab.
2. Scroll to **API configuration**.
3. Paste the key into **Paste API key**.
4. Press **Save key**.

The card then shows a green **Key configured** pill.

### 1.4 What the key is allowed to do

| | |
|---|---|
| Stored as | AES-GCM ciphertext, key held in the **Android Keystore** |
| Written to logs | **Never** |
| Included in exports | **Never** |
| Included in the APK | **Never** — it is entered by the user at runtime |
| Sent to | Only `data.hub.api.metoffice.gov.uk`, in the `apikey` header |

### 1.5 Quota

The free **Global Spot** plan allows **360 API calls per day**.

One collection cycle makes three calls (hourly, three-hourly, daily). With the
default 6-hour interval that is 4 cycles per day = 12 calls — far below the
limit. Even at the 15-minute minimum the app would make 288 calls per day,
still inside the quota.

The quota is per account, not per app, and resets daily.

### 1.6 Errors the app handles

| HTTP | What the server returns | What the app does |
|---|---|---|
| 401 | `{"error":"Invalid Credentials"}` | Marks the Met Office collection as failed with code `auth`, keeps the MGM data, and shows **"Met Office rejected the API key (HTTP 401). Re-enter a valid Weather DataHub key in Settings."** on the Home page. |
| 403 | Dataset not enabled for the key | Treated the same way as 401, with the server's own message shown. |
| 404 | Wrong endpoint / product name | Treated as a parse error; the raw response is stored for inspection. |
| 429 | Quota exhausted | Recorded as `http_429`, marked **retryable**, and the next scheduled collection tries again. The Home page explains the daily quota. |
| 5xx / network | — | Recorded as a retryable failure; the app keeps working offline with stored data. |

Failures are **per provider**. A Met Office failure never removes or blocks
MGM data.

### 1.7 The coordinates used

The default point is the centre of Istanbul:

```
latitude  = 41.0082
longitude = 28.9784
```

They are stored in DataStore and can be changed in Settings. The Met Office
request also passes `includeLocationName=true` so the response carries the
resolved location name.

---

## 2. MGM (no key)

MGM's public web service needs no account, no token and no key.

### 2.1 Required headers

The service rejects requests that do not look like they came from
`www.mgm.gov.tr`. The app sends all three of these on every request:

```http
Referer: https://www.mgm.gov.tr/
Origin: https://www.mgm.gov.tr
User-Agent: Mozilla/5.0 ... (a normal browser user agent)
```

Without them the service answers:

```json
{"error":"ServerError","message":"Not allowed by MGM"}
```

### 2.2 Endpoints used

```
GET /web/merkezler?il=istanbul        province centre + station ids
GET /web/sondurumlar?merkezid=93401    current observation
GET /web/tahminler/saatlik?istno=…     hourly forecast (3-hour steps)
GET /web/tahminler/gunluk?istno=93401  daily forecast
GET /web/istasyonlar                   station catalogue (1930 stations)
```

See [`MGM_NOTES.md`](MGM_NOTES.md) for the full details, the verified units and
the caveats.

---

## 3. Privacy

- The app requests **no location permission** and collects **no personal
  data**.
- The app declares `INTERNET` and `ACCESS_NETWORK_STATE`. The WorkManager
  library additionally contributes `WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED` and
  `FOREGROUND_SERVICE`, which it needs in order to run and reschedule the
  background collection.
- The only outbound requests are to the two provider base URLs.
- All collected weather data stays in the app's private Room database on the
  device. There is no backend, no analytics, no crash reporting and no
  telemetry.
- Exports are produced locally and leave the device only when the user shares
  them.

---

## 4. Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| "Met Office is enabled but no API key is saved" | No key entered | Settings → API configuration → paste → Save key |
| "Met Office rejected the API key (HTTP 401)" | Wrong key, or the dataset is not enabled for it | Create a new key, confirm Global Spot access is *active* |
| "MGM could not be reached" | No network | The app keeps working offline; the next scheduled collection retries |
| "MGM rate limited the request (HTTP 429)" | Too many requests | Wait; the next scheduled collection retries |
| Everything shows "not collected yet" | Automatic collection is off | Settings → Collection → **Automatic collection** on, then **Collect Now** |
| The background schedule never fires | Android delayed it | This is normal — Doze, battery optimisation and network conditions all delay WorkManager. Use **Collect Now**. |
