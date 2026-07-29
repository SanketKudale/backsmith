$ErrorActionPreference = "Stop"

$repository = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $repository "backsmith-cli\target\backsmith.jar"
$root = Join-Path ([System.IO.Path]::GetTempPath()) ("backsmith-acceptance-" + [guid]::NewGuid().ToString("N"))
$architectures = @("layered", "hexagonal", "modular-monolith", "clean", "onion", "cqrs", "microservice")

& (Join-Path $repository "mvnw.cmd") -B -ntp clean verify
if ($LASTEXITCODE -ne 0) { throw "Backsmith build failed" }

New-Item -ItemType Directory -Path $root | Out-Null
foreach ($architecture in $architectures) {
    $name = "sample-$architecture"
    & java -jar $jar create $name --project $root --architecture $architecture --yes --quiet
    if ($LASTEXITCODE -ne 0) { throw "Generation failed for $architecture" }
    $project = Join-Path $root $name
    Push-Location $project
    try {
        & ".\mvnw.cmd" -B -ntp verify
        if ($LASTEXITCODE -ne 0) { throw "Generated verification failed for $architecture" }
    } finally {
        Pop-Location
    }
}

$hexagonal = Join-Path $root "sample-hexagonal"
& java -jar $jar module payment --project $hexagonal --quiet
& java -jar $jar entity Payment --project $hexagonal --module payment `
    --field "id:uuid:required" `
    --field "amount:decimal:required:precision=19:scale=4" `
    --field "currency:string:required:length=3" `
    --field "status:enum[CREATED,PROCESSING,COMPLETED,FAILED]:required" `
    --quiet
& java -jar $jar api (Join-Path $repository "examples\contracts\payments.yaml") `
    --project $hexagonal --module payment --quiet
Push-Location $hexagonal
try {
    & ".\mvnw.cmd" -B -ntp verify
    if ($LASTEXITCODE -ne 0) { throw "Component and contract verification failed" }
} finally {
    Pop-Location
}

$dryRunTarget = Join-Path $hexagonal "src\main\java\com\example\samplehexagonal\modules\customer"
& java -jar $jar entity Customer --project $hexagonal --module customer --dry-run --quiet
if (Test-Path -LiteralPath $dryRunTarget) { throw "Dry-run wrote project files" }

$ownedFile = Join-Path $hexagonal "src\main\java\com\example\samplehexagonal\modules\payment\domain\model\Payment.java"
Add-Content -LiteralPath $ownedFile -Value "// user change"
& java -jar $jar entity Payment --project $hexagonal --module payment `
    --field "id:uuid:required" `
    --field "amount:decimal:required:precision=19:scale=4" `
    --field "currency:string:required:length=3" `
    --field "status:enum[CREATED,PROCESSING,COMPLETED,FAILED]:required" `
    --quiet
if ($LASTEXITCODE -ne 4) { throw "Conflict safety did not return exit code 4" }

Write-Output "Backsmith acceptance passed. Generated projects: $root"
