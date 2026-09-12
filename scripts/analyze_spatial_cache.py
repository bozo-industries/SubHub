"""Strict numeric-only spatial-cache diagnostics. Not a visual acceptance gate."""
import json
import re
import statistics
import sys
from pathlib import Path

FRAME = re.compile(r"SPATIAL_CACHE_FRAME id=(\d+) status=(BASELINE|REGISTERED|UNMATCHED|NO_MOTION_HINT|INVALID|STALE) known=(true|false) cpuUs=(\d+)")
QUERY = re.compile(r"SPATIAL_CACHE_QUERY id=(\d+) entries=(\d+) inserted=(\d+) candidates=(\d+)")
HOLD = re.compile(r"SPATIAL_CACHE_HOLD id=(\d+)")


def analyze(text):
    raw = parsed = 0
    frames, queries, holds = [], [], []
    for line in text.splitlines():
        if "SPATIAL_CACHE_" not in line:
            continue
        raw += 1
        record = line[line.index("SPATIAL_CACHE_"):].strip()
        frame, query = FRAME.fullmatch(record), QUERY.fullmatch(record)
        if frame:
            identity, status, known, cpu = frame.groups()
            if (known == "true") != (status in ("BASELINE", "REGISTERED")):
                continue
            frames.append(dict(id=int(identity), status=status, known=known == "true", cpuUs=int(cpu)))
        elif query:
            identity, entries, inserted, candidates = map(int, query.groups())
            if candidates > 24 or entries > 2048 or inserted > entries:
                continue
            queries.append(dict(id=identity, entries=entries, inserted=inserted, candidates=candidates))
        elif HOLD.fullmatch(record):
            holds.append(int(HOLD.fullmatch(record)[1]))
        else:
            continue
        parsed += 1
    cpu = [frame["cpuUs"] for frame in frames]
    return dict(rawRecords=raw, parsedRecords=parsed, complete=raw > 0 and raw == parsed,
                frames=len(frames), knownFrames=sum(frame["known"] for frame in frames),
                cpuMedianUs=statistics.median(cpu) if cpu else None, cpuMaxUs=max(cpu) if cpu else None,
                queries=len(queries), queriesWithRegions=sum(query["candidates"] > 0 for query in queries),
                inserted=sum(query["inserted"] for query in queries),
                maxEntries=max([query["entries"] for query in queries], default=0),
                holdEvents=len(holds), visualAcceptance=False)


if __name__ == "__main__":
    result = analyze(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))
    print(json.dumps(result, indent=2))
    sys.exit(0 if result["complete"] else 1)
