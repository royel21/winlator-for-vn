#!/usr/bin/env bash
set -e

# ==========================================
# Configuration Variables
# ==========================================
# Target Android package name
PACKAGE_NAME="com.winlator"

TARGET_DIR="/data/data/${PACKAGE_NAME}/files/rootfs"
BUILD_WORK_DIR="/tmp/minimal_rootfs_2404_build"

GST_VER="1.24.2"
XZ_VER="v5.6.1"
CUSTOM_TAG="24.04-minimal-ffmpeg"

# Dynamic Linker Path Setup for Android PRoot Execution
LD_RPATH="${TARGET_DIR}/usr/lib"
LD_FILE="${TARGET_DIR}/usr/lib/ld-linux-aarch64.so.1"

# ==========================================
# 0. Install Host Dependencies & Headers
# ==========================================
echo "[+] Installing build tools, doxygen, po4a, and FFmpeg / x264 headers..."
apt-get update && apt-get install -y --no-install-recommends \
    debootstrap patchelf meson ninja-build wget git tar zstd build-essential file \
    libavcodec-dev libavformat-dev libavutil-dev libswscale-dev libavfilter-dev \
    libx264-dev autoconf automake libtool po4a libudev-dev ca-certificates \
    doxygen graphviz

# ==========================================
# 1. Clean & Bootstrap Ubuntu 24.04 (Noble) Base
# ==========================================
echo "[+] Initializing build directories..."
rm -rf "${BUILD_WORK_DIR}" "${TARGET_DIR}"
mkdir -p "${TARGET_DIR}" "${BUILD_WORK_DIR}"
cd "${BUILD_WORK_DIR}"

echo "[+] Bootstrapping minimal Ubuntu 24.04 LTS (Noble) ARM64 rootfs..."
debootstrap --variant=minbase --arch=arm64 noble "${TARGET_DIR}" http://ports.ubuntu.com/ubuntu-ports

# Setup essential network files & CA certificates
mkdir -p "${TARGET_DIR}/etc/ca-certificates"
cp /etc/resolv.conf "${TARGET_DIR}/etc/resolv.conf" 2>/dev/null || true
wget -q https://curl.haxx.se/ca/cacert.pem -O "${TARGET_DIR}/etc/ca-certificates/cacert.pem" || true

# ==========================================
# 2. Compile Minimal XZ / Liblzma
# ==========================================
echo "[+] Compiling minimal liblzma (${XZ_VER})..."
git clone -b "${XZ_VER}" --depth 1 https://github.com/tukaani-project/xz.git xz-src
cd xz-src
./autogen.sh
mkdir build && cd build
../configure --prefix="${TARGET_DIR}/usr" --disable-doc --disable-scripts --disable-nls
make -j$(nproc)
make install
cd "${BUILD_WORK_DIR}"

# ==========================================
# 3. Compile GStreamer (FFmpeg, H.264, x264, WMA)
# ==========================================
# ==========================================
# 3. Compile GStreamer (FFmpeg, H.264, x264, WMA)
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
# 4. Cleanup & Strip Debug Symbols
# ==========================================
echo "[+] Removing unused docs, caches, and locales..."
rm -rf "${TARGET_DIR}"/usr/share/doc/*
rm -rf "${TARGET_DIR}"/usr/share/man/*
rm -rf "${TARGET_DIR}"/usr/share/info/*
rm -rf "${TARGET_DIR}"/usr/share/locale/*
rm -rf "${TARGET_DIR}"/var/lib/apt/lists/*
rm -rf "${TARGET_DIR}"/var/cache/apt/*
rm -rf "${TARGET_DIR}"/tmp/*

echo "[+] Stripping debug symbols from dynamic binaries..."
find "${TARGET_DIR}/lib" "${TARGET_DIR}/usr/lib" "${TARGET_DIR}/bin" "${TARGET_DIR}/usr/bin" -type f 2>/dev/null | while read -r bin; do
    if file -b "$bin" | grep -q "^ELF"; then
        strip --strip-unneeded "$bin" 2>/dev/null || true
    fi
done

# ==========================================
# 5. Safe Dynamic Linker Patching (patchelf)
# ==========================================
echo "[+] Applying patchelf safely to true ELF binaries..."

patch_elf_safe() {
    local target="$1"
    # Ensure it's a regular file (not a symlink or ld script like libc.so)
    if [ -f "$target" ] && [ ! -L "$target" ]; then
        if file -b "$target" | grep -q "^ELF"; then
            patchelf --set-rpath "${LD_RPATH}" --set-interpreter "${LD_FILE}" "$target" 2>/dev/null || true
        fi
    fi
}

# Patch GStreamer plugins
if [ -d "${TARGET_DIR}/usr/lib/gstreamer-1.0" ]; then
    find "${TARGET_DIR}/usr/lib/gstreamer-1.0" -name "*.so" | while read -r plugin; do
        patch_elf_safe "$plugin"
    done
fi

# Patch overall system ELF binaries and shared objects safely
find "${TARGET_DIR}/usr/bin" "${TARGET_DIR}/bin" "${TARGET_DIR}/usr/lib" "${TARGET_DIR}/lib" -type f 2>/dev/null | while read -r candidate; do
    patch_elf_safe "$candidate"
done

chmod +x "${TARGET_DIR}/usr/bin/"* "${TARGET_DIR}/bin/"* 2>/dev/null || true

# ==========================================
# 6. Generate Version File & Compress Archive
# ==========================================
echo "[+] Generating _version_.txt..."
cat > "${TARGET_DIR}/_version_.txt" << EOF
Output Date(UTC): $(date -u)
Distro: Ubuntu 24.04 LTS (Noble)
Type: Minimal-RootFS
Version: gstreamer=> ${GST_VER}, xz=> ${XZ_VER}, tag=> ${CUSTOM_TAG}
Features: FFmpeg (libavcodec), H.264 (Decode/Encode), x264, WMA (v1/v2/Pro)
Package Target: ${PACKAGE_NAME}
EOF

echo "[+] Packaging into rootfs.tzst..."
OUTPUT_TZST="${BUILD_WORK_DIR}/rootfs.tzst"
cd "${TARGET_DIR}"
tar --zstd -cvf "${OUTPUT_TZST}" .

echo "=========================================="
echo " ROOTFS BUILD SUCCESSFUL!"
echo " Output Path: ${OUTPUT_TZST}"
echo " Archive Size: $(du -sh ${OUTPUT_TZST} | cut -f1)"
echo "=========================================="