# ADR-002: Agriculture weather (W8) and satellite NDVI (W9)

**Status:** PROPOSED. Nothing is built, no key is bought, no money is spent. Decisions needed are in section 9.
**Date:** 2026-10-04. **Follows:** ADR-001 (W1 to W7 and the W6 leftovers are built).
**Scope:** weather and satellite-derived vegetation data for the Agriculture module only.

ADR-001's ownership table says weather and NDVI come from *external providers*, as *observations and references*. This ADR picks the providers, and designs how they fit the module without breaking that rule.

---

## 1. What we are trying to give farmers

| Need | What it is | Phase |
|---|---|---|
| Know what is coming | 7-day forecast per farm (rain, temperature, wind) | W8a |
| Know what fell | Daily rainfall per farm, season-to-date totals; the farmer's own rain-gauge reading wins over a model | W8a |
| Link weather to crops | Rain and heat accumulated since a crop cycle's planting date | W8b |
| Be warned | Frost, heat or heavy rain in the next 48 hours, in the existing "Needs attention" list | W8b |
| See how a field is doing | NDVI trend per field across a crop cycle, compared with its own history | W9 |

**What NDVI is not.** It is a greenness indicator, not a diagnosis and not a yield forecast. Every screen says so, shows how much of the field was cloud-free on each date, and never fills gaps silently.

## 2. What is in the codebase today (verified)

- A **farm** has one nullable GPS point (`gps_latitude`, `gps_longitude`). That is enough for weather.
- A **production area (field) has no boundary at all**, only a name, type, size in hectares and soil type. NDVI needs a polygon per field, so **boundary capture is a prerequisite for W9**, not a detail of it.
- A crop cycle belongs to a production area (`production_area_id`, not null), so NDVI per crop cycle is possible once boundaries exist.
- **No PostGIS, no geometry library (JTS), no scheduler lock (ShedLock), no resilience or retry library.** Existing schedulers are plain `@Scheduled`. A second app instance would run every job twice.
- The one existing outbound integration (`BulkSmsSaService`) is `@ConditionalOnProperty`-gated, takes credentials through `@Value`, and uses a bare `new RestTemplate()` with **no timeouts**. New clients must not copy that.
- Frontend: `leaflet` and `react-leaflet` are installed. There is **no drawing plugin and no KML/GeoJSON parser**.

## 3. Research findings

Confidence key: **A** = read on the provider's own page this session. **B** = second-hand or a forum answer from provider staff. **C** = not verified, treat as a question.

### 3.1 Weather

| Provider | What I found | Conf. |
|---|---|---|
| **Open-Meteo** | Forecast plus historical archive from 1940, 30+ national models (ECMWF, DWD, NOAA, and others), hourly, 1 to 11 km resolution, no key needed on the free tier. **The free tier is non-commercial only** (600 calls/min, 5,000/hour, 10,000/day, 300,000/month, no uptime guarantee). Commercial use needs a paid plan with a key on a dedicated endpoint. Plans are priced on a monthly call budget: **Standard 1M calls, Professional 5M, Enterprise 50M+**. **Standard excludes the Historical Weather, Climate, Ensemble and Satellite Radiation APIs; Professional includes them.** Data is CC BY 4.0 (attribution required, commercial use allowed). Server code is AGPLv3 and can be self-hosted. Fixed monthly price, no overage charges, 99.9% uptime target on paid plans. A request for more than 10 variables or more than 2 weeks counts as more than one call. | A |
| Open-Meteo price | Open-Meteo's own blog says the Professional plan "starts at 99 Euro per month". Third-party pages say anything from $30 to €99. **The plan cards did not render on the pricing page, so the exact price must be confirmed at purchase.** Billing is in EUR through Stripe. | B |
| **AfriGIS Weather API** | Exposes South African Weather Service (SAWS) feeds: forecasts (daily and 6-hourly), measurements, lightning, storms, and official severe-weather warnings. Commercial, with a pilot of 50 credits/day for 60 days. No prices found. | B |
| **SAWS directly** | A public entity with "public good" services and "paid-for commercial services". I found no open API. Measured near-real-time data in South Africa sits with SAWS and the ARC station networks, which are institutional. | B |
| **ERA5** (Copernicus) | Global reanalysis from 1940, distributed under the Copernicus licence. Open-Meteo's historical archive is built on it. | B |
| NASA POWER, CHIRPS, TAMSAT | Free gridded rainfall and climate data, relevant to Africa. **I could not confirm their commercial-use terms.** | C |
| OpenWeather One Call, Tomorrow.io, Meteomatics, Visual Crossing | **Not evaluated.** | C |

