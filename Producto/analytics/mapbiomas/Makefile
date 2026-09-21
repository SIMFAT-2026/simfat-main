.PHONY: install test fire-report

install:
	python -m pip install -r requirements.txt

test:
	python -m pytest

# Regenerate data/coverage_report.csv and data/fire_stats_<year>_partial.jsonl
# from the real Fuego Collection 1 rasters. Set MB_FIRE_RASTER_DIR to point
# at a directory holding them, or place the files at %TEMP%\mb_fire_col1
# (see README's "Fuego Collection 1 (fire)" section).
fire-report:
	PYTHONPATH=src python scripts/generate_fire_report_2017.py
