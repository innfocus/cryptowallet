#!/bin/bash
set -euo pipefail

#===============================================================================
# release-ios.sh
#
# Script tu dong hoa release XCFramework len GitHub de SPM su dung.
#
# Flow:
#   1. Build XCFramework (release) bang Gradle
#   2. Zip XCFramework
#   3. Tinh SHA256 checksum
#   4. Tao GitHub Release + upload zip
#   5. Cap nhat Package.swift voi url va checksum moi
#   6. Commit & push Package.swift
#
# Yeu cau:
#   - GitHub CLI (gh): brew install gh && gh auth login
#   - Quyen push len repo
#
# Su dung:
#   ./scripts/release-ios.sh              # Dung version tu gradle.properties
#   ./scripts/release-ios.sh 1.2.3        # Chi dinh version cu the
#===============================================================================

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$REPO_ROOT"

# --- Kiem tra gh CLI ---
if ! command -v gh &>/dev/null; then
    echo "ERROR: GitHub CLI (gh) chua duoc cai dat."
    echo "Cai dat: brew install gh"
    echo "Dang nhap: gh auth login"
    exit 1
fi

# --- Xac dinh version ---
if [ -n "${1:-}" ]; then
    VERSION="$1"
else
    VERSION=$(grep "^VERSION_NAME=" gradle.properties | cut -d'=' -f2)
    if [ -z "$VERSION" ]; then
        echo "ERROR: Khong tim thay VERSION_NAME trong gradle.properties"
        exit 1
    fi
fi

TAG="v${VERSION}"
FRAMEWORK_NAME="crypto-wallet-lib"
XCFRAMEWORK_DIR="crypto-wallet-lib/build/XCFrameworks/release"
XCFRAMEWORK_PATH="${XCFRAMEWORK_DIR}/${FRAMEWORK_NAME}.xcframework"
ZIP_NAME="${FRAMEWORK_NAME}.xcframework.zip"
ZIP_PATH="${REPO_ROOT}/${ZIP_NAME}"
GITHUB_REPO=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo "")

if [ -z "$GITHUB_REPO" ]; then
    echo "ERROR: Khong xac dinh duoc GitHub repo. Chay 'gh auth login' truoc."
    exit 1
fi

echo "============================================"
echo "  iOS XCFramework Release"
echo "============================================"
echo "  Version:  ${VERSION}"
echo "  Tag:      ${TAG}"
echo "  Repo:     ${GITHUB_REPO}"
echo "============================================"
echo ""

# --- Buoc 1: Build XCFramework ---
echo "[1/6] Building XCFramework (release)..."
./gradlew ":crypto-wallet-lib:assemble${FRAMEWORK_NAME}ReleaseXCFramework" --no-configuration-cache

if [ ! -d "$XCFRAMEWORK_PATH" ]; then
    echo "ERROR: XCFramework khong tim thay tai: $XCFRAMEWORK_PATH"
    echo "Kiem tra ten task Gradle bang: ./gradlew tasks --all | grep -i xcframework"
    exit 1
fi
echo "   -> Build thanh cong!"

# --- Buoc 2: Zip XCFramework ---
echo "[2/6] Zipping XCFramework..."
rm -f "$ZIP_PATH"
cd "$XCFRAMEWORK_DIR"
zip -r -q "$ZIP_PATH" "${FRAMEWORK_NAME}.xcframework"
cd "$REPO_ROOT"

ZIP_SIZE=$(du -h "$ZIP_PATH" | cut -f1)
echo "   -> ${ZIP_NAME} (${ZIP_SIZE})"

# --- Buoc 3: Tinh checksum ---
echo "[3/6] Tinh SHA256 checksum..."
CHECKSUM=$(swift package compute-checksum "$ZIP_PATH")
echo "   -> ${CHECKSUM}"

# --- Buoc 4: Tao GitHub Release ---
echo "[4/6] Tao GitHub Release ${TAG}..."

# Kiem tra tag da ton tai chua
if gh release view "$TAG" &>/dev/null; then
    echo "   -> Release ${TAG} da ton tai. Xoa va tao lai..."
    gh release delete "$TAG" --yes --cleanup-tag
fi

gh release create "$TAG" \
    "$ZIP_PATH" \
    --title "Release ${TAG}" \
    --notes "iOS XCFramework release ${VERSION}" \
    --latest

DOWNLOAD_URL="https://github.com/${GITHUB_REPO}/releases/download/${TAG}/${ZIP_NAME}"
echo "   -> Upload thanh cong!"
echo "   -> URL: ${DOWNLOAD_URL}"

# --- Buoc 5: Cap nhat Package.swift ---
echo "[5/6] Cap nhat Package.swift..."
cat > Package.swift << SWIFT
// swift-tools-version:5.3
import PackageDescription

let package = Package(
   name: "${FRAMEWORK_NAME}",
   products: [
      .library(name: "${FRAMEWORK_NAME}", targets: ["${FRAMEWORK_NAME}"])
   ],
   targets: [
      .binaryTarget(
         name: "${FRAMEWORK_NAME}",
         url: "${DOWNLOAD_URL}",
         checksum: "${CHECKSUM}"
      )
   ]
)
SWIFT
echo "   -> Package.swift da duoc cap nhat!"

# --- Buoc 6: Commit & Push ---
echo "[6/6] Commit va push Package.swift..."
git add Package.swift
if git diff --cached --quiet Package.swift; then
    echo "   -> Package.swift khong thay doi, bo qua commit."
else
    git commit -m "Update Package.swift for ${TAG}"
    git push
    echo "   -> Da push len remote!"
fi

# --- Don dep file zip local ---
rm -f "$ZIP_PATH"

echo ""
echo "============================================"
echo "  HOAN TAT!"
echo "============================================"
echo "  Tag:      ${TAG}"
echo "  URL:      ${DOWNLOAD_URL}"
echo "  Checksum: ${CHECKSUM}"
echo ""
echo "  iOS project them dependency:"
echo "    Xcode > File > Add Package Dependencies"
echo "    URL: https://github.com/${GITHUB_REPO}"
echo "    Version: ${VERSION}"
echo "============================================"