### 3.2 Satellite vegetation (NDVI)

| Provider | What I found | Conf. |
|---|---|---|
| **Copernicus Data Space Ecosystem (CDSE)** | The EU's official Sentinel access. Includes the **Sentinel Hub Statistical API**: give it a polygon and a date range, get per-date statistics back (exactly an NDVI trend per field). Open to users worldwide. The FAQ says commercial services can be built on top of it. Sentinel data is available about 24 hours after sensing. **The programme is funded to end of 2028, with an optional extension to 2032.** Attribution required: "Contains modified Copernicus Sentinel data [year]". | A |
| CDSE free quotas | A "Copernicus General user" account gets about **10,000 requests and 10,000 processing units per month** (300 requests and 300 PU per minute), per CDSE support staff on their forum. Over quota, access is **slowed, not cut off**. Quotas reset monthly. For scale: one NDVI processing unit covers about 39 km² at 10 m. The free tier is described as "for individual use"; "large scale operations" go on commercial terms. | B |
| **Commercial Sentinel Hub plans** (Sinergise/Planet, also sold via CREODIAS) | Plans are sized by monthly PU and requests (for example a tier with 400,000 PU/month). **The trial account is explicitly not allowed for commercial use**, and the vendor says to use the commercial plan if it is used within a company. **No euro prices found; this needs a quote.** | A (terms), C (price) |
| **EOSDA API Connect** | Ready-made agriculture API: 15+ vegetation indices, field boundaries, weather, zoning maps. **Billed per request, not per hectare.** One field per statistics request, up to 365 days. Default 10 requests/minute on some endpoints. Free trial by request. No public prices. | A (features), C (price) |
| **Agromonitoring / OpenWeather Agro API** | Polygon-based NDVI and EVI statistics and history, from Landsat, Sentinel and MODIS. Polygons must be 1 to 3,000 ha. **Billing counts the total area of every polygon you create, even ones you never query.** Pricing page now says to contact OpenWeather. | A (features), C (price) |
| Planet (PlanetScope) | Commercial, per hectare under management, higher resolution (about 3 m, daily), per a second-hand mention and Sentinel Hub's pricing FAQ. **Likely far costlier than Sentinel-2, but I did not verify any price or evaluate it further.** | B |

### 3.3 Three traps found in the research

1. **"Free and keyless" weather is not available to us.** HandyFlow is a commercial product, so the Open-Meteo free tier does not apply. Free use is fine for evaluation and prototyping only (the pricing page says so); production needs a paid plan **before launch**.
2. **The cheap Open-Meteo tier is the wrong one.** Standard excludes the Historical API that season rainfall totals would use. Professional is the realistic tier. (Open question: the forecast API has a `past_days` option that may cover recent history on Standard. I recall up to 92 days but did not verify it; check before choosing.)
3. **One free CDSE account for every tenant is a fragile foundation.** The free quotas are per account, meant for individual use, and slow down when exceeded. A multi-tenant SaaS should plan for a commercial Sentinel Hub plan in production, and must read CDSE's terms and conditions (chapter 3) on whether pooling tenants under one free account is allowed. **I could not confirm that.**

## 4. Recommendation

### 4.1 Weather: Open-Meteo, on a paid commercial plan, behind a provider interface

