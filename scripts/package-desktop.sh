#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DESKTOP_DIR="$PROJECT_DIR/desktop"
INPUT_DIR="$DESKTOP_DIR/target/app-input"
OUTPUT_DIR="$DESKTOP_DIR/dist"
PACKAGE_DIR="$(mktemp -d "$DESKTOP_DIR/target/jpackage.XXXXXX")"

case "$(uname -m)" in
  arm64|aarch64)
    OPENCV_NATIVE_PATH="nu/pattern/opencv/osx/ARMv8/libopencv_java490.dylib"
    SWIFT_TARGET="arm64-apple-macosx14.0"
    ;;
  x86_64)
    OPENCV_NATIVE_PATH="nu/pattern/opencv/osx/x86_64/libopencv_java490.dylib"
    SWIFT_TARGET="x86_64-apple-macosx14.0"
    ;;
  *) echo "Unsupported macOS architecture: $(uname -m)" >&2; exit 1 ;;
esac

(cd "$PROJECT_DIR" && ./mvnw clean -DskipTests package)
(cd "$DESKTOP_DIR" && ../mvnw -DskipTests package)
cp "$DESKTOP_DIR/target/omr-desktop-1.0.0.jar" "$INPUT_DIR/omr-desktop-1.0.0.jar"
mkdir -p "$OUTPUT_DIR"
jpackage \
  --type app-image \
  --name "OMR Scanner" \
  --app-version 1.0.0 \
  --input "$INPUT_DIR" \
  --main-jar omr-desktop-1.0.0.jar \
  --main-class com.omr.desktop.DesktopLauncher \
  --java-options "--module-path=\$APPDIR --add-modules=javafx.controls,javafx.web -Domr.opencv.library=\$APPDIR/libopencv_java490.dylib -Domr.camera.helper=\$APPDIR/camera-capture" \
  --dest "$PACKAGE_DIR"

APP_BUNDLE="$PACKAGE_DIR/OMR Scanner.app"
unzip -p "$INPUT_DIR/opencv-4.9.0-0.jar" "$OPENCV_NATIVE_PATH" > "$APP_BUNDLE/Contents/app/libopencv_java490.dylib"
swiftc -sdk /Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk \
  -module-cache-path "$DESKTOP_DIR/target/swift-module-cache" \
  -target "$SWIFT_TARGET" -O \
  -o "$APP_BUNDLE/Contents/app/camera-capture" "$DESKTOP_DIR/src/main/swift/CameraCapture.swift"
INFO_PLIST="$APP_BUNDLE/Contents/Info.plist"
if /usr/libexec/PlistBuddy -c 'Print :NSCameraUsageDescription' "$INFO_PLIST" >/dev/null 2>&1; then
  /usr/libexec/PlistBuddy -c 'Set :NSCameraUsageDescription OMR Scanner uses the camera to capture and grade answer sheets.' "$INFO_PLIST"
else
  /usr/libexec/PlistBuddy -c 'Add :NSCameraUsageDescription string OMR Scanner uses the camera to capture and grade answer sheets.' "$INFO_PLIST"
fi
/usr/bin/codesign --force --deep --sign - "$APP_BUNDLE"

mkdir -p "$OUTPUT_DIR"
if [ -e "$OUTPUT_DIR/OMR Scanner.app" ]; then
  BACKUP_DIR="$(mktemp -d "$DESKTOP_DIR/target/previous-app.XXXXXX")"
  mv "$OUTPUT_DIR/OMR Scanner.app" "$BACKUP_DIR/"
fi
mv "$APP_BUNDLE" "$OUTPUT_DIR/"
