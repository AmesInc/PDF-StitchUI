$ErrorActionPreference = "Stop"

$pom = [xml](Get-Content "pom.xml")
$artifactId = $pom.project.artifactId
$projectVersion = $pom.project.version
$appVersion = $projectVersion -replace "-SNAPSHOT$", ""
$jarPath = "$artifactId-$projectVersion.jar"
$outputDir = "dist\windows"
$inputDir = "dist\_jpackage-input"
$commonArgs = @(
    "--name", "PDF-StitchUI",
    "--app-version", $appVersion,
    "--vendor", "AmesInc",
    "--input", $inputDir,
    "--dest", $outputDir,
    "--main-jar", $jarPath,
    "--main-class", "com.ameli.pdfstitcher.PdfStitcherApp"
)

if (Test-Path "packaging\assets\pdf-stitchui.ico") {
    $commonArgs += @("--icon", "packaging\assets\pdf-stitchui.ico")
}

if (Test-Path $outputDir) {
    Remove-Item -Recurse -Force $outputDir
}

if (Test-Path $inputDir) {
    Remove-Item -Recurse -Force $inputDir
}

New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null
Copy-Item "target\$jarPath" $inputDir

& jpackage "--type" "app-image" @commonArgs

$wixOnPath = (Get-Command light.exe -ErrorAction SilentlyContinue) -and (Get-Command candle.exe -ErrorAction SilentlyContinue)
if ($wixOnPath) {
    & jpackage "--type" "msi" @commonArgs
} else {
    Write-Host "Skipping MSI packaging because WiX tools are not installed on PATH."
}
