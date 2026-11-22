<#
  Stores the release secrets in GitHub (encrypted; nobody can read them back, not even you).
  Run from the project folder in PowerShell, signed in with `gh auth login`:

    .\scripts\set-release-secrets.ps1
        Uploads the signing key from "Documents\Workbid signing key".

    .\scripts\set-release-secrets.ps1 -PlayJson C:\path\play-key.json -FirebaseJson C:\path\firebase-key.json
        Also stores the service-account keys the app owner sends you. One file can be used for both.

    .\scripts\set-release-secrets.ps1 -GoogleServicesJson C:\path\google-services.json
        Makes releases use the owner's Firebase project instead of the one committed in the repo.

  Safe to run again; each secret is overwritten.
#>
param(
    [string]$KeyDir = (Join-Path $env:USERPROFILE "Documents\Workbid signing key"),
    [string]$PlayJson,
    [string]$FirebaseJson,
    [string]$GoogleServicesJson,
    [switch]$SkipSigningKey
)
$ErrorActionPreference = "Stop"

# Values go through a temp file and cmd's "<" so PowerShell can't add newlines or mangle quotes.
function Set-GhSecret([string]$Name, [string]$Value) {
    $tmp = [IO.Path]::GetTempFileName()
    try {
        [IO.File]::WriteAllText($tmp, $Value)
        cmd /c "gh secret set $Name < `"$tmp`""
        if ($LASTEXITCODE -ne 0) { throw "gh secret set $Name failed" }
    } finally {
        Remove-Item $tmp -Force -ErrorAction SilentlyContinue
    }
}

function Read-JsonFile([string]$Path, [string]$What) {
    if (-not (Test-Path $Path)) { throw "$What not found: $Path" }
    $raw = [IO.File]::ReadAllText((Resolve-Path $Path))
    $null = $raw | ConvertFrom-Json   # fails loudly if it isn't valid JSON
    return $raw
}

if (-not $SkipSigningKey) {
    $props = @{}
    Get-Content (Join-Path $KeyDir "keystore.properties") | ForEach-Object {
        if ($_ -match '^\s*([^#=]+?)\s*=\s*(.*)$') { $props[$matches[1]] = $matches[2] }
    }
    $jks = Join-Path $KeyDir "workbid-upload.jks"
    Set-GhSecret "UPLOAD_KEYSTORE_BASE64" ([Convert]::ToBase64String([IO.File]::ReadAllBytes($jks)))
    Set-GhSecret "KEYSTORE_PASSWORD" $props["storePassword"]
    Set-GhSecret "KEY_ALIAS" $props["keyAlias"]
    Set-GhSecret "KEY_PASSWORD" $props["keyPassword"]
    Write-Host "Signing key stored."
}
if ($PlayJson) {
    Set-GhSecret "PLAY_SERVICE_ACCOUNT_JSON" (Read-JsonFile $PlayJson "Play service account key")
    Write-Host "Google Play key stored."
}
if ($FirebaseJson) {
    Set-GhSecret "FIREBASE_SERVICE_ACCOUNT_JSON" (Read-JsonFile $FirebaseJson "Firebase service account key")
    Write-Host "Firebase key stored."
}
if ($GoogleServicesJson) {
    Set-GhSecret "GOOGLE_SERVICES_JSON" (Read-JsonFile $GoogleServicesJson "google-services.json")
    Write-Host "google-services.json stored."
}
Write-Host ""
gh secret list
