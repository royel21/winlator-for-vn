package com.winlator.core;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.annotation.NonNull;

import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WineInfo implements Parcelable {
    public static final WineInfo WINE_X86_64 = new WineInfo("10.10", null, "x86_64", "/opt/x86_64-wine");
    public static final WineInfo WINE_ARM64EC = new WineInfo("10.14", null, "arm64ec", "/opt/arm64ec-wine");
    public static final WineInfo MAIN_WINE_VERSION = WINE_X86_64;
    private static final Pattern pattern = Pattern.compile("^wine\\-([0-9\\.]+)\\-?(?:(.+)\\-)?(x86_64|arm64ec)$", Pattern.CASE_INSENSITIVE);
    public final String version;
    public final String subversion;
    public final String path;
    private String arch;

    public WineInfo(String version, String arch) {
        this.version = version;
        this.subversion = null;
        this.arch = arch;
        this.path = null;
    }

    public WineInfo(String version, String subversion, String arch, String path) {
        this.version = version;
        this.subversion = subversion != null && !subversion.isEmpty() ? subversion : null;
        this.arch = arch;
        this.path = path;
    }

    private WineInfo(Parcel in) {
        version = in.readString();
        subversion = in.readString();
        arch = in.readString();
        path = in.readString();
    }

    public String getArch() {
        return arch;
    }

    public void setArch(String arch) {
        this.arch = arch;
    }

    public boolean isWin64() {
        return true; // 强制为 64 位环境
    }

    public String getExecutable(Context context, boolean wow64Mode) {
        // x86_64: 使用 wine（现代 Wine 统一使用 wine 命令）
        // arm64ec: 使用 wine
        return "wine";
    }

    public boolean isDefaultWine() {
        return (this == WINE_X86_64) || (this == WINE_ARM64EC) || (path != null && (path.equals("/opt/x86_64-wine") || path.equals("/opt/arm64ec-wine")));
    }

    public String identifier() {
        if (this == WINE_X86_64) return "Wine-10.10-x86_64";
        if (this == WINE_ARM64EC) return "Wine-10.14-arm64ec";
        return "wine-"+fullVersion()+"-"+arch;
    }

    public String fullVersion() {
        return version+(subversion != null ? "-"+subversion : "");
    }

    @NonNull
    @Override
    public String toString() {
        return identifier();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Parcelable.Creator<WineInfo> CREATOR = new Parcelable.Creator<WineInfo>() {
        public WineInfo createFromParcel(Parcel in) {
            return new WineInfo(in);
        }

        public WineInfo[] newArray(int size) {
            return new WineInfo[size];
        }
    };

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(version);
        dest.writeString(subversion);
        dest.writeString(arch);
        dest.writeString(path);
    }

    @NonNull
    public static WineInfo fromIdentifier(Context context, String identifier) {
        if (identifier == null) return MAIN_WINE_VERSION;

        if (identifier.equalsIgnoreCase(WINE_X86_64.identifier())) return WINE_X86_64;
        if (identifier.equalsIgnoreCase(WINE_ARM64EC.identifier())) return WINE_ARM64EC;

        ContentsManager contentsManager = new ContentsManager(context);
        contentsManager.syncContents();
        ContentProfile profile = contentsManager.getProfileByEntryName(identifier);
        if (profile != null && profile.type == ContentProfile.ContentType.CONTENT_TYPE_WINE) {
            File installDir = ContentsManager.getInstallDir(context, profile);
            if (installDir.exists()) {
                String arch = identifier.contains("arm64ec") ? "arm64ec" : "x86_64";
                String rootfsPath = RootFS.find(context).getRootDir().getPath();
                String absPath = installDir.getAbsolutePath();
                String relPath = absPath.startsWith(rootfsPath) ? absPath.substring(rootfsPath.length()) : absPath;
                if (relPath.startsWith("/")) relPath = relPath.substring(1);
                android.util.Log.d("Winlator", "WCP Wine path - absolute: " + absPath + ", relative: " + relPath);
                return new WineInfo(profile.verName, null, arch, relPath);
            }
        }

        RootFS rootFS = RootFS.find(context);
        File installedWineDir = rootFS.getInstalledWineDir();
        File customWineDir = new File(installedWineDir, identifier);

        if (!customWineDir.exists()) {
            File contentsWineDir = new File(rootFS.getRootDir(), "opt/contents/Wine/" + identifier);
            if (contentsWineDir.exists()) {
                customWineDir = contentsWineDir;
            } else if (installedWineDir.exists()) {
                File[] files = installedWineDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().equalsIgnoreCase(identifier)) {
                            customWineDir = f;
                            break;
                        }
                    }
                }
            }
        }

        String path = customWineDir.exists() ? customWineDir.getAbsolutePath() : rootFS.getRootDir().getPath() + "/opt/contents/Wine/" + identifier;

        Matcher matcher = pattern.matcher(identifier);
        if (matcher.find()) {
            return new WineInfo(matcher.group(1), matcher.group(2), matcher.group(3).toLowerCase(), path);
        } else {
            Matcher verMatcher = Pattern.compile("([0-9]+\\.[0-9]+)").matcher(identifier);
            String ver = verMatcher.find() ? verMatcher.group(1) : "10.17";
            String arch = identifier.toLowerCase().contains("arm64ec") ? "arm64ec" : "x86_64";
            return new WineInfo(ver, null, arch, path);
        }
    }

    public static boolean isMainWineVersion(String wineVersion) {
        return wineVersion == null || wineVersion.equalsIgnoreCase(WINE_X86_64.identifier()) || wineVersion.equalsIgnoreCase(WINE_ARM64EC.identifier());
    }

    public boolean isVersionAtLeast(int targetMajor, int targetMinor) {
        if (version == null || version.isEmpty()) return false;
        try {
            String cleanVersion = version.replaceAll("[^0-9\\.]", "");
            String[] parts = cleanVersion.split("\\.");
            int major = parts.length > 0 && !parts[0].isEmpty() ? Integer.parseInt(parts[0]) : 0;
            int minor = parts.length > 1 && !parts[1].isEmpty() ? Integer.parseInt(parts[1]) : 0;
            if (major > targetMajor) return true;
            if (major == targetMajor) return minor >= targetMinor;
        } catch (Exception e) {
            Log.e("WineInfo", "Failed to parse version: " + version, e);
        }
        return false;
    }

    public boolean isWine112OrNewer() {
        return isVersionAtLeast(11, 2);
    }
}
