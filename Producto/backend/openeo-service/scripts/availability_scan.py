from __future__ import annotations

import argparse
import csv
import json
from dataclasses import dataclass
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import httpx


@dataclass(frozen=True)
class Zone:
    name: str
    bbox: list[float]  # [west, south, east, north]


DEFAULT_ZONES: list[Zone] = [
    Zone("Arica", [-70.45, -18.65, -70.15, -18.35]),
    Zone("Antofagasta", [-70.55, -23.85, -70.20, -23.45]),
    Zone("LaSerena", [-71.40, -30.15, -70.95, -29.75]),
    Zone("Valparaiso", [-71.80, -33.25, -71.35, -32.90]),
    Zone("Santiago", [-70.95, -33.80, -70.35, -33.10]),
    Zone("Concepcion", [-73.25, -36.95, -72.75, -36.55]),
    Zone("Temuco", [-72.90, -39.00, -72.35, -38.45]),
    Zone("Valdivia", [-73.35, -39.95, -72.95, -39.55]),
    Zone("PuertoMontt", [-73.25, -41.65, -72.65, -41.20]),
    Zone("PuntaArenas", [-71.20, -53.35, -70.75, -52.95]),
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Escanea disponibilidad NDVI/NDMI por zonas y ventanas temporales usando openeo-service."
    )
    parser.add_argument("--base-url", default="http://127.0.0.1:8000", help="URL base del openeo-service")
    parser.add_argument("--start-date", required=True, help="Fecha inicio global (YYYY-MM-DD)")
    parser.add_argument("--end-date", required=True, help="Fecha fin global (YYYY-MM-DD)")
    parser.add_argument("--window-days", type=int, default=3, help="Tamanio de ventana temporal en dias")
    parser.add_argument(
        "--step-days",
        type=int,
        default=7,
        help="Salto entre ventanas en dias (7 = semanal)",
    )
    parser.add_argument(
        "--indicators",
        default="NDVI,NDMI",
        help="Lista separada por coma, por ejemplo NDVI,NDMI",
    )
    parser.add_argument(
        "--out-prefix",
        default="availability_scan",
        help="Prefijo de archivos de salida (JSON/CSV)",
    )
    parser.add_argument(
        "--zones",
        default="",
        help="Subconjunto de zonas separadas por coma (ej: Santiago,Valparaiso,Temuco). Vacio = todas.",
    )
    return parser.parse_args()


def daterange_windows(start: date, end: date, window_days: int, step_days: int) -> list[tuple[date, date]]:
    windows: list[tuple[date, date]] = []
    cursor = start
    max_delta = timedelta(days=max(window_days - 1, 0))
    step = timedelta(days=max(step_days, 1))
    while cursor <= end:
        window_end = cursor + max_delta
        if window_end > end:
            break
        windows.append((cursor, window_end))
        cursor += step
    return windows


def classify_response(status_code: int, body: dict[str, Any]) -> str:
    if status_code == 200:
        return "ok"
    message = ""
    if isinstance(body, dict):
        error_obj = body.get("error")
        if isinstance(error_obj, dict):
            message = str(error_obj.get("message", "")).lower()
        else:
            message = str(body).lower()
    if "no data available" in message:
        return "no_data"
    if status_code == 429:
        return "rate_limited"
    if status_code == 502 and "timed out" in message:
        return "timeout"
    if 500 <= status_code <= 599:
        return "server_error"
    if 400 <= status_code <= 499:
        return "client_error"
    return "unknown_error"


def post_indicator(
    client: httpx.Client,
    base_url: str,
    indicator: str,
    zone: Zone,
    start_d: date,
    end_d: date,
) -> tuple[int, dict[str, Any]]:
    payload = {
        "regionId": f"probe-{zone.name}",
        "aoi": {"type": "bbox", "coordinates": zone.bbox},
        "periodStart": start_d.isoformat(),
        "periodEnd": end_d.isoformat(),
    }
    response = client.post(
        f"{base_url}/openeo/indicators/latest/{indicator}",
        headers={"Content-Type": "application/json"},
        json=payload,
    )
    try:
        data = response.json()
    except ValueError:
        data = {"raw": response.text[:500]}
    return response.status_code, data


