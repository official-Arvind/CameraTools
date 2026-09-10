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
    --version-code 101 `
    --version-name "1.1.0" `
    -o "$buildDir\unaligned.apk" `
    $flatFiles

Write-Host "[3/6] Compiling Java source files..."
$javaFiles = @(
    Get-ChildItem -Recurse "app\src\main\java\*.java" | ForEach-Object { $_.FullName }
    Get-ChildItem -Recurse "$buildDir\gen\*.java" | ForEach-Object { $_.FullName }
    Get-ChildItem -Recurse "stubs\src\*.java" | ForEach-Object { $_.FullName }
)

javac -cp "$androidJar" -d "$buildDir\classes" -source 1.8 -target 1.8 $javaFiles

Write-Host "[4/6] Isolating module classes (excluding Xposed API stubs)..."
Copy-Item -Recurse "$buildDir\classes\com" "$buildDir\classes_mod\"

Write-Host "[5/6] Converting to Dalvik Executable (DEX) with D8 (v36)..."
$classFiles = Get-ChildItem -Recurse "$buildDir\classes_mod\*.class" | ForEach-Object { $_.FullName }
& $d8 --release --min-api 29 --lib $androidJar --output "$buildDir" $classFiles

Write-Host "[6/6] Packaging DEX and assets/xposed_init into APK via Python..."
python -c "
import zipfile
apk = r'$buildDir\unaligned.apk'
dex = r'$buildDir\classes.dex'
init = r'app\src\main\assets\xposed_init'
with zipfile.ZipFile(apk, 'a') as z:
    z.write(dex, 'classes.dex')
    z.write(init, 'assets/xposed_init')
print('Injected classes.dex and assets/xposed_init successfully')
"

Write-Host "[7/7] Aligning & Signing with Arvind's release keystore..."
$outApk = "release\com.jigar.cameratools-v1.1.0.apk"
if (Test-Path $outApk) { Remove-Item -Force $outApk }
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

Remove-Item -Recurse -Force $buildDir
Write-Host "SUCCESS! Signed release APK generated at: $outApk"
