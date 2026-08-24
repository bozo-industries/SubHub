#!/usr/bin/env python3
"""Read, validate, or update SubHub's single release-version source."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
VERSION_FILE = ROOT / "version.properties"
SEMVER = re.compile(r"^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-[0-9A-Za-z.-]+)?$")


def read_version() -> tuple[int, str]:
    values: dict[str, str] = {}
    for line in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        if line and not line.lstrip().startswith("#") and "=" in line:
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
    try:
        code = int(values["VERSION_CODE"])
        name = values["VERSION_NAME"]
    except (KeyError, ValueError) as exc:
        raise SystemExit(f"Invalid {VERSION_FILE.name}: {exc}") from exc
    if code < 1:
        raise SystemExit("VERSION_CODE must be a positive integer")
    if not SEMVER.fullmatch(name):
        raise SystemExit("VERSION_NAME must be semantic versioning, for example 0.2.0")
    return code, name


def write_version(code: int, name: str) -> None:
    VERSION_FILE.write_text(
        f"VERSION_CODE={code}\nVERSION_NAME={name}\n",
        encoding="utf-8",
        newline="\n",
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", help="Require an exact v<versionName> release tag")
    parser.add_argument("--set-version", help="Write a new semantic VERSION_NAME")
    parser.add_argument("--version-code", type=int, help="VERSION_CODE used with --set-version")
    parser.add_argument("--github-output", type=Path, help="Append name/code values for Actions")
    args = parser.parse_args()

    old_code, old_name = read_version()
    if args.set_version:
        if not SEMVER.fullmatch(args.set_version):
            parser.error("--set-version must be semantic versioning")
        next_code = args.version_code if args.version_code is not None else old_code + 1
        if next_code <= old_code:
            parser.error("the new VERSION_CODE must be greater than the current code")
        write_version(next_code, args.set_version)

    code, name = read_version()
    if args.tag and args.tag != f"v{name}":
        raise SystemExit(f"Tag {args.tag!r} does not match VERSION_NAME {name!r}; expected v{name}")

    if args.github_output:
        with args.github_output.open("a", encoding="utf-8", newline="\n") as output:
            output.write(f"version_code={code}\nversion_name={name}\n")
    print(f"SubHub {name} (versionCode {code})")


if __name__ == "__main__":
    main()
