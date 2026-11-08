#!/usr/bin/env python3
"""
Extract release notes for a specific version or the latest version from docs/RELEASE_NOTES.md.
Usage:
    python3 scripts/extract-release-notes.py [TAG_OR_VERSION] [OUTPUT_FILE]
"""

import sys
import re
from pathlib import Path

def extract_release_notes(tag_or_version=None, notes_path="docs/RELEASE_NOTES.md"):
    notes_file = Path(notes_path)
    if not notes_file.exists():
        return f"# Release {tag_or_version or 'Latest'}\n\nNo release notes file found at {notes_path}."

    content = notes_file.read_text(encoding="utf-8")

    # If specific tag or version provided
    if tag_or_version:
        clean_ver = tag_or_version.lstrip("v").strip()
        # Look for ## Test Studio v?X.Y.Z
        pattern = rf"(##\s+Test Studio\s+v?{re.escape(clean_ver)}[\s\S]*?)(?=\n##\s+Test Studio|\Z)"
        match = re.search(pattern, content, re.IGNORECASE)
        if match:
            extracted = match.group(1).strip()
            # Clean trailing separator
            extracted = re.sub(r"\n---\s*$", "", extracted).strip()
            return extracted

    # Fallback: extract the topmost release section (first ## Test Studio ... until next ## Test Studio or EOF)
    pattern = r"(##\s+Test Studio\s+v?[\d.]+[\s\S]*?)(?=\n##\s+Test Studio|\Z)"
    match = re.search(pattern, content)
    if match:
        extracted = match.group(1).strip()
        extracted = re.sub(r"\n---\s*$", "", extracted).strip()
        return extracted

    return content.strip()

def main():
    tag = sys.argv[1] if len(sys.argv) > 1 and sys.argv[1].strip() else None
    output_path = sys.argv[2] if len(sys.argv) > 2 and sys.argv[2].strip() else None

    notes = extract_release_notes(tag)

    if output_path:
        out_file = Path(output_path)
        out_file.parent.mkdir(parents=True, exist_ok=True)
        out_file.write_text(notes + "\n", encoding="utf-8")
        print(f"Extracted release notes to: {output_path}")
    else:
        print(notes)

if __name__ == "__main__":
    main()

