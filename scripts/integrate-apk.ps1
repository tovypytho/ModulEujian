param(
    [Parameter(Mandatory = $true)][string]$BaseApk,
    [Parameter(Mandatory = $true)][string]$Keystore,
    [Parameter(Mandatory = $true)][string]$OutputDirectory,
    [string]$AndroidSdk = 'C:\Android',
    [string]$Apktool = 'C:\Tools\apktool\apktool.bat',
    [string]$ExpectedBaseSha256 = 'F23B7C5AE44BE920FA0BB28979DC0CC4B3AF9E5D1AB0FF4467381CE7B7D74D1C'
)

$ErrorActionPreference = 'Stop'
if (!$env:EUJIAN_KS_PASS) { throw 'Set EUJIAN_KS_PASS in the local environment first' }
$base = (Resolve-Path -LiteralPath $BaseApk).Path
$key = (Resolve-Path -LiteralPath $Keystore).Path
$actual = (Get-FileHash -Algorithm SHA256 -LiteralPath $base).Hash
if ($actual -ne $ExpectedBaseSha256) { throw "Unexpected base SHA-256: $actual" }

$repo = Split-Path -Parent $PSScriptRoot
$out = [IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Force -Path $out | Out-Null
$backup = Join-Path $out 'baseline_backup.apk'
Copy-Item -LiteralPath $base -Destination $backup -Force
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $backup).Hash -ne $actual) { throw 'Backup hash mismatch' }

& (Join-Path $PSScriptRoot 'build-module.ps1') -AndroidSdk $AndroidSdk
if ($LASTEXITCODE -and $LASTEXITCODE -ne 0) { throw 'Module build failed' }

$decoded = Join-Path $out 'decoded'
& $Apktool d -f $backup -o $decoded
if ($LASTEXITCODE -ne 0) { throw 'apktool decode failed' }
$activity = Join-Path $decoded 'smali\id\exambro\cbt\MainActivity.smali'
$source = [IO.File]::ReadAllText($activity)
$anchor = '    invoke-virtual {p1, p0}, Lid/exambro/cbt/GateJniBridge;->attach(Landroid/app/Activity;)V'
$hook = '    invoke-static {p0}, Lid/eujian/cbt/capture/CaptureModule;->install(Landroid/app/Activity;)V'
if ($source.Contains($hook)) { throw 'Capture hook already present' }
if (([regex]::Matches($source, [regex]::Escape($anchor))).Count -ne 1) { throw 'Unexpected MainActivity anchor count' }
$newline = if ($source.Contains("`r`n")) { "`r`n" } else { "`n" }
$patched = $source.Replace($anchor, $anchor + $newline + $newline + $hook)
[IO.File]::WriteAllText($activity, $patched, [Text.UTF8Encoding]::new($false))

$unsigned = Join-Path $out 'unsigned.apk'
& $Apktool b $decoded -o $unsigned
if ($LASTEXITCODE -ne 0) { throw 'apktool build failed' }
Add-Type -AssemblyName System.IO.Compression
$dex = Join-Path $repo 'build\module\classes.dex'
$zip = [IO.Compression.ZipFile]::Open($unsigned, [IO.Compression.ZipArchiveMode]::Update)
try {
    if ($zip.GetEntry('classes3.dex')) { throw 'classes3.dex already present in base' }
    [IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $dex, 'classes3.dex', [IO.Compression.CompressionLevel]::NoCompression) | Out-Null
} finally { $zip.Dispose() }

$zipalign = Join-Path $AndroidSdk 'build-tools\35.0.1\zipalign.exe'
$apksigner = Join-Path $AndroidSdk 'build-tools\35.0.1\apksigner.bat'
$aligned = Join-Path $out 'aligned.apk'
$candidate = Join-Path $out 'E-Ujian_capture_gemini_candidate.apk'
& $zipalign -P 16 -f 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& $apksigner sign --ks $key --ks-key-alias androiddebugkey --ks-pass env:EUJIAN_KS_PASS --key-pass env:EUJIAN_KS_PASS --v2-signing-enabled true --v3-signing-enabled true --out $candidate $aligned
if ($LASTEXITCODE -ne 0) { throw 'signing failed' }
& $zipalign -c -P 16 4 $candidate
if ($LASTEXITCODE -ne 0) { throw 'alignment verification failed' }
& $apksigner verify --verbose --print-certs $candidate
if ($LASTEXITCODE -ne 0) { throw 'signature verification failed' }
$baseCert = (& $apksigner verify --print-certs $backup | Select-String 'Signer #1 certificate SHA-256 digest:').ToString().Split(':')[-1].Trim()
$newCert = (& $apksigner verify --print-certs $candidate | Select-String 'Signer #1 certificate SHA-256 digest:').ToString().Split(':')[-1].Trim()
if ($baseCert -ne $newCert) { throw "Signing certificate mismatch: $newCert" }
& python (Join-Path $PSScriptRoot 'verify_apk.py') $backup $candidate
if ($LASTEXITCODE -ne 0) { throw 'APK entry verification failed' }
Get-FileHash -Algorithm SHA256 -LiteralPath $candidate
