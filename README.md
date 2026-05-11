# PDF-StitchUI

`PDF-StitchUI` is a desktop Java app for building a stitched PDF from a draggable tile collection.

## Features

- Add one or more PDF files with a native file picker.
- Show each PDF as a large tile with a first-page thumbnail, filename, sequence number, and remove button.
- Reorder the sequence by dragging tiles.
- Export the ordered collection into a single stitched PDF with a native Save As dialog.
- Run on Windows, macOS, or Linux anywhere Java 21 is available.

## Run

```powershell
.\mvnw.cmd package
java -jar target\pdf-stitchui-1.0.0-SNAPSHOT.jar
```

## Native Packaging

Windows app-image example:

```powershell
jpackage `
  --type app-image `
  --name "PDF-StitchUI" `
  --input target `
  --main-jar pdf-stitchui-1.0.0-SNAPSHOT.jar `
  --main-class com.ameli.pdfstitcher.PdfStitcherApp
```

The same codebase can be packaged on macOS or Linux with the platform-specific `jpackage` output type.

## License

The application source is released under the MIT License. This repo also ships Apache PDFBox as a dependency; see [THIRD_PARTY_NOTICES.md](C:/Users/ameli/OneDrive/Documents/New%20project%202/THIRD_PARTY_NOTICES.md) for dependency attribution.

## Contribution Flow

- External contributions should come in through pull requests.
- `main` should be protected in GitHub so direct pushes are blocked.
- Require at least one review from the repository owners before merge.
- Keep CI green before merge.
