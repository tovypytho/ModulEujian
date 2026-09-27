param([string]$AndroidSdk = 'C:\Android', [string]$Keystore = 'C:\Users\Administrator\Downloads\E-Ujian\clean_paste_workbench_20260927\debug.keystore')
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$tools = Join-Path $AndroidSdk 'build-tools\35.0.1'
$jar = Join-Path $AndroidSdk 'platforms\android-35\android.jar'
$out = Join-Path $root 'build'
New-Item -ItemType Directory -Force -Path (Join-Path $out 'classes'), (Join-Path $out 'dex') | Out-Null
& javac -source 8 -target 8 -Xlint:-options -cp $jar -d (Join-Path $out 'classes') (Join-Path $root 'src\id\eujian\capture\settings\SettingsActivity.java')
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$classes = @(Get-ChildItem -LiteralPath (Join-Path $out 'classes') -Recurse -File -Filter '*.class' | ForEach-Object FullName)
& (Join-Path $tools 'd8.bat') --min-api 29 --lib $jar --output (Join-Path $out 'dex') $classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
& (Join-Path $tools 'aapt2.exe') link --manifest (Join-Path $root 'AndroidManifest.xml') -I $jar --min-sdk-version 29 --target-sdk-version 35 -o (Join-Path $out 'unsigned.apk')
if ($LASTEXITCODE -ne 0) { throw 'aapt2 failed' }
@'
import sys,zipfile
apk,dex=sys.argv[1:]
with zipfile.ZipFile(apk,'a',zipfile.ZIP_DEFLATED) as z:
    z.write(dex,'classes.dex')
'@ | python - (Join-Path $out 'unsigned.apk') (Join-Path $out 'dex\classes.dex')
if ($LASTEXITCODE -ne 0) { throw 'zip failed' }
& (Join-Path $tools 'zipalign.exe') -f -P 16 4 (Join-Path $out 'unsigned.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'zipalign failed' }
& (Join-Path $tools 'apksigner.bat') sign --ks $Keystore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --out (Join-Path $out 'E-Ujian-Settings.apk') (Join-Path $out 'aligned.apk')
if ($LASTEXITCODE -ne 0) { throw 'sign failed' }
& (Join-Path $tools 'apksigner.bat') verify --verbose (Join-Path $out 'E-Ujian-Settings.apk')
if ($LASTEXITCODE -ne 0) { throw 'verify failed' }
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $out 'E-Ujian-Settings.apk')
