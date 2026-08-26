#!/usr/bin/env python3
"""Build deterministic, user-facing release notes from tagged Git history.

GitHub's generated notes are PR/label-oriented and collapse to only a compare link
when a release is assembled directly from commits on master. SubHub releases are
tag-driven, so this generator treats the repository's ``[type]`` commit subjects as
the stable changelog source and uses the first useful body sentence as optional
detail.
"""

from __future__ import annotations

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


SUBJECT = re.compile(
    r"^\[(feat|fix|perf|refactor|docs|test|build|ci|chore|revert)\]\s+(.+)$",
    re.IGNORECASE,
)
SEMVER_TAG = re.compile(r"^v\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$")
RELEASE_HOUSEKEEPING = re.compile(
    r"^(?:bump|prepare|release)\b.*\b(?:version|subhub|v?\d+\.\d+\.\d+)",
    re.IGNORECASE,
)

CATEGORIES = (
    ("feat", "New"),
    ("fix", "Fixed"),
    ("perf", "Faster & smoother"),
    ("refactor", "Improved"),
    ("revert", "Restored"),
    ("docs", "Documentation"),
    ("build", "Build & release"),
    ("ci", "Build & release"),
    ("test", "Quality"),
    ("chore", "Maintenance"),
)


@dataclass(frozen=True)
class Commit:
    hash: str
    subject: str
    body: str


@dataclass(frozen=True)
class Entry:
    kind: str
    title: str
    detail: str


def run_git(arguments: Sequence[str]) -> str:
    completed = subprocess.run(
        ["git", *arguments], check=True, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, encoding="utf-8", errors="strict",
    )
    return completed.stdout


def previous_tag(tag: str) -> str | None:
    try:
        value = run_git(["describe", "--tags", "--abbrev=0", "--match", "v[0-9]*", f"{tag}^"])
    except subprocess.CalledProcessError:
        return None
    candidate = value.strip()
    return candidate if SEMVER_TAG.fullmatch(candidate) else None


def read_commits(tag: str, base_tag: str | None = None) -> list[Commit]:
    revision = f"{base_tag}..{tag}" if base_tag else tag
    raw = run_git(["log", "--no-merges", "--reverse", "--format=%H%x00%s%x00%b%x00", revision])
    fields = raw.split("\0")
    while fields and not fields[-1].strip():
        fields.pop()
    if len(fields) % 3:
        raise ValueError("Unexpected git log record shape")
    return [Commit(*fields[index:index + 3]) for index in range(0, len(fields), 3)]


def _plain_body(body: str) -> str:
    # Repair historical PowerShell commits that accidentally stored literal \n text.
    body = body.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\r", "")
    paragraphs = re.split(r"\n\s*\n", body)
    for paragraph in paragraphs:
        clean = " ".join(line.strip() for line in paragraph.splitlines() if line.strip())
        if not clean:
            continue
        if re.match(r"^(checks?|tests?|verification|signed-off-by|co-authored-by):", clean,
                    re.IGNORECASE):
            continue
        return clean
    return ""


def _first_sentence(text: str, limit: int = 240) -> str:
    if not text:
        return ""
    match = re.search(r"(?<=[.!?])\s", text)
    sentence = text[:match.start() + 1] if match else text
    if len(sentence) <= limit:
        return sentence
    clipped = sentence[:limit - 1].rsplit(" ", 1)[0].rstrip(" ,;:-")
    return clipped + "…"


def entries(commits: Iterable[Commit], strict: bool = True) -> list[Entry]:
    result: list[Entry] = []
    invalid: list[str] = []
    for commit in commits:
        match = SUBJECT.fullmatch(commit.subject.strip())
        if not match:
            invalid.append(f"{commit.hash[:8]} {commit.subject}")
            continue
        kind = match.group(1).lower()
        title = match.group(2).strip().rstrip(".")
        if kind == "chore" and RELEASE_HOUSEKEEPING.match(title):
            continue
        detail = _first_sentence(_plain_body(commit.body))
        normalized_title = re.sub(r"\W+", " ", title).strip().lower()
        normalized_detail = re.sub(r"\W+", " ", detail).strip().lower()
        if normalized_detail.startswith(normalized_title) or normalized_title in normalized_detail[:120]:
            detail = ""
        result.append(Entry(kind, title, detail))
    if strict and invalid:
        joined = "\n".join(f"  - {value}" for value in invalid)
        raise ValueError(
            "Release commits must start with a supported [type] prefix:\n" + joined
        )
    return result


def changelog_markdown(version: str, values: Iterable[Entry]) -> str:
    grouped: dict[str, list[Entry]] = {}
    heading_for = dict(CATEGORIES)
    for value in values:
        grouped.setdefault(heading_for[value.kind], []).append(value)
    lines = [f"## What’s new in SubHub {version}", ""]
    emitted: set[str] = set()
    for _, heading in CATEGORIES:
        if heading in emitted or heading not in grouped:
            continue
        emitted.add(heading)
        lines.extend((f"### {heading}", ""))
        for value in grouped[heading]:
            title = value.title[0].upper() + value.title[1:] if value.title else value.title
            bullet = f"- **{title}**"
            if value.detail:
                bullet += f" — {value.detail}"
            lines.append(bullet)
        lines.append("")
    if not emitted:
        lines.extend(("SubHub received a maintenance release with no user-facing changes.", ""))
    return "\n".join(lines).rstrip() + "\n"


def release_markdown(version: str, changelog: str, repository: str,
                     base_tag: str | None, tag: str) -> str:
    lines = [changelog.rstrip(), "", "## Choose your APK", "",
             "- **Universal** works on every Android CPU architecture supported by SubHub. "
             "**Choose Universal if you are unsure.**",
             "- **arm64-v8a** is the smaller download for most modern Android phones and tablets.",
             "- **armeabi-v7a** is for older 32-bit ARM devices.",
             "- **x86** and **x86_64** are primarily for compatible Intel devices and Android emulators.",
             "", "Per-ABI APKs are smaller, but only install on their matching architecture. "
             "Every APK in this release contains the same SubHub version and features."]
    if base_tag:
        lines.extend(("", f"**Full changelog:** https://github.com/{repository}/compare/{base_tag}...{tag}"))
    return "\n".join(lines).rstrip() + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repository")
    parser.add_argument("--changelog-output", type=Path)
    parser.add_argument("--release-output", type=Path)
    parser.add_argument("--base-tag")
    parser.add_argument("--allow-untyped", action="store_true")
    parser.add_argument("--check-only", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    base = args.base_tag if args.base_tag is not None else previous_tag(args.tag)
    values = entries(read_commits(args.tag, base), strict=not args.allow_untyped)
    if args.check_only:
        print(f"Validated {len(values)} release-note commit(s) since {base or 'repository start'}")
        return 0
    if not SEMVER_TAG.fullmatch(args.tag):
        raise SystemExit(f"Invalid release tag: {args.tag}")
    if not args.repository or args.changelog_output is None or args.release_output is None:
        raise SystemExit("Release output requires --repository, --changelog-output, and --release-output")
    version = args.tag[1:]
    changelog = changelog_markdown(version, values)
    release = release_markdown(version, changelog, args.repository, base, args.tag)
    args.changelog_output.parent.mkdir(parents=True, exist_ok=True)
    args.changelog_output.write_text(changelog, encoding="utf-8")
    args.release_output.parent.mkdir(parents=True, exist_ok=True)
    args.release_output.write_text(release, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
