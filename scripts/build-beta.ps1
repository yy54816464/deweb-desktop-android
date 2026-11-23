$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$outDir = if ($env:DEWEB_OUT_DIR) { $env:DEWEB_OUT_DIR } else { Join-Path (Split-Path -Parent $root) 'release-output' }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) { $env:JAVA_HOME = (Join-Path (Split-Path -Parent $root) 'toolchain\jdk\PFiles64\Microsoft\jdk-17.0.21.21-hotspot') }
if (-not $env:ANDROID_HOME -or -not (Test-Path "$env:ANDROID_HOME\platform-tools")) { $env:ANDROID_HOME = (Join-Path (Split-Path -Parent $root) 'toolchain\sdk') }
if (-not $env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME }
$gradle = Join-Path (Split-Path -Parent $root) 'toolchain\gradle-8.9\bin\gradle.bat'
if (-not (Test-Path $gradle)) { $gradle = "$root\gradlew.bat" }

Push-Location $root
try {
    & $gradle :app:assembleBetaRelease --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'gradle failed' }
    $ver = (Select-String -Path "$root\app\build.gradle.kts" -Pattern 'versionName\s*=\s*"([^"]+)"').Matches[0].Groups[1].Value
    $src = "$root\app\build\outputs\apk\beta\release\app-beta-release.apk"
    New-Item -ItemType Directory -Force $outDir | Out-Null
    $dst = Join-Path $outDir "DeWEB-Beta-$ver-beta-signed.apk"
    Copy-Item $src $dst -Force
    Write-Output "BETA   $dst"
    Write-Output "SHA256 $((Get-FileHash $dst -Algorithm SHA256).Hash.ToLower())"
} finally { Pop-Location }