def main() -> None:
    args = parse_args()
    start_d = date.fromisoformat(args.start_date)
    end_d = date.fromisoformat(args.end_date)
    indicators = [i.strip().upper() for i in args.indicators.split(",") if i.strip()]
    selected_zone_names = {z.strip() for z in args.zones.split(",") if z.strip()}
    zones = DEFAULT_ZONES
    if selected_zone_names:
        zones = [z for z in DEFAULT_ZONES if z.name in selected_zone_names]
        if not zones:
            raise ValueError("No hay zonas validas en --zones")
    windows = daterange_windows(start_d, end_d, args.window_days, args.step_days)

    rows: list[dict[str, Any]] = []
    summary: dict[str, dict[str, dict[str, Any]]] = {}
    timeout = httpx.Timeout(connect=10.0, read=80.0, write=20.0, pool=10.0)

    with httpx.Client(timeout=timeout) as client:
        for zone in zones:
            for indicator in indicators:
                key = f"{zone.name}:{indicator}"
                summary[key] = {
                    "zone": zone.name,
                    "indicator": indicator,
                    "total": 0,
                    "ok": 0,
                    "no_data": 0,
                    "rate_limited": 0,
                    "timeout": 0,
                    "other_error": 0,
                }
                for w_start, w_end in windows:
                    status, body = post_indicator(
                        client=client,
                        base_url=args.base_url.rstrip("/"),
                        indicator=indicator,
                        zone=zone,
                        start_d=w_start,
                        end_d=w_end,
                    )
                    result = classify_response(status, body)
                    value = None
                    if isinstance(body, dict) and isinstance(body.get("value"), (int, float)):
                        value = float(body["value"])

                    message = ""
                    if isinstance(body, dict):
                        error_obj = body.get("error")
                        if isinstance(error_obj, dict):
                            message = str(error_obj.get("message", ""))

                    row = {
                        "zone": zone.name,
                        "indicator": indicator,
                        "period_start": w_start.isoformat(),
                        "period_end": w_end.isoformat(),
                        "status_code": status,
                        "result": result,
                        "value": value,
                        "error_message": message,
                    }
                    rows.append(row)

                    bucket = summary[key]
                    bucket["total"] += 1
                    if result == "ok":
                        bucket["ok"] += 1
                    elif result == "no_data":
                        bucket["no_data"] += 1
                    elif result == "rate_limited":
                        bucket["rate_limited"] += 1
                    elif result == "timeout":
                        bucket["timeout"] += 1
                    else:
                        bucket["other_error"] += 1

    summary_rows: list[dict[str, Any]] = []
    for item in summary.values():
        total = max(item["total"], 1)
        item["availability_ratio"] = round(item["ok"] / total, 4)
        summary_rows.append(item)

    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out_prefix = Path(f"{args.out_prefix}_{timestamp}")
    detail_json = out_prefix.with_suffix(".details.json")
    detail_csv = out_prefix.with_suffix(".details.csv")
    summary_json = out_prefix.with_suffix(".summary.json")
    summary_csv = out_prefix.with_suffix(".summary.csv")

    detail_json.write_text(json.dumps(rows, ensure_ascii=True, indent=2), encoding="utf-8")
    summary_json.write_text(json.dumps(summary_rows, ensure_ascii=True, indent=2), encoding="utf-8")

    with detail_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "zone",
                "indicator",
                "period_start",
                "period_end",
                "status_code",
                "result",
                "value",
                "error_message",
            ],
        )
        writer.writeheader()
        writer.writerows(rows)

    with summary_csv.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(
            f,
            fieldnames=[
                "zone",
                "indicator",
                "total",
                "ok",
                "no_data",
                "rate_limited",
                "timeout",
                "other_error",
                "availability_ratio",
            ],
        )
        writer.writeheader()
        writer.writerows(summary_rows)

    print(f"Escaneo completado. Ventanas evaluadas: {len(windows)}")
    print(f"Detalle JSON:  {detail_json}")
    print(f"Detalle CSV:   {detail_csv}")
    print(f"Resumen JSON:  {summary_json}")
    print(f"Resumen CSV:   {summary_csv}")


if __name__ == "__main__":
    main()
