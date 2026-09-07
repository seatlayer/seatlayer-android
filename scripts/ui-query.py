#!/usr/bin/env python3
"""Query one uiautomator hierarchy dump.

Reads the XML produced by `uiautomator dump` and answers the two questions the
device walk asks of it:

  * `centre` — the centre point, in device pixels, of the first node whose
    `content-desc`, `text` or `resource-id` matches a pattern.
  * `exists` — whether such a node is present (exit status only).
  * `list`   — every matching node, one `desc|text|bounds` line each, for
    diagnosing a walk step that could not find its control.

Matching is a case-insensitive regular expression so a caller can pass either a
literal label or an alternation of labels. Only stdlib is used, so the walk
script needs nothing installed beyond Python itself.
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def nodes(path: str):
    for node in ET.parse(path).getroot().iter("node"):
        yield node


def matches(node, pattern: re.Pattern[str]) -> bool:
    for attribute in ("content-desc", "text", "resource-id"):
        value = node.get(attribute) or ""
        if value and pattern.search(value):
            return True
    return False


def centre(node):
    found = BOUNDS.search(node.get("bounds") or "")
    if not found:
        return None
    left, top, right, bottom = (int(value) for value in found.groups())
    if right <= left or bottom <= top:
        return None
    return (left + right) // 2, (top + bottom) // 2


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("centre", "exists", "list"))
    parser.add_argument("dump")
    parser.add_argument("pattern", nargs="?", default=".")
    parser.add_argument(
        "--index",
        type=int,
        default=0,
        help="which match to use when several nodes match (default: the first)",
    )
    parser.add_argument(
        "--clickable-only",
        action="store_true",
        help="consider only nodes the platform reports as clickable",
    )
    arguments = parser.parse_args()

    try:
        pattern = re.compile(arguments.pattern, re.IGNORECASE)
    except re.error as error:
        print(f"invalid pattern: {error}", file=sys.stderr)
        return 2

    try:
        candidates = [
            node
            for node in nodes(arguments.dump)
            if matches(node, pattern)
            and (not arguments.clickable_only or node.get("clickable") == "true")
        ]
    except (ET.ParseError, OSError) as error:
        print(f"unreadable hierarchy dump: {error}", file=sys.stderr)
        return 2

    if arguments.mode == "list":
        for node in candidates:
            print(
                "|".join(
                    (
                        node.get("content-desc") or "",
                        node.get("text") or "",
                        node.get("bounds") or "",
                    )
                )
            )
        return 0

    if not candidates:
        return 1
    if arguments.mode == "exists":
        return 0

    if arguments.index >= len(candidates):
        return 1
    point = centre(candidates[arguments.index])
    if point is None:
        return 1
    print(f"{point[0]} {point[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
