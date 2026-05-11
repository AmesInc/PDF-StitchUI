# Packaging

## Windows

Use [windows/package.ps1](C:/Users/ameli/OneDrive/Documents/New%20project%202/packaging/windows/package.ps1) to build a Windows app-image and MSI with `jpackage`.

## macOS

Use [macos/package.sh](C:/Users/ameli/OneDrive/Documents/New%20project%202/packaging/macos/package.sh) to build a `.dmg` or `.app-image` style artifact from macOS.

## Linux

Use [linux/package.sh](C:/Users/ameli/OneDrive/Documents/New%20project%202/packaging/linux/package.sh) to build an app-image style package on Linux.

## Versioning

The packaging scripts derive the Maven version from `pom.xml`, strip `-SNAPSHOT` for the app version shown in package metadata, and derive the jar filename from the current Maven `artifactId` and full project version.

## Signing

- Windows code signing can be added by wrapping the generated `.msi` or `.exe` with `signtool.exe`.
- macOS signing and notarization can be added after `jpackage` output is created using `codesign` and `notarytool`.
- Linux signing typically happens at the package repository layer rather than inside the app bundle.

The release workflow is structured so signing steps can be inserted once certificate handling is finalized.

