param(
    [string]$AndroidSdk = 'C:\Android',
    [string]$JavaHome = $env:JAVA_HOME
)

$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
$platform = Join-Path $AndroidSdk 'platforms\android-35\android.jar'
$d8 = Join-Path $AndroidSdk 'build-tools\35.0.1\d8.bat'
$javac = if ($JavaHome) { Join-Path $JavaHome 'bin\javac.exe' } else { 'javac' }
$classDir = Join-Path $repo 'build\classes'
$dexDir = Join-Path $repo 'build\module'

if (!(Test-Path -LiteralPath $platform)) { throw "Missing Android SDK platform: $platform" }
if (!(Test-Path -LiteralPath $d8)) { throw "Missing d8: $d8" }
$resolvedRepo = [IO.Path]::GetFullPath($repo).TrimEnd([IO.Path]::DirectorySeparatorChar)
$resolvedClasses = [IO.Path]::GetFullPath($classDir)
if (!$resolvedClasses.StartsWith($resolvedRepo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe class directory' }
if (Test-Path -LiteralPath $classDir) { Remove-Item -LiteralPath $classDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $classDir, $dexDir | Out-Null
$sources = @(Get-ChildItem -LiteralPath (Join-Path $repo 'src\main\java') -Recurse -File -Filter '*.java' | ForEach-Object FullName)
if ($sources.Count -eq 0) { throw 'No Java source files' }
& $javac -source 8 -target 8 -Xlint:-options -cp $platform -d $classDir $sources
if ($LASTEXITCODE -ne 0) { throw 'javac failed' }
$classes = @(Get-ChildItem -LiteralPath $classDir -Recurse -File -Filter '*.class' | ForEach-Object FullName)
& $d8 --min-api 29 --lib $platform --output $dexDir $classes
if ($LASTEXITCODE -ne 0) { throw 'd8 failed' }
Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $dexDir 'classes.dex')
