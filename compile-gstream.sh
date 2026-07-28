#!/usr/bin/env bash
set -e

# ==========================================
# Configuration Variables
# ==========================================
PACKAGE_NAME="com.winlator"

TARGET_DIR="/data/data/${PACKAGE_NAME}/files/rootfs"
BUILD_WORK_DIR="/tmp/gstreamer_only_build"

GST_VER="1.24.2"
CUSTOM_TAG="24.04-gstreamer-only"

# Dynamic Linker Path Setup for Android PRoot Execution
LD_RPATH="${TARGET_DIR}/usr/lib"
LD_FILE="${TARGET_DIR}/usr/lib/ld-linux-aarch64.so.1"

# Non-interactive mode for Docker container
export DEBIAN_FRONTEND=noninteractive

# Set build date in Asia/Shanghai timezone
export BUILD_DATE=$(TZ=Asia/Shanghai date '+%Y-%m-%d %H:%M:%S')

# ==========================================
# 0. Install Build Dependencies inside Container
# ==========================================
echo "[+] Installing GStreamer build tools and FFmpeg / x264 dev libraries..."
apt-get update && apt-get install -y --no-install-recommends \
    patchelf meson ninja-build wget git tar zstd build-essential file \
    libavcodec-dev libavformat-dev libavutil-dev libswscale-dev libavfilter-dev \
    libx264-dev autoconf automake libtool po4a libudev-dev ca-certificates \
    doxygen graphviz tzdata

# ==========================================
# 1. Clean & Prepare Work Directories
# ==========================================
echo "[+] Initializing output directories..."
rm -rf "${BUILD_WORK_DIR}" "${TARGET_DIR}"
mkdir -p "${TARGET_DIR}" "${BUILD_WORK_DIR}"
cd "${BUILD_WORK_DIR}"

# ==========================================
# 2. Compile GStreamer (FFmpeg, H.264, x264, WMA)
# ==========================================
echo "[+] Compiling GStreamer (${GST_VER}) with FFmpeg, H.264 & WMA..."
git clone -b "${GST_VER}" --depth 1 https://github.com/GStreamer/gstreamer.git gst-src
cd gst-src

meson setup builddir \
    --prefix="${TARGET_DIR}/usr" \
    --buildtype=release \
    --strip \
    --wrap-mode=nopromote \
    -Dgpl=enabled \
    -Dgst-full-target-type=shared_library \
    -Dgst-full-libraries=app,video,player,audio,tag,pbutils \
    -Dbase=enabled \
    -Dgood=enabled \
    -Dbad=enabled \
    -Dugly=enabled \
    -Dlibav=enabled \
    -Dgst-plugins-base:gl=disabled \
    -Dgst-plugins-base:x11=disabled \
    -Dgst-plugins-base:alsa=disabled \
    -Dgst-plugins-good:cairo=disabled \
    -Dgst-plugins-good:v4l2=disabled \
    -Dgst-libav:ffmpeg=enabled \
    -Dgst-plugins-ugly:x264=enabled \
    -Dintrospection=disabled \
    -Dtests=disabled \
    -Dexamples=disabled \
    -Ddoc=disabled \
    -Dges=disabled \
    -Dpython=disabled \
    -Ddevtools=disabled

ninja -C builddir install
cd "${BUILD_WORK_DIR}"

# ==========================================
# 3. Strip Debug Symbols
# ==========================================
echo "[+] Stripping debug symbols from GStreamer binaries and libraries..."
find "${TARGET_DIR}/usr/lib" "${TARGET_DIR}/usr/bin" -type f 2>/dev/null | while read -r bin; do
    if file -b "$bin" | grep -q "^ELF"; then
        strip --strip-unneeded "$bin" 2>/dev/null || true
    fi
done

# ==========================================
# 4. Safe Dynamic Linker Patching (patchelf)
# ==========================================
echo "[+] Applying patchelf to built binaries..."

patch_elf_safe() {
    local target="$1"
    
    # 1. Skip symlinks or missing files
    if [ ! -f "$target" ] || [ -L "$target" ]; then
        return 0
    fi

    # 2. Skip ASCII text files (GNU ld scripts like libc.so, libpthread.so)
    local file_type
    file_type=$(file -b "$target" 2>/dev/null || true)
    if echo "$file_type" | grep -q "text"; then
        return 0
    fi

    # 3. Patch valid 64-bit ELF binaries only
    if echo "$file_type" | grep -qE "ELF 64-bit.*(shared object|executable)"; then
        patchelf --set-rpath "${LD_RPATH}" --set-interpreter "${LD_FILE}" "$target" 2>/dev/null || true
    fi
}

# Patch GStreamer plugins and binaries
if [ -d "${TARGET_DIR}/usr/lib/gstreamer-1.0" ]; then
    find "${TARGET_DIR}/usr/lib/gstreamer-1.0" -name "*.so" | while read -r plugin; do
        patch_elf_safe "$plugin"
    done
fi

find "${TARGET_DIR}/usr/bin" "${TARGET_DIR}/usr/lib" -type f 2>/dev/null | while read -r candidate; do
    patch_elf_safe "$candidate"
done

chmod +x "${TARGET_DIR}/usr/bin/"* 2>/dev/null || true

# ==========================================
# 5. Generate Version File & Package Output
# ==========================================
echo "[+] Generating _version_.txt..."

cat > "${TARGET_DIR}/_version_.txt" << EOF
Output Date(CST): ${BUILD_DATE}
Type: Standalone-GStreamer-Build
Version: gstreamer=> ${GST_VER}, tag=> ${CUSTOM_TAG}
Features: FFmpeg (libavcodec), H.264 (Decode/Encode), x264, WMA (v1/v2/Pro)
Package Target: ${PACKAGE_NAME}
EOF

echo "[+] Packaging into rootfs.tzst..."
OUTPUT_TZST="${BUILD_WORK_DIR}/rootfs.tzst"
cd "${TARGET_DIR}"
tar --zstd -cvf "${OUTPUT_TZST}" .

echo "=========================================="
echo " GSTREAMER BUILD SUCCESSFUL!"
echo " Output Date: ${BUILD_DATE}"
echo " Output Path: ${OUTPUT_TZST}"
echo " Archive Size: $(du -sh ${OUTPUT_TZST} | cut -f1)"
echo "=========================================="