Why: it is the only candidate I could verify end to end on pricing structure, licence, history and resolution; its call budget is generous for our scale (see 4.3); the data licence allows commercial use with attribution; and the API is plain HTTP with no SDK. The paid plan removes the non-commercial restriction and the missing uptime guarantee.

**Not recommended now:** self-hosting (AGPLv3 server plus heavy data ingestion is an operations burden we do not need), and SAWS or AfriGIS as the primary feed (paid, prices unknown, and weaker on history). **AfriGIS stays on the list for later**, specifically for official South African severe-weather warnings, which gridded models do not provide.

### 4.2 Satellite: build on Sentinel-2 via the Copernicus Sentinel Hub Statistical API, behind a provider interface

- **Pilot** on a free CDSE account (development and a few real farms, with the terms check done first).
- **Production** on a commercial Sentinel Hub plan (quote needed).
- **Keep EOSDA or Agromonitoring as fallback adapters** behind the same interface, in case the commercial Sentinel Hub quote is poor or the operating work is heavier than expected. Ask EOSDA for a quote in parallel; if it is close, buying could ship W9 sooner.

Why build rather than buy first: Sentinel-2 is the same underlying data the resellers use, so buying adds a margin and a lock-in without better data; per-polygon statistics is exactly one API call; and we keep control of cloud masking and provenance. The costs of building are real: evalscript maintenance, scene and cloud handling, and running the sync.

### 4.3 What it would cost to run (order of magnitude, from the figures above)

- **Weather:** a forecast refresh every 3 hours is 8 calls/day, about 240/month per farm, plus a daily observed-rain call, about 30/month. 1,000 farms is roughly 270,000 calls/month, well inside Professional's 5M. Expect on the order of **€99/month plus VAT, confirm at purchase**, not per tenant.
- **Satellite:** one Statistical API request per field per refresh. Refreshing every 5 days is about 6 requests/month per field, so the free quota's 10,000 requests/month is roughly **1,600 fields**, shared by all tenants. A production tier is a quote.
- These are estimates from the quoted limits. **The PU cost of a Statistical API request is not verified.**

## 5. Design

### 5.1 Principles

1. **Observations and references, not live calls in a page load.** Screens read from our database. Background jobs fetch from providers and write. A provider outage must never break a screen.
2. **Providers sit behind interfaces** (`AgWeatherProvider`, `AgNdviProvider`) with the adapter selected by configuration, so the vendor can change without touching screens, tables or reports.
3. **Store statistics, not imagery.** Rainfall, temperature, NDVI mean and spread, valid-pixel percentage, source, and fetch time. No satellite images are stored, which keeps storage small and licence exposure low.
4. **Say where every number came from.** Each row carries its source. Screens show the source, the resolution class (modelled grid, not a farm sensor), and the attribution the licence demands.
5. **The farmer's own reading beats the model.** A manual rain-gauge entry for a day takes precedence over the modelled value for that day. Both are stored.

### 5.2 Data model (new migrations, none applied yet)

```
ag_weather_daily        farm_id, obs_date, rain_mm, temp_max_c, temp_min_c, et0_mm,
                        source ('OPEN_METEO' | 'MANUAL'), fetched_at
                        unique (farm_id, obs_date, source)
ag_production_areas  +  boundary_geojson (jsonb), boundary_area_ha, boundary_source
                        ('DRAWN' | 'IMPORTED_KML' | 'IMPORTED_GEOJSON'), boundary_updated_at
ag_ndvi_observations    production_area_id, obs_date, ndvi_mean, ndvi_p10, ndvi_p90,
                        valid_pixel_pct, source, scene_ref, fetched_at
                        unique (production_area_id, obs_date, source)
ag_external_sync_state  provider, scope_key, last_success_at, last_error_at, last_error,
                        consecutive_failures
```

Forecasts are **not stored**: fetched on demand and cached for a short time, because they are only true for a few hours. No PostGIS: polygons are stored as GeoJSON in `jsonb`.

