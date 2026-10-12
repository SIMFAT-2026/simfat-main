# Deviations and implementation notes

Append-only log for the study pre-registered in `PREREGISTRATION.md` v1.0
(commit `337c68f`, sha256 `541b9266…a4b4b`). Entries are numbered and dated
and are never edited or removed after they are committed. The final report
cites this file next to the pre-registration.

**Deviations** change something the pre-registration states.
**Implementation notes** fix details the pre-registration leaves open; they
are not deviations, but they are recorded so a reader can audit every choice.

## Deviations

### D1 — Deviations are logged here, not in PREREGISTRATION.md (2026-10-02)

- **Pre-registered:** Section 18 says a deviation "is recorded as a numbered,
  dated deviation entry in this log", meaning the pre-registration's own
  change log.
- **Done instead:** deviations are recorded in this separate append-only file.
- **Why:** editing `PREREGISTRATION.md` changes its sha256, so the hash in
  `PREREGISTRATION.lock.json` would no longer match and every study entry
  point would abort, as Section 17 requires. A separate file keeps the
  pre-registration and its lock frozen.
- **Effect on results:** none.

### D2 — ERA5 downloaded with one request per comuna (2026-10-02)

- **Pre-registered:** Section 8.1 says the Open-Meteo archive is read "one call
  per comuna per season window".
- **Done instead:** one request per comuna covering 2016-08-31 to 2025-04-30
  (`scripts/download_era5.py`).
- **Why:** it cuts the number of requests ninefold. Open-Meteo's free tier
  already returned HTTP 429 during the download.
- **Effect on results:** none. The FWI chain still restarts each season on
  Sep 1 of `s-1` with the pre-registered startup values
  (`season_fwi.run_chain`), and each season reads the same hourly values it
  would have received from a per-season request.

## Implementation notes

### N1 — ERA5 range ends on 2025-04-30 (2026-10-02)

The pre-registration fixes no download range. The download ends on
2025-04-30 so the pre-registered Nov 1 to Apr 30 sensitivity window is fully
covered by real data.

### N2 — FIRMS detections are dated in Chile local time (2026-10-02)

FIRMS `acq_date` and `acq_time` are UTC; the season windows are local calendar
dates. Each detection is converted to an America/Santiago local date before
the window check. This environment has no IANA tzdata, so the exact DST
transitions for 2016-2026 were read from the Windows time-zone database
("Pacific SA Standard Time") and hard-coded in
`firms_target.CHILE_DST_INTERVALS_UTC`. A weekday rule tried first was wrong:
Chile left DST in mid-May in 2016-2018 and started it on Sep 11 in 2022. Both
reviewers checked the table independently against Windows. Rebuilding the
target with the corrected table gave a byte-identical file, because S-NPP
overpasses fall around 06:00 and 18:00 UTC.

### N3 — One weather point per comuna (2026-10-02)

Each comuna's weather comes from one point inside its polygon: the vertex
average of its largest part if that lies inside the polygon (ray casting,
holes excluded), otherwise the nearest inside point on a 101x101 grid over
that part (`era5.representative_point`). All 86 points were verified to lie
inside their polygons.

### N4 — Detections outside the 86 comunas are dropped (2026-10-02)

Detections are assigned by point-in-polygon against the repository GeoJSON.
18,083 of the 208,890 archive rows fall outside every monitored comuna and
are dropped and counted, never assigned to the nearest comuna.

### N5 — A cross-check of the Fuego union was skipped (2026-10-02)

The planned cross-check of the 2013-2017 union against
`frequency_burned_2013_2017.tif` was skipped because that file has no
provenance entry in the manifest. The union method itself matched MapBiomas'
`accumulated_burned_v1` product in S1b5 (max difference 1.1e-16).
