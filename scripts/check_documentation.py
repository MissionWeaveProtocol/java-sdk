from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
READMES = {
    "README.md": "**English** | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)",
    "README.zh-CN.md": "[English](README.md) | **简体中文** | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)",
    "README.zh-TW.md": "[English](README.md) | [简体中文](README.zh-CN.md) | **繁體中文** | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)",
    "README.ja.md": "[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | **日本語** | [Español](README.es.md) | [Français](README.fr.md) | [Deutsch](README.de.md)",
    "README.es.md": "[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | **Español** | [Français](README.fr.md) | [Deutsch](README.de.md)",
    "README.fr.md": "[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | **Français** | [Deutsch](README.de.md)",
    "README.de.md": "[English](README.md) | [简体中文](README.zh-CN.md) | [繁體中文](README.zh-TW.md) | [日本語](README.ja.md) | [Español](README.es.md) | [Français](README.fr.md) | **Deutsch**",
}
REQUIRED_FILES = (
    "docs/usage.md",
    "docs/conformance.md",
    "examples/src/main/java/org/missionweaveprotocol/examples/ValidateAndSignExample.java",
    "examples/src/main/java/org/missionweaveprotocol/examples/FrameRoundTripExample.java",
    "examples/src/main/java/org/missionweaveprotocol/examples/RunConformanceExample.java",
    "scripts/smoke_install.sh",
)
REQUIRED_README_TEXT = (
    "MissionWeaveProtocol",
    "org.missionweaveprotocol",
    "missionweaveprotocol-sdk",
    "0.1.0-SNAPSHOT",
    "00964ea9064cbf1f0eca8af21a0c57367ee14752",
    "FrameCodec",
    "SchemaCatalog",
    "DocumentSignatures",
    "ConformanceRunner",
    "43/43",
    "docs/usage.md",
    "docs/conformance.md",
    "Apache-2.0",
)
FENCE_PATTERN = re.compile(r"^```[^\n]*\n(.*?)^```\s*$", re.MULTILINE | re.DOTALL)
LINK_PATTERN = re.compile(r"\[[^\]]+\]\(([^)]+)\)")


def main() -> None:
    failures: list[str] = []
    for relative in REQUIRED_FILES:
        if not (ROOT / relative).is_file():
            failures.append(f"missing required documentation asset: {relative}")

    documents: dict[str, str] = {}
    for filename, switcher in READMES.items():
        path = ROOT / filename
        if not path.is_file():
            failures.append(f"missing README translation: {filename}")
            continue
        document = path.read_text(encoding="utf-8")
        documents[filename] = document
        first_line = next((line for line in document.splitlines() if line.strip()), "")
        if first_line != switcher:
            failures.append(f"{filename}: language switcher is not canonical")
        for required in REQUIRED_README_TEXT:
            if required not in document:
                failures.append(f"{filename}: missing required text {required!r}")

    english = documents.get("README.md")
    if english is not None:
        expected_blocks = FENCE_PATTERN.findall(english)
        expected_links = LINK_PATTERN.findall("\n".join(english.splitlines()[1:]))
        for filename, document in documents.items():
            blocks = FENCE_PATTERN.findall(document)
            if blocks != expected_blocks:
                failures.append(f"{filename}: fenced code blocks differ from README.md")
            links = LINK_PATTERN.findall("\n".join(document.splitlines()[1:]))
            if links != expected_links:
                failures.append(f"{filename}: link targets differ from README.md")

    if failures:
        raise SystemExit("Documentation checks failed:\n  " + "\n  ".join(failures))
    print(f"Documentation checks passed for {len(documents)} README languages.")


if __name__ == "__main__":
    main()
