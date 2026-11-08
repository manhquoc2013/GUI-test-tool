# Technical Specification: Unified CI/CD Dual Release (Portable & In-Place Upgrading Installers)

## 1. Objectives & Overview
Fulfill user specification for automated dual-package generation across CI/CD:
1. **Dual Artifact Distribution per Platform**:
   - For each release, automatically produce both:
     - **Portable ZIP**: zero-install archive for Windows, macOS, and Linux.
     - **Installer package**:
       - Windows: Inno Setup executable (`TestStudio-<version>-windows-x64-Setup.exe`).
       - macOS: Drag-and-drop bundle disk image (`TestStudio-<version>-macos-<arch>.dmg`).
       - Linux: Native Debian package (`teststudio_<version>_amd64.deb`).
   - Unified Release Workflow: Merge portable and installer build jobs into a single pipeline (`.github/workflows/release-portable.yml`), uploading all portable ZIPs and installers into one GitHub Release.

2. **In-Place Upgrading & Auto-Shutdown of Running Instances (Windows & Linux)**:
   - **Active Process Detection & Termination**:
     - When installer launches while `TestStudio.exe` is currently running, prompt user with confirmation: *"Test Studio đang chạy trên hệ thống. Vui lòng bấm OK để bộ cài tự động đóng ứng dụng và tiếp tục cài đặt, hoặc Cancel để thoát."*
     - If confirmed (or silent mode), automatically and gracefully close/terminate running instances via `taskkill /F /IM TestStudio.exe /T` before file extraction begins, preventing locked file errors (`ERROR_SHARING_VIOLATION`).
   - **Override Existing Installation**:
     - Maintain stable `AppId={{5E973B1B-6FE8-4A1E-8B52-3A65C9D780E2}` and `UsePreviousAppDir=yes`, automatically detecting existing install location (`{app}`) and overwriting binaries in-place without prompting.
   - **System Version & Registry Synchronization**:
     - Update Windows Registry `Uninstall` information (`DisplayVersion`, `DisplayName`, `Publisher`, `InstallLocation`).
     - Write application metadata under `HKLM\Software\Test Studio`: `CurrentVersion`, `InstallLocation`, `LastUpdated`.
     - Dynamically pass version from `pom.xml` (`/DMyAppVersion=<version>`) to Inno Setup, eliminating hardcoded version numbers.

---

## 2. Architecture & Components

### 2.1 Windows Inno Setup Engine (`scripts/installer.iss`)
- **Mutex & Application Guard**:
  - `AppMutex=TestStudioAppMutex`
  - `CloseApplications=yes`
  - `[Code]` block: `IsAppRunning('TestStudio.exe')`, `KillApp('TestStudio.exe')`, and interactive confirmation in `InitializeSetup()`.
- **In-Place Upgrade Directives**:
  - `UsePreviousAppDir=yes`
  - `DirExistsWarning=no`
  - `[Files] Flags: ignoreversion recursesubdirs createallsubdirs`
- **System Version Registry**:
  - Writes to `HKLM\Software\Microsoft\Windows\CurrentVersion\Uninstall\{AppId}` (managed by Inno Setup).
  - Explicit entries in `HKLM\Software\{#MyAppName}` for `CurrentVersion`, `InstallLocation`, `Publisher`, `LastUpdated`.

### 2.2 Windows Build Script (`scripts/build-installer.bat`)
- Evaluates `VERSION` dynamically from `pom.xml` via `mvn help:evaluate`.
- Compiles Inno Setup with `/DMyAppVersion=%VERSION%`.
- Replaces hardcoded fallback version paths.

### 2.3 Linux Debian Package Generator (`scripts/build-installer-linux.sh`)
- Evaluates `VERSION` dynamically from `pom.xml`.
- Packages self-contained application via `jpackage --type deb`.
- Embeds desktop shortcut and menu categories.

### 2.4 macOS Disk Image Generator (`scripts/build-installer.sh`)
- Evaluates `VERSION` dynamically from `pom.xml`.
- Bundles self-contained `.app` and packages `.dmg`.

### 2.5 Unified Release Pipeline (`.github/workflows/release-portable.yml`)
- Single unified workflow triggered on tag `v*` and manual dispatch.
- Matrix builds:
  - Windows: Portable ZIP + Inno Setup EXE (`choco install innosetup`).
  - macOS: Portable ZIP + DMG Installer.
  - Linux: Portable ZIP + Debian .deb Installer.
- Publish Job:
  - Collects all portable ZIPs and installers.
  - Runs `scripts/extract-release-notes.py` to extract strictly the current version's release notes.
  - Attaches all 6 artifacts (`*.zip`, `*.exe`, `*.dmg`, `*.deb`) to the GitHub Release.
