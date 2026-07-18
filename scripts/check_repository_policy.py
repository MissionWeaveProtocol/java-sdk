from __future__ import annotations

import re
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DISPLAY_FRAGMENT = "".join(("Mission", "Weave"))
MACHINE_FRAGMENT = "".join(("mission", "weave"))
ENVIRONMENT_FRAGMENT = "".join(("MISSION", "WEAVE"))
RETIRED_ACRONYM = "".join(("AW", "GP"))
RETIRED_DECISION_WORD = "".join(("A", "DR"))

VOCABULARY_RULES = (
    (
        "retired acronym",
        re.compile(rf"\b{re.escape(RETIRED_ACRONYM)}\b", re.IGNORECASE),
    ),
    (
        "retired expanded name",
        re.compile(
            r"\b"
            + r"[\s_-]+".join(("Agent", "Workgroup", "Protocol"))
            + r"\b",
            re.IGNORECASE,
        ),
    ),
    (
        "incomplete product display name",
        re.compile(rf"{re.escape(DISPLAY_FRAGMENT)}(?!Protocol)"),
    ),
    (
        "incomplete product machine identifier",
        re.compile(rf"{re.escape(MACHINE_FRAGMENT)}(?!protocol)"),
    ),
    (
        "incomplete environment prefix",
        re.compile(rf"{re.escape(ENVIRONMENT_FRAGMENT)}(?!PROTOCOL)"),
    ),
    (
        "retired decision-record shorthand",
        re.compile(rf"\b{re.escape(RETIRED_DECISION_WORD)}s?\b", re.IGNORECASE),
    ),
    (
        "retired decision-record directory",
        re.compile(r"docs[/\\]+" + RETIRED_DECISION_WORD, re.IGNORECASE),
    ),
    (
        "retired decision-record phrase",
        re.compile(
            r"\b"
            + r"[\s_-]+".join(("architecture", "decision", "record"))
            + r"s?\b",
            re.IGNORECASE,
        ),
    ),
)


def tracked_files() -> list[str]:
    output = subprocess.check_output(
        ["git", "ls-files", "-z"],
        cwd=ROOT,
    ).decode("utf-8")
    return [entry for entry in output.split("\0") if entry]


def inspect(label: str, value: str, failures: list[str]) -> None:
    for line_number, line in enumerate(value.splitlines(), start=1):
        for rule_name, pattern in VOCABULARY_RULES:
            if pattern.search(line):
                failures.append(f"{label}:{line_number}: {rule_name}")


def main() -> None:
    failures: list[str] = []
    for relative_path in tracked_files():
        inspect(f"path:{relative_path}", relative_path, failures)
        contents = (ROOT / relative_path).read_bytes()
        if b"\0" not in contents:
            try:
                text = contents.decode("utf-8")
            except UnicodeDecodeError:
                continue
            inspect(relative_path, text, failures)

    if failures:
        raise SystemExit(
            "Repository policy violations:\n  " + "\n  ".join(failures)
        )

    print(f"Repository policy passed for {len(tracked_files())} tracked files.")


if __name__ == "__main__":
    main()
