#!/usr/bin/env bash
set -e

# ==========================================
# Configuration Variables
# ==========================================
# Change this to match your exact Android package name
PACKAGE_NAME="com.vnwinlator"

TARGET_DIR="/data/data/${PACKAGE_NAME}/files/rootfs"
BUILD_WORK_DIR="/tmp/minimal_rootfs_2404_build"

GST_VER="1.24.2"
XZ_VER="v5.6.1"
CUSTOM_TAG="24.04-minimal-ffmpeg"

# Dynamic Linker Path Setup for Android PRoot Execution
LD_RPATH="${TARGET_DIR}/lib"
LD_FILE="${LD_RPATH}/ld-linux-aarch64.so.1"

# ==========================================
# 0. Install Host Build Dependencies & Libraries
# ==========================================
echo "[+] Installing build tools and FFmpeg / x264 development headers..."
sudo apt-get update && sudo apt-get install -y \
    debootstrap patchelf meson ninja-build wget git tar zstd build-essential file \
    libavcodec-dev libavformat-dev libavutil-dev libswscale-dev libavfilter-dev \
    libx264-dev autoconf automake libtool

# ==========================================
# 1. Clean & Bootstrap Ubuntu 24.04 (Noble) Base
# ==========================================
echo "[+] Initializing directory structure..."
rm -rf "${BUILD_WORK_DIR}" "${TARGET_DIR}"
mkdir -p "${TARGET_DIR}" "${BUILD_WORK_DIR}"
cd "${BUILD_WORK_DIR}"

echo "[+] Bootstrapping minimal Ubuntu 24.04 LTS (Noble) ARM64 rootfs..."
sudo debootstrap --variant=minbase --arch=arm64 noble "${TARGET_DIR}" http://ports.ubuntu.com/ubuntu-ports

# Setup networking and CA certificates
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
../configure --prefix="${TARGET_DIR}" --disable-doc --disable-scripts --disable-nls
make -j$(nproc)
make install
cd "${BUILD_WORK_DIR}"

# ==========================================
# 3. Compile GStreamer with FFmpeg, H.264 & WMA
# ==========================================
echo "[+] Compiling GStreamer (${GST_VER}) with FFmpeg (libav), x264, H.264 & WMA support..."
git clone -b "${GST_VER}" --depth 1 https://github.com/GStreamer/gstreamer.git gst-src
cd gst-src

meson setup builddir \
    --prefix="${TARGET_DIR}" \
    --buildtype=release \
    --strip \
    --wrap-mode=fallback \
    -Dgst-full-target-type=shared_library \
    -Dgst-full-libraries=app,video,player,audio,tag,pbutils,ffmpeg \
    -Dbase=enabled \
    -Dgood=enabled \
    -Dbad=enabled \
    -Dugly=enabled \
    -Dlibav=enabled \
    -Dx264=enabled \
    -Dffmpeg=enabled \
    -Dintrospection=disabled \
    -Dtests=disabled \
    -Dexamples=disabled \
    -Ddoc=disabled \
    -Dges=disabled \
    -Dpython=disabled \
    -Ddevtools=disabled \
    -Dgstreamer:check=disabled \
    -Dgstreamer:benchmarks=disabled \
    -Dgstreamer:libunwind=disabled \
    -Dgstreamer:libdw=disabled \
    -Dgstreamer:bash-completion=disabled

ninja -C builddir install
cd "${BUILD_WORK_DIR}"

# ==========================================
# 4. Aggressive Pruning & Cleanup
# ==========================================
echo "[+] Removing unnecessary documentation, locales, and package caches..."
rm -rf "${TARGET_DIR}"/usr/share/doc/*
rm -rf "${TARGET_DIR}"/usr/share/man/*
rm -rf "${TARGET_DIR}"/usr/share/info/*
rm -rf "${TARGET_DIR}"/usr/share/locale/*
rm -rf "${TARGET_DIR}"/var/lib/apt/lists/*
rm -rf "${TARGET_DIR}"/var/cache/apt/*
rm -rf "${TARGET_DIR}"/tmp/*

echo "[+] Stripping debug symbols from all shared libraries and ELF binaries..."
find "${TARGET_DIR}/lib" "${TARGET_DIR}/usr/lib" "${TARGET_DIR}/bin" "${TARGET_DIR}/usr/bin" -type f | while read -r bin; do
    if file "$bin" | grep -q "ELF"; then
        strip --strip-unneeded "$bin" 2>/dev/null || true
    fi
done

# ==========================================
# 5. Patch Dynamic Linker (RPATH & Interpreter)
# ==========================================
echo "[+] Applying patchelf to all ELF files..."

# Patch GStreamer plugins directory
if [ -d "${TARGET_DIR}/lib/gstreamer-1.0" ]; then
    find "${TARGET_DIR}/lib/gstreamer-1.0" -name "*.so" -type f | while read -r plugin; do
        patchelf --set-rpath "${LD_RPATH}" --set-interpreter "${LD_FILE}" "${plugin}" 2>/dev/null || true
    done
fi

# Patch system executables and libraries
find "${TARGET_DIR}" -type f -exec file {} + | grep -E ":.*ELF" | cut -d: -f1 | while read -r elf_file; do
    patchelf --set-rpath "${LD_RPATH}" --set-interpreter "${LD_FILE}" "${elf_file}" 2>/dev/null || true
done

chmod +x "${TARGET_DIR}/bin/"* "${TARGET_DIR}/usr/bin/"* "${TARGET_DIR}/lib/"ld-*.so* 2>/dev/null || true

# ==========================================
# 6. Generate Version File & Package
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

echo "[+] Archiving rootfs into rootfs.tzst..."
OUTPUT_TZST="${BUILD_WORK_DIR}/rootfs.tzst"
cd "${TARGET_DIR}"

# Tar directly from inside the root folder
tar --zstd -cvf "${OUTPUT_TZST}" .

echo "=========================================="
echo " ROOTFS BUILD COMPLETE!"
echo " Location: ${OUTPUT_TZST}"
echo " Archive Size: $(du -sh ${OUTPUT_TZST} | cut -f1)"
echo "=========================================="