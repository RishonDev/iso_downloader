# T2ISO

T2ISO is a desktop ISO downloader and flasher focused on T2 Linux supported distributions.

## Screenshot

![T2ISO Screenshot](docs/screenshot.png)

## Features

- Download Ubuntu and Mint ISO variants from metadata
- Resume/cancel download support
- SHA-256 checksum display after download
- Optional ISO flashing to a selected device path
- Build outputs for Linux AppImage and macOS `.app`

## Build

### Prerequisites

- JDK 25+
- Maven 3.9+
- Linux AppImage builds: `appimagetool`

### Package

```bash
mvn clean package
```

Outputs are generated in project root and `target/`.

## Release Workflow

GitHub release automation triggers on version tags:

```bash
git tag v0.3
git push origin v0.3
```

This creates a release and uploads build artifacts.

## Notes

- macOS icon is copied automatically from `packaging/macos/icon.icns` into `T2ISO.app/Contents/Resources/T2ISO.icns` during packaging on macOS.
