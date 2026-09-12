"""Opt-in source correction counts; tracker updates are not proof of visible improvement."""
import json
import re
import sys
from pathlib import Path

RECORD = re.compile(r"SOURCE_TRACK v=1 id=(\d+) enabled=(true|false) corrected=(\d+) extraDy=(-?\d+)")


def analyze(text):
    raw = 0
    rows = []
    for line in text.splitlines():
        if "SOURCE_TRACK " not in line:
            continue
        raw += 1
        match = RECORD.fullmatch(line[line.index("SOURCE_TRACK "):].strip())
        if match is None:
            continue
        identity, enabled, corrected, dy = match.groups()
        corrected, dy = int(corrected), int(dy)
        if (enabled == "false" and corrected != 0) or ((corrected == 0) != (dy == 0)):
            continue
        rows.append(dict(id=int(identity), enabled=enabled == "true", corrected=corrected, extraDy=dy))
    return dict(rawRecords=raw, parsedRecords=len(rows), complete=raw > 0 and raw == len(rows),
                correctedUpdates=sum(row["corrected"] > 0 for row in rows),
                correctedTracks=sum(row["corrected"] for row in rows),
                maxAbsExtraDy=max((abs(row["extraDy"]) for row in rows), default=0),
                visualAcceptance=False, records=rows)


if __name__ == "__main__":
    result = analyze(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))
    print(json.dumps(result, indent=2))
    sys.exit(0 if result["complete"] else 1)
