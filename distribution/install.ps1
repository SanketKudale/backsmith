$ErrorActionPreference = "Stop"

$repository = "SanketKudale/backsmith"
$installRoot = if ($env:BACKSMITH_HOME) { $env:BACKSMITH_HOME } else { Join-Path $env:LOCALAPPDATA "Backsmith" }
$binDirectory = if ($env:BACKSMITH_BIN_DIR) { $env:BACKSMITH_BIN_DIR } else { Join-Path $env:USERPROFILE ".local\bin" }
$baseUrl = "https://github.com/$repository/releases/latest/download"
$temporary = Join-Path ([System.IO.Path]::GetTempPath()) ("backsmith-" + [guid]::NewGuid())

try {
    $javaVersionOutput = (& java -version 2>&1 | Select-Object -First 1).ToString()
    if ($LASTEXITCODE -ne 0 -or $javaVersionOutput -notmatch 'version "(\d+)') {
        throw "Backsmith requires Java 21 or newer."
    }
    if ([int]$Matches[1] -lt 21) {
        throw "Backsmith requires Java 21 or newer; detected Java $($Matches[1])."
    }

    New-Item -ItemType Directory -Force -Path $temporary | Out-Null
    $archive = Join-Path $temporary "backsmith.zip"
    $checksums = Join-Path $temporary "SHA256SUMS"
    Invoke-WebRequest "$baseUrl/backsmith.zip" -OutFile $archive
    Invoke-WebRequest "$baseUrl/SHA256SUMS" -OutFile $checksums

    $checksumLine = Get-Content $checksums | Where-Object { $_ -match '\sbacksmith\.zip$' } | Select-Object -First 1
    if (-not $checksumLine) {
        throw "Release checksum for backsmith.zip was not found."
    }
    $expected = ($checksumLine -split '\s+')[0].ToLowerInvariant()
    $actual = (Get-FileHash -Algorithm SHA256 $archive).Hash.ToLowerInvariant()
    if ($expected -ne $actual) {
        throw "Checksum verification failed; installation stopped."
    }

    $extracted = Join-Path $temporary "extracted"
    Expand-Archive $archive -DestinationPath $extracted
    $archiveRoot = Get-ChildItem -Path $extracted -Directory | Select-Object -First 1
    if (-not $archiveRoot) {
        throw "Release archive did not contain the expected directory."
    }

    New-Item -ItemType Directory -Force -Path $installRoot, $binDirectory | Out-Null
    Copy-Item -Path (Join-Path $archiveRoot.FullName "*") -Destination $installRoot -Recurse -Force
    $shim = Join-Path $binDirectory "backsmith.cmd"
    Set-Content -Path $shim -Encoding Ascii -Value "@echo off`r`ncall `"$installRoot\bin\backsmith.cmd`" %*"

    Write-Output "Backsmith installed to $installRoot"
    Write-Output "Ensure $binDirectory is on PATH, then run: backsmith --help"
}
finally {
    if (Test-Path -LiteralPath $temporary) {
        Remove-Item -LiteralPath $temporary -Recurse -Force
    }
}
