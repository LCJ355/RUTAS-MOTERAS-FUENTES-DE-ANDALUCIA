"""Attach Access id_fuente values to the generated FD records."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import pyodbc
from pyproj import Transformer


ROOT = Path(__file__).resolve().parents[1]
DATA_PATH = ROOT / "fuentes_data.js"
DB_PATH = ROOT.parent / "FUENTES-Access" / "FUENTES_Andalucia.accdb"


def compatible(source: object, exported: object) -> bool:
    source_text = "" if source is None else str(source)
    exported_text = "" if exported is None else str(exported)
    return len(source_text) == len(exported_text) and all(
        a == "\ufffd" or a == b for a, b in zip(source_text, exported_text)
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    args = parser.parse_args()

    connection = pyodbc.connect(
        r"DRIVER={Microsoft Access Driver (*.mdb, *.accdb)};DBQ=" + str(DB_PATH)
    )
    try:
        rows = connection.cursor().execute(
            """
            SELECT id_fuente, nombre, municipio, coordenada_x, coordenada_y, huso, acceso
            FROM Fuentes
            ORDER BY provincia, municipio, nombre
            """
        ).fetchall()
    finally:
        connection.close()

    transformers = {
        zone: Transformer.from_crs(f"EPSG:258{zone}", "EPSG:4326", always_xy=True)
        for zone in (29, 30, 31)
    }
    source_by_coords: dict[tuple[float, float], list[object]] = {}
    source_records: list[tuple[object, float, float]] = []
    for row in rows:
        if row.coordenada_x is None or row.coordenada_y is None:
            continue
        zone = int(row.huso) if row.huso else 30
        zone = zone if zone in transformers else 30
        longitude, latitude = transformers[zone].transform(
            float(row.coordenada_x), float(row.coordenada_y)
        )
        latitude, longitude = round(latitude, 6), round(longitude, 6)
        source_by_coords.setdefault((latitude, longitude), []).append(row)
        source_records.append((row, latitude, longitude))

    lines = DATA_PATH.read_text(encoding="utf-8").splitlines(keepends=True)
    record_indexes = [i for i, line in enumerate(lines) if line.startswith('{"p":') or line.startswith('{"id":')]
    source_count = sum(len(group) for group in source_by_coords.values())
    if source_count != len(record_indexes):
        raise SystemExit(f"Access/export count mismatch: {source_count} != {len(record_indexes)}")

    rewritten = list(lines)
    mismatches: list[str] = []
    used_ids: set[int] = set()
    for position, line_index in enumerate(record_indexes):
        record = json.loads(lines[line_index].rstrip(",\r\n"))
        candidates = [
            row
            for row in source_by_coords.get((record["la"], record["lo"]), [])
            if int(row.id_fuente) not in used_ids and compatible(row.municipio, record.get("p"))
        ]
        if not candidates:
            fallback = [
                (row, latitude, longitude)
                for row, latitude, longitude in source_records
                if int(row.id_fuente) not in used_ids
                and compatible(row.municipio, record.get("p"))
                and compatible(row.nombre or f"Fuente {row.id_fuente}", record.get("n"))
            ]
            if len(fallback) == 1:
                row, _latitude, _longitude = fallback[0]
                candidates = [row]
        name_matches = [
            row
            for row in candidates
            if compatible(row.nombre or f"Fuente {row.id_fuente}", record.get("n"))
        ]
        if name_matches:
            candidates = name_matches
        access_matches = [
            row
            for row in candidates
            if ("r" if row.acceso and "sin dificultad" in row.acceso.lower() else "o") == record.get("t")
        ]
        if access_matches:
            candidates = access_matches
        if len(candidates) != 1:
            mismatches.append(
                f"{position}: {len(candidates)} Access matches for "
                f"FD {record.get('p')!r}/{record.get('n')!r} at {record['la']},{record['lo']}"
            )
            continue
        row = candidates[0]
        used_ids.add(int(row.id_fuente))
        record = {"id": int(row.id_fuente), **{k: v for k, v in record.items() if k != "id"}}
        ending = "\r\n" if lines[line_index].endswith("\r\n") else "\n"
        rewritten[line_index] = json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "," + ending

    if mismatches:
        raise SystemExit("Identity validation failed:\n" + "\n".join(mismatches[:20]))

    print(f"Validated {len(rows)} stable source IDs")
    if args.write:
        DATA_PATH.write_text("".join(rewritten), encoding="utf-8", newline="")
        print(f"Updated {DATA_PATH}")


if __name__ == "__main__":
    main()
