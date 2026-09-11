$ErrorActionPreference = "Stop"

$sdkTools34 = "C:\Users\Arvind\AppData\Local\Android\Sdk\build-tools\34.0.0"
$sdkTools36 = "C:\Users\Arvind\AppData\Local\Android\Sdk\build-tools\36.0.0"
$androidJar = "C:\Users\Arvind\AppData\Local\Android\Sdk\platforms\android-34\android.jar"
$aapt2 = "$sdkTools34\aapt2.exe"
$d8 = "$sdkTools36\d8.bat"
$zipalign = "$sdkTools34\zipalign.exe"
$apksigner = "$sdkTools34\apksigner.bat"

$keystore = "D:\Desktop\UPIPaymentAlert_Keys\upipaymentalert-release.jks"
$storepass = "UPIAlert@Arvind2024"
$keyalias = "upipaymentalert"

$buildDir = "build_tmp"
if (Test-Path $buildDir) { Remove-Item -Recurse -Force $buildDir }
New-Item -ItemType Directory -Path "$buildDir\compiled_res" -Force
New-Item -ItemType Directory -Path "$buildDir\gen" -Force
New-Item -ItemType Directory -Path "$buildDir\classes" -Force
New-Item -ItemType Directory -Path "$buildDir\classes_mod" -Force

Write-Host "[1/6] Compiling Android resources with AAPT2..."
Get-ChildItem -Path "app\src\main\res" -Recurse -File | ForEach-Object {
    & $aapt2 compile $_.FullName -o "$buildDir\compiled_res"
}

Write-Host "[2/6] Linking resources & generating R.java..."
$flatFiles = Get-ChildItem "$buildDir\compiled_res\*.flat" | ForEach-Object { $_.FullName }
& $aapt2 link `
    -I $androidJar `
    --manifest "app\src\main\AndroidManifest.xml" `
    --java "$buildDir\gen" `
    --custom-package "io.github.official_arvind.cameratools" `
    --version-code 101 `
    --version-name "1.1.0" `
    -o "$buildDir\unaligned.apk" `
    $flatFiles
if ($LASTEXITCODE -ne 0) { throw "AAPT2 link failed!" }

Write-Host "[3/6] Compiling Java source files..."
$javaFiles = @(
    Get-ChildItem -Recurse "app\src\main\java\*.java" | ForEach-Object { $_.FullName }
    Get-ChildItem -Recurse "$buildDir\gen\*.java" | ForEach-Object { $_.FullName }
    Get-ChildItem -Recurse "stubs\src\*.java" | ForEach-Object { $_.FullName }
)

javac -cp "$androidJar" -d "$buildDir\classes" -source 1.8 -target 1.8 $javaFiles
if ($LASTEXITCODE -ne 0) { throw "Java compilation failed!" }

Write-Host "[4/6] Isolating module classes (excluding Xposed API stubs)..."
Get-ChildItem "$buildDir\classes" -Directory | Where-Object { $_.Name -ne "de" } | ForEach-Object {
    Copy-Item -Recurse $_.FullName "$buildDir\classes_mod\"
}

Write-Host "[5/6] Converting to Dalvik Executable (DEX) with D8 (v36)..."
$classFiles = Get-ChildItem -Recurse "$buildDir\classes_mod\*.class" | ForEach-Object { $_.FullName }
$d8ArgsFile = "$buildDir\d8_args.txt"
$classFiles | Out-File -FilePath $d8ArgsFile -Encoding ascii
& $d8 --release --min-api 29 --lib $androidJar --output "$buildDir" "@$d8ArgsFile"
if ($LASTEXITCODE -ne 0) { throw "D8 dex compilation failed!" }

Write-Host "[6/6] Packaging DEX and assets/xposed_init into APK via Python..."
python -c "
import zipfile, os
apk = r'$buildDir\unaligned.apk'
dex = r'$buildDir\classes.dex'
init = r'app\src\main\assets\xposed_init'
assert os.path.exists(dex), 'classes.dex not found!'
assert os.path.exists(init), 'assets/xposed_init not found!'
with zipfile.ZipFile(apk, 'a') as z:
    z.write(dex, 'classes.dex')
    z.write(init, 'assets/xposed_init')
print('Injected classes.dex and assets/xposed_init successfully')
"
if ($LASTEXITCODE -ne 0) { throw "Zip injection failed!" }

Write-Host "[7/7] Aligning & Signing with Arvind's release keystore..."
$outApk = "release\io.github.official_arvind.cameratools-v1.1.0.apk"
$legacyApk = "release\com.jigar.cameratools-v1.1.0.apk"
if (Test-Path $outApk) { Remove-Item -Force $outApk }
if (Test-Path $legacyApk) { Remove-Item -Force $legacyApk }
& $zipalign -p -f 4 "$buildDir\unaligned.apk" $outApk

& $apksigner sign `
    --ks "$keystore" `
    --ks-key-alias "$keyalias" `
    --ks-pass "pass:$storepass" `
    --key-pass "pass:$storepass" `
    --v1-signing-enabled true `
    --v2-signing-enabled true `
    --v3-signing-enabled true `
    $outApk

& $apksigner verify --verbose $outApk

Copy-Item $outApk $legacyApk -Force
Remove-Item -Recurse -Force $buildDir
Write-Host "SUCCESS! Signed release APK generated at: $outApk"
