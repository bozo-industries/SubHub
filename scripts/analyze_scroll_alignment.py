#!/usr/bin/env python3
"""Measure page/censor motion agreement directly from an Android screen recording.

Requires NumPy and OpenCV (``opencv-python-headless`` is sufficient). The analyzer deliberately
ignores the browser chrome, masks the neon censor
border before estimating page flow, and compares that flow with matched censor-border components.
It is a visual oracle for transit alignment; Accessibility timestamps are not involved.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

import cv2
import numpy as np


Box = tuple[float, float, float, float]


def percentile(values: list[float], q: float) -> float | None:
    if not values:
        return None
    return round(float(np.percentile(np.asarray(values), q)), 3)


def magenta_mask(frame: np.ndarray) -> np.ndarray:
    blue, green, red = cv2.split(frame)
    mask = (
        (red > 150)
        & (blue > 65)
        & (green < 145)
        & ((red.astype(np.int16) - green.astype(np.int16)) > 55)
    ).astype(np.uint8) * 255
    return cv2.dilate(mask, np.ones((5, 5), np.uint8), iterations=1)


def censor_boxes(mask: np.ndarray, top: int) -> list[Box]:
    boxes: list[Box] = []
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    for contour in contours:
        x, y, width, height = cv2.boundingRect(contour)
        if width < 28 or height < 28 or width * height < 1_400:
            continue
        boxes.append((x + width / 2.0, y + top + height / 2.0, width, height))
    return boxes


def match_box_pairs(
    previous: list[Box],
    current: list[Box],
    expected_dy: float,
) -> list[tuple[int, int]]:
    candidates: list[tuple[float, int, int]] = []
    for old_index, old in enumerate(previous):
        for new_index, new in enumerate(current):
            size_error = abs(np.log(max(1.0, new[2]) / max(1.0, old[2]))) + abs(
                np.log(max(1.0, new[3]) / max(1.0, old[3]))
            )
            dx = abs(new[0] - old[0])
            dy_error = abs((new[1] - old[1]) - expected_dy)
            if size_error > 0.55 or dx > max(90.0, old[2] * 0.55) or dy_error > 180.0:
                continue
            candidates.append((dy_error + dx * 0.7 + size_error * 80.0,
                               old_index, new_index))
    used_old: set[int] = set()
    used_new: set[int] = set()
    pairs: list[tuple[int, int]] = []
    for _, old_index, new_index in sorted(candidates):
        if old_index in used_old or new_index in used_new:
            continue
        used_old.add(old_index)
        used_new.add(new_index)
        pairs.append((old_index, new_index))
    return pairs


def match_box_motion(
    previous: list[Box],
    current: list[Box],
    expected_dy: float,
) -> list[float]:
    """Compatibility wrapper retained for callers of the original analyzer API."""
    return [current[new_index][1] - previous[old_index][1]
            for old_index, new_index in match_box_pairs(previous, current, expected_dy)]


def local_background_motion(
    previous_gray: np.ndarray,
    current_gray: np.ndarray,
    previous_mask: np.ndarray,
    current_mask: np.ndarray,
    previous_box: Box,
    box_delta: tuple[float, float],
    content_top: int,
    scale: float,
) -> dict | None:
    """Estimate motion from the unmasked background surrounding one censor.

    A single page-wide flow median is unreliable on lazy-loaded masonry grids: unrelated tiles
    can move, be replaced, or remain static in the same frame pair.  This searches only the
    local ring around a matched censor, using gradient-bearing pixels and a brightness-normalized
    error.  The search is bounded around the observed censor displacement, so a new/refined box
    with no matching local background is rejected instead of becoming a large false residual.
    """
    x, y, width, height = previous_box
    expected_dx, expected_dy = box_delta
    local_y = y - content_top
    # Use enough surrounding texture to survive a tile being lazily replaced.  This is in the
    # already-downscaled analyzer image; the bounded search is only invoked for disagreements.
    pad = 100
    image_height, image_width = previous_gray.shape[:2]
    left = max(0, int(round(x - width / 2.0)) - pad)
    top = max(0, int(round(local_y - height / 2.0)) - pad)
    right = min(image_width, int(round(x + width / 2.0)) + pad)
    bottom = min(image_height, int(round(local_y + height / 2.0)) + pad)
    if right <= left or bottom <= top:
        return None

    template = previous_gray[top:bottom, left:right].astype(np.float32)
    ring_mask = np.ones(template.shape, dtype=np.uint8) * 255
    box_left = int(round(x - width / 2.0)) - left - max(3, round(6 * scale))
    box_top = int(round(local_y - height / 2.0)) - top - max(3, round(6 * scale))
    box_right = int(round(x + width / 2.0)) - left + max(3, round(6 * scale))
    box_bottom = int(round(local_y + height / 2.0)) - top + max(3, round(6 * scale))
    ring_mask[max(0, box_top):min(ring_mask.shape[0], box_bottom),
              max(0, box_left):min(ring_mask.shape[1], box_right)] = 0
    ring_mask[previous_mask[top:bottom, left:right] > 0] = 0

    gradient_x = cv2.Sobel(template, cv2.CV_32F, 1, 0, ksize=3)
    gradient_y = cv2.Sobel(template, cv2.CV_32F, 0, 1, ksize=3)
    gradient = cv2.magnitude(gradient_x, gradient_y)
    ring_mask[gradient < 20.0] = 0
    points_y, points_x = np.where(ring_mask > 0)
    if len(points_x) < 48:
        return None
    if len(points_x) > 5000:
        sample_indices = np.linspace(0, len(points_x) - 1, 5000, dtype=np.int32)
        points_x = points_x[sample_indices]
        points_y = points_y[sample_indices]
    template_values = template[points_y, points_x]
    points_x = points_x + left
    points_y = points_y + top

    # Motion is normally vertical. Permit a modest horizontal correction for grid reflow while
    # keeping the search bounded enough that repeated faces/text do not become easy matches.
    search_x = 80
    search_y = 180
    step = 4
    candidate_dxs = range(round(expected_dx) - search_x,
                          round(expected_dx) + search_x + 1, step)
    candidate_dys = range(round(expected_dy) - search_y,
                          round(expected_dy) + search_y + 1, step)
    best: tuple[float, int, int, int] | None = None
    second_score = float("inf")
    for delta_y in candidate_dys:
        for delta_x in candidate_dxs:
            current_x = points_x + delta_x
            current_y = points_y + delta_y
            valid = ((current_x >= 0) & (current_x < image_width)
                     & (current_y >= 0) & (current_y < image_height))
            if not np.any(valid):
                continue
            valid_indices = np.flatnonzero(valid)
            if len(valid_indices) < 48:
                continue
            current_values = current_gray[current_y[valid_indices], current_x[valid_indices]].astype(np.float32)
            template_centered = template_values[valid_indices] - np.mean(template_values[valid_indices])
            current_centered = current_values - np.mean(current_values)
            score = float(np.mean(np.abs(template_centered - current_centered)))
            candidate = (score, delta_x, delta_y, len(valid_indices))
            if best is None or score < best[0]:
                if best is not None and abs(delta_x - best[1]) > step * 2:
                    second_score = min(second_score, best[0])
                elif best is not None and abs(delta_y - best[2]) > step * 2:
                    second_score = min(second_score, best[0])
                best = candidate
            elif (best is not None and
                  (abs(delta_x - best[1]) > step * 2 or abs(delta_y - best[2]) > step * 2)):
                second_score = min(second_score, score)

    if best is None:
        return None
    score, delta_x, delta_y, support = best
    if not np.isfinite(second_score):
        second_score = score
    peak_margin = (second_score - score) / max(1.0, second_score)
    # Low-texture or ambiguous matches are useful as diagnostics but must not affect the gate.
    if score > 50.0 or (support < 96 and peak_margin < 0.08):
        return {
            "dx": delta_x,
            "dy": delta_y,
            "support": support,
            "score": round(score, 3),
            "peakMargin": round(peak_margin, 3),
            "accepted": False,
        }
    return {
        "dx": delta_x,
        "dy": delta_y,
        "support": support,
        "score": round(score, 3),
        "peakMargin": round(peak_margin, 3),
        "accepted": True,
    }


def page_motion(
    previous_gray: np.ndarray,
    current_gray: np.ndarray,
    previous_mask: np.ndarray,
    current_mask: np.ndarray,
) -> tuple[float | None, int]:
    usable = cv2.bitwise_not(cv2.bitwise_or(previous_mask, current_mask))
    points = cv2.goodFeaturesToTrack(
        previous_gray,
        maxCorners=450,
        qualityLevel=0.015,
        minDistance=12,
        mask=usable,
        blockSize=5,
    )
    if points is None or len(points) < 12:
        return None, 0
    moved, status, _ = cv2.calcOpticalFlowPyrLK(
        previous_gray,
        current_gray,
        points,
        None,
        winSize=(25, 25),
        maxLevel=3,
        criteria=(cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 24, 0.01),
    )
    if moved is None or status is None:
        return None, 0
    valid = status.reshape(-1) == 1
    deltas = moved.reshape(-1, 2)[valid] - points.reshape(-1, 2)[valid]
    if len(deltas) < 10:
        return None, len(deltas)
    # Chrome image results predominantly translate vertically. Reject animated media and newly
    # loaded tiles using a robust median/MAD consensus rather than fitting a full homography.
    median_dx, median_dy = np.median(deltas, axis=0)
    residual = np.abs(deltas[:, 1] - median_dy) + np.abs(deltas[:, 0] - median_dx) * 0.35
    mad = max(1.0, float(np.median(residual)))
    inliers = deltas[residual <= max(3.0, mad * 3.5)]
    if len(inliers) < 8:
        return None, len(inliers)
    return float(np.median(inliers[:, 1])), len(inliers)


def analyze(path: Path, sample_every: int, top_fraction: float) -> dict:
    capture = cv2.VideoCapture(str(path))
    if not capture.isOpened():
        raise RuntimeError(f"Could not open video: {path}")
    fps = float(capture.get(cv2.CAP_PROP_FPS))
    width = int(capture.get(cv2.CAP_PROP_FRAME_WIDTH))
    height = int(capture.get(cv2.CAP_PROP_FRAME_HEIGHT))
    crop_top = max(1, round(height * top_fraction))
    scale = min(1.0, 480.0 / max(1, width))
    sample_width = max(1, round(width * scale))
    sample_height = max(1, round((height - crop_top) * scale))

    previous_gray = None
    previous_mask = None
    previous_boxes = None
    frame_index = -1
    motion_frames = 0
    visually_aligned_2px = 0
    visually_aligned_5px = 0
    signed_residuals: list[float] = []
    absolute_residuals: list[float] = []
    page_deltas: list[float] = []
    box_deltas: list[float] = []
    matched_boxes = 0
    samples: list[dict] = []
    corrected_signed_residuals: list[float] = []
    corrected_absolute_residuals: list[float] = []
    local_signed_residuals: list[float] = []
    local_absolute_residuals: list[float] = []
    local_trigger_frames = 0
    local_evidence_frames = 0
    local_evidence_matched_boxes = 0
    local_rejected_boxes = 0
    local_uncertain_frames = 0

    # Global flow is retained for backwards-compatible fields, but a disagreement this large
    # triggers local background evidence.  This catches grid reflow without making every frame
    # pay for a bounded template search.
    local_trigger_threshold = 12.0

    while True:
        ok, frame = capture.read()
        if not ok:
            break
        frame_index += 1
        if frame_index % sample_every:
            continue
        crop = frame[crop_top:, :]
        crop = cv2.resize(crop, (sample_width, sample_height), interpolation=cv2.INTER_AREA)
        mask = magenta_mask(crop)
        gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
        boxes = censor_boxes(mask, round(crop_top * scale))
        if previous_gray is not None:
            page_dy, feature_count = page_motion(previous_gray, gray, previous_mask, mask)
            if page_dy is not None and abs(page_dy) >= 0.55:
                pairs = match_box_pairs(previous_boxes, boxes, page_dy)
                if pairs:
                    motions = [boxes[new_index][1] - previous_boxes[old_index][1]
                               for old_index, new_index in pairs]
                    box_dy = float(np.median(motions))
                    residual = box_dy - page_dy
                    motion_frames += 1
                    matched_boxes += len(pairs)
                    page_deltas.append(page_dy)
                    box_deltas.append(box_dy)
                    signed_residuals.append(residual)
                    absolute_residuals.append(abs(residual))
                    visually_aligned_2px += abs(residual) <= 2.0
                    visually_aligned_5px += abs(residual) <= 5.0
                    trigger_local = (
                        abs(residual / scale) > local_trigger_threshold
                        or len(pairs) != min(len(previous_boxes), len(boxes))
                    )
                    local_diagnostics: list[dict] = []
                    frame_local_residuals: list[float] = []
                    if trigger_local:
                        local_trigger_frames += 1
                        frame_local_records: list[tuple[dict, float]] = []
                        for old_index, new_index in pairs:
                            old_box = previous_boxes[old_index]
                            new_box = boxes[new_index]
                            box_delta_x = new_box[0] - old_box[0]
                            box_delta_y = new_box[1] - old_box[1]
                            evidence = local_background_motion(
                                previous_gray,
                                gray,
                                previous_mask,
                                mask,
                                old_box,
                                (box_delta_x, box_delta_y),
                                round(crop_top * scale),
                                scale,
                            )
                            if evidence is None:
                                local_rejected_boxes += 1
                                local_diagnostics.append({
                                    "accepted": False,
                                    "reason": "insufficient-background",
                                    "boxDy": round(box_delta_y / scale, 2),
                                })
                                continue
                            evidence_residual = box_delta_y - float(evidence["dy"])
                            evidence_record = {
                                **evidence,
                                "boxDy": round(box_delta_y / scale, 2),
                                "backgroundDy": round(float(evidence["dy"]) / scale, 2),
                                "residualDy": round(evidence_residual / scale, 2),
                            }
                            local_diagnostics.append(evidence_record)
                            if not evidence["accepted"]:
                                local_rejected_boxes += 1
                                continue
                            frame_local_records.append((evidence_record, evidence_residual / scale))
                        if len(frame_local_records) > 1:
                            local_center = float(np.median([value for _, value in frame_local_records]))
                            consensus_records = []
                            for evidence_record, evidence_residual in frame_local_records:
                                if abs(evidence_residual - local_center) <= 24.0:
                                    consensus_records.append((evidence_record, evidence_residual))
                                else:
                                    evidence_record["accepted"] = False
                                    evidence_record["reason"] = "motion-consensus-outlier"
                                    local_rejected_boxes += 1
                            frame_local_records = consensus_records
                        for _, evidence_residual in frame_local_records:
                            local_evidence_matched_boxes += 1
                            local_signed_residuals.append(evidence_residual)
                            local_absolute_residuals.append(abs(evidence_residual))
                            frame_local_residuals.append(evidence_residual)
                        if frame_local_residuals:
                            local_evidence_frames += 1
                            corrected_residual = float(np.median(frame_local_residuals))
                        else:
                            local_uncertain_frames += 1
                            corrected_residual = residual / scale
                    else:
                        corrected_residual = residual / scale
                    corrected_signed_residuals.append(corrected_residual)
                    corrected_absolute_residuals.append(abs(corrected_residual))
                    if len(samples) < 120:
                        sample = {
                            "timeMs": round(frame_index / fps * 1_000.0, 1),
                            "pageDy": round(page_dy / scale, 2),
                            "boxDy": round(box_dy / scale, 2),
                            "residualDy": round(residual / scale, 2),
                            "boxMatches": len(pairs),
                            "flowFeatures": feature_count,
                        }
                        if trigger_local:
                            sample["localEvidence"] = {
                                "frameResidualDy": round(corrected_residual, 2),
                                "accepted": len(frame_local_residuals),
                                "rejected": len(local_diagnostics) - len(frame_local_residuals),
                                "boxes": local_diagnostics,
                            }
                        samples.append(sample)
        previous_gray = gray
        previous_mask = mask
        previous_boxes = boxes
    capture.release()

    return {
        "file": str(path.resolve()),
        "width": width,
        "height": height,
        "fps": round(fps, 3),
        "sampleEveryFrames": sample_every,
        "motionFramesWithBoxMatch": motion_frames,
        "matchedBoxes": matched_boxes,
        "alignmentRateWithin2px": round(visually_aligned_2px / max(1, motion_frames), 4),
        "alignmentRateWithin5px": round(visually_aligned_5px / max(1, motion_frames), 4),
        "signedResidualPx": {
            "p50": percentile([value / scale for value in signed_residuals], 50),
            "p90": percentile([value / scale for value in signed_residuals], 90),
        },
        "absoluteResidualPx": {
            "p50": percentile([value / scale for value in absolute_residuals], 50),
            "p90": percentile([value / scale for value in absolute_residuals], 90),
            "p95": percentile([value / scale for value in absolute_residuals], 95),
            "max": round(max(absolute_residuals, default=0.0) / scale, 3),
        },
        "correctedAlignment": {
            "method": "global-flow-with-local-background-fallback",
            "triggerThresholdPx": local_trigger_threshold,
            "signedResidualPx": {
                "p50": percentile(corrected_signed_residuals, 50),
                "p90": percentile(corrected_signed_residuals, 90),
            },
            "absoluteResidualPx": {
                "p50": percentile(corrected_absolute_residuals, 50),
                "p90": percentile(corrected_absolute_residuals, 90),
                "p95": percentile(corrected_absolute_residuals, 95),
                "max": round(max(corrected_absolute_residuals, default=0.0), 3),
            },
            "alignmentRateWithin2px": round(
                sum(value <= 2.0 for value in corrected_absolute_residuals)
                / max(1, len(corrected_absolute_residuals)), 4
            ),
            "alignmentRateWithin5px": round(
                sum(value <= 5.0 for value in corrected_absolute_residuals)
                / max(1, len(corrected_absolute_residuals)), 4
            ),
            "localEvidenceFrames": local_evidence_frames,
            "localTriggerFrames": local_trigger_frames,
            "localEvidenceMatchedBoxes": local_evidence_matched_boxes,
            "localRejectedBoxes": local_rejected_boxes,
            "localUncertainFrames": local_uncertain_frames,
            "localSignedResidualPx": {
                "p50": percentile(local_signed_residuals, 50),
                "p90": percentile(local_signed_residuals, 90),
            },
            "localAbsoluteResidualPx": {
                "p50": percentile(local_absolute_residuals, 50),
                "p90": percentile(local_absolute_residuals, 90),
                "p95": percentile(local_absolute_residuals, 95),
                "max": round(max(local_absolute_residuals, default=0.0), 3),
            },
        },
        "samples": samples,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("video", type=Path)
    parser.add_argument("--sample-every", type=int, default=1)
    parser.add_argument("--content-top", type=float, default=0.16)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    result = analyze(args.video, max(1, args.sample_every), args.content_top)
    rendered = json.dumps(result, indent=2)
    if args.output:
        args.output.write_text(rendered + "\n", encoding="utf-8")
    print(rendered)


if __name__ == "__main__":
    main()
