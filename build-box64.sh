#!/bin/bash
set -e

# ==============================================================================
# Box64 Build & WCP Packaging Script with profile.json
# ==============================================================================

# Script Configuration
BUILD_DIR="${HOME}/box64_build"
INSTALL_PREFIX="/usr/local"
VERSION_NAME="0.4.3"
OUTPUT_WCP="${HOME}/box64-${VERSION_NAME}.wcp"
BOX64_REPO="https://github.com/ptitSeb/box64.git"
BOX64_BRANCH="main"

echo "=================================================="
echo "          Box64 Build Pipeline Starting          "
echo "=================================================="

# 1. Update system and install dependencies
echo "[1/5] Installing build dependencies..."
if command -v apt &> /dev/null; then
    sudo apt update
    sudo apt install -y \
        git \
        build-essential \
        cmake \
        ccache \
        python3 \
        zstd \
        tar \
        libzydis-dev \
        gcc-aarch64-linux-gnu || true
fi

# 2. Setup build directory & clone repository
echo "[2/5] Fetching Box64 source code..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
git clone --depth 1 --branch "$BOX64_BRANCH" "$BOX64_REPO" "$BUILD_DIR/source"

# 3. Configure CMake
echo "[3/5] Configuring CMake build flags..."
mkdir -p "$BUILD_DIR/source/build"
cd "$BUILD_DIR/source/build"

cmake .. \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$INSTALL_PREFIX" \
    -DARM_DYNAREC=ON \
    -DBOX32=ON \
    -DBOX32_BINFMT=ON \
    -DBAD_SIGNAL=ON \
    -DNO_ASM=OFF

# 4. Compile Box64
echo "[4/5] Compiling Box64..."
NPROC=$(nproc 2>/dev/null || echo 4)
make -j"$NPROC"

echo "[+] Installing Box64 to $INSTALL_PREFIX..."
sudo make install

# 5. Create profile.json & Package .wcp
echo "[5/5] Generating profile.json and building .wcp archive..."
STAGING_DIR=$(mktemp -d)

# Copy the box64 binary to root of staging directory
cp "${INSTALL_PREFIX}/bin/box64" "${STAGING_DIR}/box64"

# Create the profile.json manifest
cat << 'EOF' > "${STAGING_DIR}/profile.json"
{
  "type": "Box64",
  "versionName": "0.4.3",
  "versionCode": 0,
  "description": "0.4.3 Box64 Linux Userspace x86-64 Emulator with a Twist",
  "author": "ptitSeb",
  "files": [
    {
      "source": "box64",
      "target": "${localbin}/box64"
    }
  ]
}
EOF

# Compress files sitting at root into the .wcp package
cd "$STAGING_DIR"
tar -I 'zstd -19 -T0' -cf "$OUTPUT_WCP" box64 profile.json

# Clean up staging directory
rm -rf "$STAGING_DIR"

echo "=================================================="
echo "               BUILD SUCCESSFUL!                  "
echo "=================================================="
echo "[+] Installed Binary Location : ${INSTALL_PREFIX}/bin/box64"
echo "[+] Winlator Package (.wcp)   : ${OUTPUT_WCP}"
echo "=================================================="

# Verify binary
"${INSTALL_PREFIX}/bin/box64" -v