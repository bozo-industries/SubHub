"""Face-only source/tracker coordinate diagnostics; no absolute image or display-time truth."""
import json
import re
import statistics
import sys
from pathlib import Path

RECORD = re.compile(r"FACE_GEOMETRY v=1 id=(\d+) source=(\d+)x(\d+) viewport=(\d+)x(\d+) "
                    r"cameras=(-?\d+(?:,-?\d+){5}) obsTotal=(\d+) obsEncoded=(\d+) observations=([\d,|\-]+) "
                    r"tracksTotal=(\d+) tracksEncoded=(\d+) tracks=([\d,|\-]+)")


def tuples(text, length):
    if text == "-":
        return []
    rows = [list(map(int, row.split(","))) for row in text.split("|")]
    if any(len(row) != length for row in rows):
        raise ValueError("Invalid tuple")
    return rows


def analyze(text):
    raw = parsed = truncated = 0
    differences, fresh_differences, rows = [], [], []
    for line in text.splitlines():
        if "FACE_GEOMETRY " not in line:
            continue
        raw += 1
        match = RECORD.fullmatch(line[line.index("FACE_GEOMETRY "):].strip())
        if not match:
            continue
        identity, width, height, viewport_width, viewport_height = map(int, match.groups()[:5])
        cameras = list(map(int, match[6].split(",")))
        total_obs, encoded_obs, total_tracks, encoded_tracks = map(int, (match[7], match[8], match[10], match[11]))
        try:
            obs, tracks = tuples(match[9], 6), tuples(match[12], 12)
        except ValueError:
            continue
        if min(width, height, viewport_width, viewport_height) <= 0:
            continue
        if len(obs) != encoded_obs or len(tracks) != encoded_tracks or max(encoded_obs, encoded_tracks) > 8:
            continue
        if total_obs < encoded_obs or total_tracks < encoded_tracks:
            continue
        if any(o[0] not in (1, 2) or not 0 <= o[1] <= 4 or min(o[4:]) < 0 for o in obs):
            continue
        if any(t[0] < 1 or t[1] not in (1, 2) or t[2] < 0 or not 0 <= t[3] <= 4
               or min(t[6:8] + t[10:12]) < 0 for t in tracks):
            continue
        parsed += 1
        if total_obs != encoded_obs or total_tracks != encoded_tracks:
            truncated += 1
        for track in tracks:
            difference = (track[9] + track[11] / 2) - (track[5] + track[7] / 2)
            differences.append(abs(difference))
            if track[2] == 0 and track[3] == 0:
                fresh_differences.append(abs(difference))
        rows.append(dict(id=identity, source=[width, height], viewport=[viewport_width, viewport_height],
                         cameras=cameras, observations=obs, tracks=tracks))
    return dict(rawRecords=raw, parsedRecords=parsed, truncatedRecords=truncated,
                complete=raw > 0 and raw == parsed and truncated == 0,
                freshTracks=len(fresh_differences),
                freshRawToFilteredYMedianPx=statistics.median(fresh_differences) if fresh_differences else None,
                freshRawToFilteredYMaxPx=max(fresh_differences) if fresh_differences else None,
                allRawToFilteredYMaxPx=max(differences) if differences else None,
                multipleFaceTrackFrames=sum(len(row["tracks"]) > 1 for row in rows),
                pixelTimeKnown=False, absoluteAlignmentMeasured=False, records=rows)


if __name__ == "__main__":
    result = analyze(Path(sys.argv[1]).read_text(encoding="utf-8-sig"))
    print(json.dumps(result, indent=2))
    sys.exit(0 if result["complete"] else 1)