### 5.3 Backend

- **Ports and adapters inside the Agriculture module** (no new module yet): the interfaces in the application layer, the Open-Meteo and Sentinel Hub clients in an infrastructure package, each enabled by a property (`ag.weather.provider`, `ag.ndvi.provider`) like `sms.enabled`. Extract to a shared module later if another module (crop insurance, for example) needs the same data.
- **HTTP clients:** `RestClient` with a connect and read timeout, limited retry with backoff on 429 and 5xx, a per-provider call budget, and never on a request thread. Credentials from environment variables via properties, never in the repo.
- **Jobs:** a daily weather sync and a roughly 12-hourly NDVI sync, idempotent upserts on the unique keys above. **The codebase has no scheduler lock**, so before these run on more than one instance we need one (ShedLock, or a Postgres advisory lock). Open item in section 8.
- **Sync health:** `ag_external_sync_state` feeds a small status line on the screens ("Weather last updated 06:10; the provider has been failing since yesterday") and an admin view.
- **Boundary validation** (no geometry library today): valid closed polygon, no self-intersection, coordinates inside South Africa's bounding box, area within a sane multiple of the recorded hectares (warn, do not block), a maximum size. Adding JTS is the clean route; the alternative is hand-written checks. Needs a decision.
- **Permissions** reuse the existing ones: read weather and NDVI with `AGRICULTURE_READ`; enter rainfall and edit boundaries with `AGRICULTURE_MANAGE`. Neither is cost data, so neither needs `AGRICULTURE_FINANCE`.

### 5.4 Frontend

- **Weather** under Insights: 7-day forecast, last 30 days of rain as bars with manual entries marked, season-to-date total, attribution, and a clear nudge when the farm has no GPS point.
- **Field boundaries:** draw a polygon on the existing Leaflet map (needs a drawing plugin) and **import KML or GeoJSON** (farmers have KML from Google Earth; needs a parser). Show the drawn area next to the recorded hectares.
- **NDVI** on the crop cycle: a trend line over the cycle with cloud-gap markers and a "compared with your last seasons for this field" line. No absolute "good or bad" colour.
- **Print and CSV** for both, reusing the escaped print document and the injection-guarded CSV.

### 5.5 Alerts (W8b)

A frost, heat or heavy-rain forecast for the next 48 hours becomes a "Needs attention" item on the Dashboard, with per-farm thresholds. They are **modelled-forecast warnings, not official warnings**, and say so.

## 6. Phasing

| Phase | Delivers | Needs first | Effort |
|---|---|---|---|
| **W8a** | Provider port, Open-Meteo adapter, daily sync, rainfall table, manual rain entry, Weather screen, attribution | Decision 1; scheduler lock | M |
| **W8b** | Season-to-date and since-planting rain and heat, degree days, ET0, Dashboard rain KPI, forecast alerts | W8a | M |
| **W9a** | Field boundaries: draw, import KML/GeoJSON, validation, area check | Decision 4 (JTS) | M to L |
| **W9b** | Provider port, Sentinel Hub adapter, NDVI table and sync, crop-cycle NDVI trend, cloud handling | W9a; decision 2 | L |

W8 and W9a are independent and can be built in either order. W9b waits on boundaries.

## 7. Risks

| Risk | Mitigation |
|---|---|
| A provider changes terms or price | Provider interface; observations are stored, so history survives a switch |
| CDSE funding ends (2028, extendable to 2032) | Same interface; EOSDA and Agromonitoring adapters as fallback; stored statistics are ours |
| Free-tier quota exhausted by many tenants | Global call budget with a fair queue per tenant; commercial plan before launch; slowed access is degradation, not failure |
| Over-claiming accuracy | Modelled-grid label on every weather figure; NDVI labelled as an indicator with cloud percentage; no gap-filling |
| **POPIA / cross-border transfer** | Farm coordinates and field boundaries go to providers in the EU and North America. Send **rounded coordinates for weather** (2 decimals is about 1 km, finer than most models); NDVI needs the real polygon. Disclose in the privacy notice and get your privacy adviser's view. **This is a flag, not legal advice.** |
| Cost grows with farms and fields | Cost scales with farms and fields, not users; decide whether this is a priced add-on (decision 7) |
| Jobs run twice on multiple instances | Scheduler lock before first deploy of a sync job |
| Cloud cover in the South African rainy season leaves NDVI gaps | Show gaps honestly with valid-pixel percentage; never interpolate silently |

