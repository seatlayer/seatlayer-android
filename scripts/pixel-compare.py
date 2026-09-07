#!/usr/bin/env python3
"""Compare a captured frame against a reference frame and score the difference.

Two frames of the same screen never line up perfectly: a device screenshot and
a reference capture can sit a couple of pixels apart, and text antialiasing
differs even when the layout is identical. So the comparison

  1. aligns the frames by the whole-pixel offset that minimises the mean
     absolute luma difference (a coarse search on a downscaled pair, refined at
     full resolution),
  2. marks every pixel whose luma still differs by more than a tolerance,
  3. reports the share of marked pixels and the bounding box of the largest
     connected group of them, and
  4. writes a heatmap so a human can see where the difference sits.

The two numbers answer different questions. The percentage catches broad drift
(a wrong surface colour, a shifted background). The largest block catches the
kind of difference a percentage hides: a missing badge or a control that moved
is a small share of a big screen but a solid, contiguous patch. Antialiasing
noise scatters as single pixels and fails neither test.

Defaults come from the pixel loop used for the React Native parity round:
below 1.5 % differing pixels, and no connected block wider or taller than
6 pixels. Both are overridable.

Requires Pillow and numpy only.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

try:
    import numpy as np
    from PIL import Image
except ImportError as error:  # pragma: no cover - environment guard
    print(
        f"pixel-compare needs Pillow and numpy ({error}). "
        "Install them, for example: python3 -m pip install pillow numpy",
        file=sys.stderr,
    )
    raise SystemExit(2)

DEFAULT_TOLERANCE = 24
DEFAULT_MAX_DIFF_PCT = 1.5
DEFAULT_MAX_BLOCK_PX = 6
DEFAULT_MAX_SHIFT = 6


def load_luma(path: Path) -> np.ndarray:
    with Image.open(path) as image:
        return np.asarray(image.convert("L"), dtype=np.int16)


def crop_common(first: np.ndarray, second: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    height = min(first.shape[0], second.shape[0])
    width = min(first.shape[1], second.shape[1])
    return first[:height, :width], second[:height, :width]


def overlap(first: np.ndarray, second: np.ndarray, dy: int, dx: int):
    """Return the two windows that overlap when `second` is shifted by (dy, dx)."""
    height, width = first.shape
    a = first[max(0, dy) : height + min(0, dy), max(0, dx) : width + min(0, dx)]
    b = second[max(0, -dy) : height + min(0, -dy), max(0, -dx) : width + min(0, -dx)]
    return a, b


def best_offset(
    first: np.ndarray, second: np.ndarray, max_shift: int
) -> tuple[int, int]:
    """Whole-pixel (dy, dx) shift of `second` that best lines it up with `first`."""
    if max_shift <= 0:
        return 0, 0

    def score(a: np.ndarray, b: np.ndarray, dy: int, dx: int) -> float:
        window_a, window_b = overlap(a, b, dy, dx)
        if window_a.size == 0:
            return float("inf")
        return float(np.abs(window_a - window_b).mean())

    step = 4 if min(first.shape) >= 400 else 1
    coarse_shift = max(1, max_shift // step)
    coarse_a, coarse_b = first[::step, ::step], second[::step, ::step]
    best = (float("inf"), 0, 0)
    for dy in range(-coarse_shift, coarse_shift + 1):
        for dx in range(-coarse_shift, coarse_shift + 1):
            value = score(coarse_a, coarse_b, dy, dx)
            if value < best[0]:
                best = (value, dy * step, dx * step)

    _, centre_y, centre_x = best
    refined = (float("inf"), centre_y, centre_x)
    span = step if step > 1 else 0
    for dy in range(centre_y - span, centre_y + span + 1):
        for dx in range(centre_x - span, centre_x + span + 1):
            if abs(dy) > max_shift or abs(dx) > max_shift:
                continue
            value = score(first, second, dy, dx)
            if value < refined[0]:
                refined = (value, dy, dx)
    return refined[1], refined[2]


def largest_block(mask: np.ndarray) -> tuple[int, int]:
    """Height and width of the largest 8-connected group of marked pixels.

    Rows are reduced to runs of marked pixels first, then runs that touch a run
    on the previous row are merged with a union-find. That keeps the pass over
    a full-resolution phone screenshot fast without a SciPy dependency.
    """
    if not mask.any():
        return 0, 0

    parent: list[int] = []
    boxes: list[list[int]] = []  # top, left, bottom, right per run

    def make(top: int, left: int, right: int) -> int:
        parent.append(len(parent))
        boxes.append([top, left, top, right])
        return len(parent) - 1

    def find(index: int) -> int:
        while parent[index] != index:
            parent[index] = parent[parent[index]]
            index = parent[index]
        return index

    def union(a: int, b: int) -> None:
        root_a, root_b = find(a), find(b)
        if root_a == root_b:
            return
        parent[root_b] = root_a
        box_a, box_b = boxes[root_a], boxes[root_b]
        box_a[0] = min(box_a[0], box_b[0])
        box_a[1] = min(box_a[1], box_b[1])
        box_a[2] = max(box_a[2], box_b[2])
        box_a[3] = max(box_a[3], box_b[3])

    previous: list[tuple[int, int, int]] = []  # (left, right, run id)
    for row in range(mask.shape[0]):
        marked = mask[row]
        if not marked.any():
            previous = []
            continue
        padded = np.concatenate(([False], marked, [False]))
        edges = np.flatnonzero(padded[1:] != padded[:-1])
        current: list[tuple[int, int, int]] = []
        for start, end in zip(edges[0::2], edges[1::2]):
            left, right = int(start), int(end) - 1
            run = make(row, left, right)
            for previous_left, previous_right, previous_run in previous:
                # 8-connectivity: a one-pixel diagonal touch still connects.
                if previous_left <= right + 1 and left <= previous_right + 1:
                    union(previous_run, run)
                    root = find(run)
                    boxes[root][2] = max(boxes[root][2], row)
            current.append((left, right, run))
        previous = current

    best = (0, 0)
    for index in range(len(parent)):
        if find(index) != index:
            continue
        top, left, bottom, right = boxes[index]
        height, width = bottom - top + 1, right - left + 1
        if height * width > best[0] * best[1]:
            best = (height, width)
    return best


def write_heatmap(base: np.ndarray, difference: np.ndarray, mask: np.ndarray, path: Path) -> None:
    faded = (base // 2 + 96).astype(np.uint8)
    canvas = np.stack([faded] * 3, axis=-1)
    warm = np.clip(difference.astype(np.int32) * 3, 0, 255).astype(np.uint8)
    canvas[..., 0] = np.where(mask, 255, canvas[..., 0])
    canvas[..., 1] = np.where(mask, np.minimum(warm, 90), canvas[..., 1])
    canvas[..., 2] = np.where(mask, np.minimum(warm, 90), canvas[..., 2])
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(canvas).save(path)


def compare(
    candidate: Path,
    reference: Path,
    heatmap: Path | None,
    tolerance: int,
    max_shift: int,
    max_diff_pct: float,
    max_block_px: int,
) -> dict:
    first, second = crop_common(load_luma(reference), load_luma(candidate))
    dy, dx = best_offset(first, second, max_shift)
    window_reference, window_candidate = overlap(first, second, dy, dx)
    difference = np.abs(window_reference - window_candidate)
    mask = difference > tolerance
    diff_pct = float(mask.mean() * 100.0)
    block_height, block_width = largest_block(mask)
    if heatmap is not None:
        write_heatmap(window_reference.astype(np.uint8), difference, mask, heatmap)
    passed = (
        diff_pct < max_diff_pct
        and block_height <= max_block_px
        and block_width <= max_block_px
    )
    return {
        "candidate": str(candidate),
        "reference": str(reference),
        "size": [int(window_reference.shape[1]), int(window_reference.shape[0])],
        "offset": [int(dy), int(dx)],
        "diff_pct": round(diff_pct, 3),
        "largest_block": [int(block_height), int(block_width)],
        "heatmap": str(heatmap) if heatmap else None,
        "pass": bool(passed),
    }


def pair_up(candidate_dir: Path, reference_dir: Path) -> list[tuple[Path, Path]]:
    pairs = []
    for candidate in sorted(candidate_dir.glob("*.png")):
        if candidate.name.endswith("-diff.png"):
            continue
        reference = reference_dir / candidate.name
        if reference.exists():
            pairs.append((candidate, reference))
    return pairs


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Score a captured frame against a reference frame.",
        formatter_class=argparse.ArgumentDefaultsHelpFormatter,
    )
    parser.add_argument("candidate", type=Path, help="captured PNG, or a directory of them")
    parser.add_argument("reference", type=Path, help="reference PNG, or a directory of them")
    parser.add_argument("--out", type=Path, help="heatmap PNG, or a directory for them")
    parser.add_argument("--tolerance", type=int, default=DEFAULT_TOLERANCE,
                        help="per-pixel luma difference, 0-255, that counts as different")
    parser.add_argument("--max-shift", type=int, default=DEFAULT_MAX_SHIFT,
                        help="largest whole-pixel alignment shift to search")
    parser.add_argument("--max-diff-pct", type=float, default=DEFAULT_MAX_DIFF_PCT,
                        help="failing share of differing pixels")
    parser.add_argument("--max-block-px", type=int, default=DEFAULT_MAX_BLOCK_PX,
                        help="failing size of the largest connected differing block")
    arguments = parser.parse_args()

    if arguments.candidate.is_dir() != arguments.reference.is_dir():
        print("compare a file with a file, or a directory with a directory", file=sys.stderr)
        return 2

    if arguments.candidate.is_dir():
        pairs = pair_up(arguments.candidate, arguments.reference)
        if not pairs:
            print("no frame in the captured directory has a same-named reference", file=sys.stderr)
            return 2
        out_dir = arguments.out or arguments.candidate
    else:
        pairs = [(arguments.candidate, arguments.reference)]
        out_dir = None

    results = []
    for candidate, reference in pairs:
        if out_dir is not None:
            heatmap = out_dir / f"{candidate.stem}-diff.png"
        elif arguments.out is not None:
            heatmap = arguments.out
        else:
            heatmap = candidate.with_name(f"{candidate.stem}-diff.png")
        results.append(
            compare(
                candidate,
                reference,
                heatmap,
                arguments.tolerance,
                arguments.max_shift,
                arguments.max_diff_pct,
                arguments.max_block_px,
            )
        )

    failures = [result for result in results if not result["pass"]]
    print(json.dumps({
        "thresholds": {
            "tolerance": arguments.tolerance,
            "max_diff_pct": arguments.max_diff_pct,
            "max_block_px": arguments.max_block_px,
        },
        "frames": results,
        "failed": len(failures),
    }, indent=2))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
