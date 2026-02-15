param(
    [Parameter(Mandatory = $true)]
    [string]$AppName,

    [Parameter(Mandatory = $true)]
    [string]$AppUrl,

    [Parameter(Mandatory = $true)]
    [string]$PackageSuffix,

    [string]$VersionName = "1.0.0",
    [int]$VersionCode = 1
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($AppUrl)) {
    throw "AppUrl is required."
}
if (-not $AppUrl.StartsWith("https://") -and -not $AppUrl.StartsWith("http://")) {
    throw "AppUrl must start with http:// or https://."
}
if ([string]::IsNullOrWhiteSpace($PackageSuffix)) {
    throw "PackageSuffix is required."
}

function Ensure-LocalSigningConfig {
    $hasEnvSigning =
        -not [string]::IsNullOrWhiteSpace($env:SIGNING_KEYSTORE_PATH) -and
            -not [string]::IsNullOrWhiteSpace($env:SIGNING_STORE_PASSWORD) -and
            -not [string]::IsNullOrWhiteSpace($env:SIGNING_KEY_ALIAS) -and
            -not [string]::IsNullOrWhiteSpace($env:SIGNING_KEY_PASSWORD)
    if ($hasEnvSigning) {
        Write-Host "Using signing config from environment."
        return
    }

    $keytool = Get-Command keytool -ErrorAction SilentlyContinue
    if ($null -eq $keytool) {
        throw "keytool was not found. Install JDK 17+ or provide SIGNING_* environment variables."
    }

    $repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
    $signingDir = Join-Path $repoRoot ".tmp\runtime-signing"
    New-Item -ItemType Directory -Force -Path $signingDir | Out-Null
    $keystorePath = Join-Path $signingDir "runtime-shell-local.jks"
    $storePassword = "android"
    $keyAlias = "androiddebugkey"
    $keyPassword = "android"

    if (-not (Test-Path $keystorePath)) {
        Write-Host "Generating local runtime signing keystore..."
        & $keytool.Source `
            -genkeypair `
            -keystore $keystorePath `
            -storepass $storePassword `
            -alias $keyAlias `
            -keypass $keyPassword `
            -keyalg RSA `
            -keysize 2048 `
            -validity 10000 `
            -dname "CN=Android Runtime,OU=ZionChat,O=ZionChat,L=NA,S=NA,C=US" | Out-Null
        if ($LASTEXITCODE -ne 0) {
            throw "Failed to generate local runtime signing keystore."
        }
    }

    $env:SIGNING_KEYSTORE_PATH = (Resolve-Path $keystorePath).Path
    $env:SIGNING_STORE_PASSWORD = $storePassword
    $env:SIGNING_KEY_ALIAS = $keyAlias
    $env:SIGNING_KEY_PASSWORD = $keyPassword
    Write-Host "Using local signing keystore: $($env:SIGNING_KEYSTORE_PATH)"
}

Ensure-LocalSigningConfig

$args = @(
    ":runtime:assembleRelease",
    "--stacktrace",
    "-PRUNTIME_APP_NAME=$AppName",
    "-PRUNTIME_APP_URL=$AppUrl",
    "-PRUNTIME_PACKAGE_SUFFIX=$PackageSuffix",
    "-PRUNTIME_VERSION_NAME=$VersionName",
    "-PRUNTIME_VERSION_CODE=$VersionCode"
)

Write-Host "Packaging runtime APK..."
Write-Host "Name: $AppName"
Write-Host "URL : $AppUrl"
Write-Host "ID  : $PackageSuffix"

& .\gradlew.bat @args

if ($LASTEXITCODE -ne 0) {
    throw "Gradle packaging failed with exit code $LASTEXITCODE."
}

$apkOutputDir = "runtime\build\outputs\apk\release"
if (-not (Test-Path $apkOutputDir)) {
    throw "APK output directory was not found: $apkOutputDir"
}

$apkFile = Get-ChildItem -Path $apkOutputDir -Filter *.apk -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if ($null -eq $apkFile) {
    throw "APK output file was not found in: $apkOutputDir"
}

Write-Host "Done. APK output path: $($apkFile.FullName)"
