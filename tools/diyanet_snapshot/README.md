# namazvakti.mtopal.dev snapshot (16 Nov 2026)

Raw responses from https://namazvakti.mtopal.dev (Diyanet data, docs: https://namazvakti.mtopal.dev/docs.html), captured by a one-off GitHub Actions job. Used as test fixtures and as input for generating the Turkey district table. Do not edit by hand.

- `ulkeler.json` — `GET /ulkeler`
- `sehirler_2.json` — `GET /sehirler/2` (Türkiye, 81 provinces)
- `ilceler/{SehirID}.json` — `GET /ilceler/{SehirID}` for every province (869 districts)
- `vakitler/{IlceID}.json` — `GET /vakitler/{IlceID}`: 9541 İSTANBUL, 9206 ANKARA, 9560 İZMİR (each 16.11.2026 → 31.12.2026, 46 days); `query_9206.json` is `GET /vakitler?ilce=9206`
- `error_unknown_district.*` — `GET /vakitler/1` returns HTTP **502** with a plain-text Cloudflare body (not the documented 404 JSON)
- `error_422.*` — `GET /vakitler` without id: HTTP 422, `{"detail":"ilce parametresi gerekli."}`
- `sample_headers.txt` — response headers (no Cache-Control/ETag; `Access-Control-Allow-Origin: *`)

Observed quirks:
- All IDs are JSON strings (`"SehirID": "539"`).
- `IlceAdiEn` is unreliable: Uşak has two districts with `IlceAdiEn` `USAK` (ULUBEY 17909 and UŞAK 9919); Kahramanmaraş's `IlceAdiEn` is `KAHRAMANMARAŞ`. Match on `IlceAdi` after Turkish-aware normalization.
- Every province's central district has the same `IlceAdi` as the province (e.g. İSTANBUL 9541, ANKARA 9206).
- `/vakitler` returns today through 31 December only; no past days, no next year.
