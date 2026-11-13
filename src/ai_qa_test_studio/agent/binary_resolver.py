"""Resolve absolute paths for node/npx when GUI apps lack shell PATH."""

from __future__ import annotations

import os
import shutil
import sys
from pathlib import Path

_CANDIDATE_DIRS = (
    Path("/opt/homebrew/bin"),
    Path("/usr/local/bin"),
    Path.home() / ".nvm" / "current" / "bin",
    Path.home() / ".fnm" / "current" / "bin",
    Path.home() / ".local" / "share" / "fnm" / "aliases" / "default" / "bin",
    Path(os.environ.get("PROGRAMFILES", r"C:\Program Files")) / "nodejs",
)


class BinaryResolver:
    def which(self, name: str) -> Path | None:
        found = shutil.which(name)
        if found:
            return Path(found)
        candidates = [name]
        if sys.platform.startswith("win"):
            candidates.extend([f"{name}.cmd", f"{name}.exe"])
        for directory in _CANDIDATE_DIRS:
            for candidate in candidates:
                path = directory / candidate
                if path.is_file():
                    return path
        # nvm versions directory (best-effort newest)
        nvm_versions = Path.home() / ".nvm" / "versions" / "node"
        if nvm_versions.is_dir():
            for version_dir in sorted(nvm_versions.iterdir(), reverse=True):
                for candidate in candidates:
                    path = version_dir / "bin" / candidate
                    if path.is_file():
                        return path
        return None

    def require(self, name: str) -> Path:
        path = self.which(name)
        if path is None:
            raise FileNotFoundError(f"Unable to resolve binary: {name}")
        return path