## 8. Things I could not verify (check before relying on them)

1. Open-Meteo's exact Professional price, and whether `past_days` lets Standard cover recent history.
2. CDSE terms and conditions (chapter 3) on pooling several tenants under one free account, and the actual quota numbers on the Quotas page (I only have a staff answer on the forum).
3. The processing-unit cost of a Statistical API request.
4. Commercial Sentinel Hub plan prices (quote needed), and EOSDA and Agromonitoring prices.
5. Commercial-use terms of NASA POWER, CHIRPS and TAMSAT.
6. AfriGIS prices and whether its warnings can be redistributed to our users.
7. How the platform runs schedulers across instances today.
8. Providers I did not evaluate: OpenWeather One Call, Tomorrow.io, Meteomatics, Visual Crossing.

## 9. Decisions needed

1. **Weather provider and budget.** Approve Open-Meteo Professional (about €99/month, EUR, card via Stripe, confirm the price) for production, with the free tier for development only? Or evaluate one of the providers I did not look at first?
2. **Satellite route.** Build on Copernicus (pilot free, production commercial) as recommended, or buy from EOSDA or Agromonitoring? Should I request quotes from Sentinel Hub (via CREODIAS) and EOSDA before we decide?
3. **CDSE terms.** Someone with authority should read CDSE's terms (chapter 3) on SaaS use of a shared free account.
4. **Geometry.** Add JTS for boundary validation, or hand-written checks? Add a Leaflet drawing plugin and a KML parser to the frontend?
5. **POPIA.** Is rounded-coordinate weather plus a privacy-notice update acceptable, and who signs it off?
6. **Scheduler lock.** ShedLock, or a Postgres advisory lock?
7. **Pricing.** Is weather and NDVI part of the Agriculture subscription or a priced add-on?
8. **Order.** W8a first (smallest, unblocks alerts), then W9a and W9b? Or boundaries first because NDVI needs the longest lead time?

## 10. Sources

- Open-Meteo pricing and terms: https://open-meteo.com/en/pricing ; plan wording on the Professional price: https://openmeteo.substack.com/p/single-runs-api
- Open-Meteo overview and licence: https://open-meteo.com/ ; https://github.com/open-meteo/open-meteo
- AfriGIS Weather (SAWS feeds): https://developers.afrigis.co.za/portfolio/weather-api/
- SAWS overview: https://www.weathersa.co.za/home/overview
- South African climate data sources: https://www.waterresearchobservatory.org/data-and-resources/climate-and-weather-data
- Copernicus Data Space Ecosystem: https://dataspace.copernicus.eu/about ; FAQ: https://documentation.dataspace.copernicus.eu/FAQ.html
- CDSE quotas (forum answers from support staff): https://forum.dataspace.copernicus.eu/t/openeo-account-restricted/694 ; https://forum.dataspace.copernicus.eu/t/insufficient-processing-units-or-requests-available-in-your-account-upgrade-account-or-acquire-additional-credits-code-access-insufficient-requests/1019
- Sentinel Hub plans and trial terms: https://www.sentinel-hub.com/faq/ ; https://webdev-fe.sinergise.com/sentinel-hub-webpage/pricing/ ; CREODIAS: https://creodias.eu/pricing/sh-pricing/
- EOSDA API: https://eos.com/agriculture-api/ ; https://doc.eos.com/docs/faq/
- Agromonitoring / OpenWeather Agro: https://agromonitoring.com/faq ; https://agromonitoring.com/price
