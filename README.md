# PDF-StitchUI

`PDF-StitchUI` is a desktop Java app for building a stitched PDF from a draggable tile collection.

## Features

- Add one or more PDF files with a native file picker.
- Add an entire folder and recursively pull in its PDF files.
- Drag PDF files or folders from Explorer directly onto the UI.
- Show each PDF as a large tile with a first-page thumbnail, filename, sequence number, page count, file size, page-range summary, and warning state.
- Reorder the sequence by dragging tiles or use move-left / move-right controls.
- Duplicate a tile without re-adding the source PDF.
- Set export page ranges per tile, such as `1-3, 6, 8-10`.
- Rotate tiles left or right before export.
- Restore the last session automatically when the app reopens.
- Export the ordered collection into a single stitched PDF with options for bookmarks, form flattening, and output compression.
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
  --app-version 1.0.0 `
  --icon packaging\assets\pdf-stitchui.ico `
  --input target `
  --main-jar pdf-stitchui-1.0.0-SNAPSHOT.jar `
  --main-class com.ameli.pdfstitcher.PdfStitcherApp
```

There are platform scripts and release notes in [packaging/README.md](C:/Users/ameli/OneDrive/Documents/New%20project%202/packaging/README.md).

## Shortcuts

- `Ctrl+O`: add PDF files
- `Ctrl+Shift+O`: add a folder
- `Ctrl+D`: duplicate the selected tiles
- `Alt+Left`: move selected tiles left
- `Alt+Right`: move selected tiles right
- `Delete`: remove the selected tiles
- `Ctrl+Shift+S`: export the stitched PDF

## License

The application source is released under the MIT License. This repo also ships Apache PDFBox as a dependency; see [THIRD_PARTY_NOTICES.md](C:/Users/ameli/OneDrive/Documents/New%20project%202/THIRD_PARTY_NOTICES.md) for dependency attribution.

## Contribution Flow

- External contributions should come in through pull requests.
- `main` should be protected in GitHub so direct pushes are blocked.
- Require at least one review from the repository owners before merge.
- Keep CI green before merge.
