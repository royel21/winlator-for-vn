package com.winlator.core;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameIdentifier {
    private static final int MAX_PARENT_DEPTH = 6;

    public enum Source { STEAM_APPID_TXT, STEAM_MANIFEST_ACF, STEAM_EMU_INI, GOG_INFO, PE_VERSION, FILENAME, NONE }
    public enum Confidence { HIGH, MEDIUM, LOW }

    public static class GameIdentity {
        public final Integer appId;
        public final String gogId;
        public final String name;
        public final Source source;
        public final Confidence confidence;

        public GameIdentity(Integer appId, String gogId, String name, Source source, Confidence confidence) {
            this.appId = appId;
            this.gogId = gogId;
            this.name = name;
            this.source = source;
            this.confidence = confidence;
        }
    }

    private static final Set<String> REPACK_TOKENS = new HashSet<>(Arrays.asList(
        "fitgirl", "dodi", "codex", "plaza", "skidrow", "rune", "empress", "flt", "tenoke",
        "reloaded", "elamigos", "razor1911", "hoodlum", "gog", "repack", "multi", "goldberg",
        "ankergames", "steamrip", "onlinefix", "online-fix", "gload", "fitgirlrepacks"
    ));

    private static final String EXE_NOISE_WORDS = "win64|win32|shipping|wingdk|client|launcher|game|x64|x86|64bit|32bit";
    private static final Pattern EXE_NOISE_RE = Pattern.compile("(?i)[-_. ]*(?:" + EXE_NOISE_WORDS + ")");
    private static final Pattern EXE_SUFFIX_RE = Pattern.compile("(?i)(?:[-_. ]*(?:" + EXE_NOISE_WORDS + "))+$");
    private static final Pattern VERSION_TOKEN_RE = Pattern.compile("(?i)\\b(v?\\d+([._]\\d+)+|build[-_ ]?\\d+|update[-_ ]?\\d+)\\b");
    private static final Pattern JUNK_NAME_RE = Pattern.compile("(?i)^(gse|steam|steam client|goldberg.*|launcher|game|play|start|setup|.* launcher.*|.*launcher redirector|.* redirector|rockstar games launcher.*|epic games launcher|smartsteamemu|coldclient.*|steamemu.*)$");

    public static GameIdentity identify(File exeFile) {
        List<File> dirs = ancestorDirs(exeFile);

        Integer steamAppId = null;
        for (File dir : dirs) {
            steamAppId = readSteamAppIdTxt(dir);
            if (steamAppId != null) break;
        }

        Integer emuAppId = null;
        if (steamAppId == null) {
            for (File dir : dirs) {
                emuAppId = readEmuIniAppId(dir);
                if (emuAppId != null) break;
            }
        }

        Acf acf = resolveSteamManifest(dirs);
        Gog gog = null;
        for (File dir : dirs) {
            gog = readGogInfo(dir);
            if (gog != null) break;
        }

        Map<String, String> peInfo = readPeVersionInfo(exeFile);
        String peName = bestNameFromPe(peInfo);
        if (peName != null && isJunkPeName(peName)) peName = null;

        Integer appId = (acf != null && acf.appId != null) ? acf.appId : (steamAppId != null ? steamAppId : emuAppId);

        String finalName;
        Source finalSource;

        if (acf != null && acf.name != null) {
            finalName = acf.name;
            finalSource = Source.STEAM_MANIFEST_ACF;
        } else if (gog != null && gog.name != null) {
            finalName = gog.name;
            finalSource = Source.GOG_INFO;
        } else if (peName != null) {
            finalName = peName;
            finalSource = Source.PE_VERSION;
        } else {
            finalName = cleanedFallbackName(exeFile);
            finalSource = Source.FILENAME;
        }

        Source primarySource;
        if (acf != null && acf.appId != null) primarySource = Source.STEAM_MANIFEST_ACF;
        else if (steamAppId != null) primarySource = Source.STEAM_APPID_TXT;
        else if (emuAppId != null) primarySource = Source.STEAM_EMU_INI;
        else primarySource = finalSource;

        Confidence confidence = Confidence.LOW;
        if (appId != null || finalSource == Source.GOG_INFO) confidence = Confidence.HIGH;
        else if (finalSource == Source.PE_VERSION) confidence = Confidence.MEDIUM;

        return new GameIdentity(
            appId,
            gog != null ? gog.id : null,
            finalName != null ? normalizeName(finalName) : null,
            primarySource,
            confidence
        );
    }

    public static String normalizeName(String raw) {
        String s = raw.replace("™", "").replace("®", "").replace("©", "");
        s = s.replaceAll("(?i)\\(\\s*(tm|r|c)\\s*\\)", "");
        s = s.replace(":", " - ");
        s = s.replaceAll("[\\\\/]", " ");
        s = s.replaceAll("[\"*?<>|]", "");
        s = s.replaceAll("(?i)[ \\-]+(executable|application|launcher|redistributable|installer|setup)$", "");
        s = s.replaceAll("\\s+", " ").trim();
        while (s.startsWith("-") || s.startsWith(" ")) s = s.substring(1).trim();
        while (s.endsWith("-") || s.endsWith(" ")) s = s.substring(0, s.length() - 1).trim();
        return s;
    }

    private static List<File> ancestorDirs(File exeFile) {
        List<File> out = new ArrayList<>();
        File d = exeFile.getAbsoluteFile().getParentFile();
        int depth = 0;
        while (d != null && depth <= MAX_PARENT_DEPTH) {
            out.add(d);
            d = d.getParentFile();
            depth++;
        }
        return out;
    }

    private static Integer readSteamAppIdTxt(File dir) {
        for (String path : new String[]{"steam_appid.txt", "steam_settings/steam_appid.txt"}) {
            File f = new File(dir, path);
            String text = readSmallTextFile(f);
            if (text != null) {
                text = text.replace("\uFEFF", "").trim();
                StringBuilder sb = new StringBuilder();
                for (char c : text.toCharArray()) {
                    if (Character.isDigit(c)) sb.append(c);
                    else break;
                }
                if (sb.length() > 0) {
                    try { return Integer.parseInt(sb.toString()); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    private static Integer readEmuIniAppId(File dir) {
        Pattern re = Pattern.compile("(?im)^\\s*appid\\s*=\\s*(\\d+)");
        String[] named = {
            "ColdClientLoader.ini", "steam_emu.ini", "flt.ini", "hlm.ini",
            "valve.ini", "cream_api.ini", "SmartSteamEmu.ini", "GreenLuma.ini"
        };
        Set<String> seen = new HashSet<>();
        List<File> candidates = new ArrayList<>();
        for (String n : named) {
            File f = new File(dir, n);
            if (f.isFile()) {
                candidates.add(f);
                seen.add(n.toLowerCase());
            }
        }
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".ini") && !seen.contains(f.getName().toLowerCase()));
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            candidates.addAll(Arrays.asList(files));
        }
        for (File f : candidates) {
            String text = readSmallTextFile(f);
            if (text != null) {
                Matcher m = re.matcher(text);
                if (m.find()) {
                    try { return Integer.parseInt(m.group(1)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    private static class Acf {
        Integer appId;
        String name;
        Acf(Integer appId, String name) { this.appId = appId; this.name = name; }
    }

    private static Acf resolveSteamManifest(List<File> dirs) {
        int commonIdx = -1;
        for (int i = 0; i < dirs.size(); i++) {
            if (dirs.get(i).getName().equalsIgnoreCase("common")) {
                commonIdx = i;
                break;
            }
        }
        if (commonIdx < 0) return null;
        File commonDir = dirs.get(commonIdx);
        File steamapps = commonDir.getParentFile();
        if (steamapps == null) return null;
        File[] manifests = steamapps.listFiles(f -> f.isFile() && f.getName().startsWith("appmanifest_") && f.getName().endsWith(".acf"));
        if (manifests == null || manifests.length == 0) return null;

        String gameFolder = commonIdx > 0 ? dirs.get(commonIdx - 1).getName() : null;

        File match = null;
        for (File m : manifests) {
            String text = readSmallTextFile(m);
            if (text != null) {
                String installdir = vdfValue(text, "installdir");
                if (gameFolder != null && installdir != null && installdir.equalsIgnoreCase(gameFolder)) {
                    match = m;
                    break;
                }
            }
        }
        if (match == null && manifests.length == 1) match = manifests[0];
        if (match == null) return null;

        String text = readSmallTextFile(match);
        if (text == null) return null;
        String appIdStr = vdfValue(text, "appid");
        Integer appId = (appIdStr != null) ? Integer.parseInt(appIdStr) : null;
        String name = vdfValue(text, "name");
        return new Acf(appId, name);
    }

    private static class Gog {
        String id;
        String name;
        Gog(String id, String name) { this.id = id; this.name = name; }
    }

    private static Gog readGogInfo(File dir) {
        File[] files = dir.listFiles(f -> f.isFile() && f.getName().startsWith("goggame-") && f.getName().endsWith(".info"));
        if (files == null) return null;
        for (File f : files) {
            String text = readSmallTextFile(f);
            if (text != null) {
                String name = jsonStringValue(text, "name");
                if (name != null) {
                    String id = f.getName().substring(8, f.getName().length() - 5);
                    return new Gog(id, name);
                }
            }
        }
        return null;
    }

    public static boolean isLauncherExeName(String base) {
        String lower = base.toLowerCase();
        if (lower.equals("gse") || lower.endsWith("launcher") || lower.endsWith("redirector")) return true;
        for (String p : new String[]{"play", "launch", "start", "run"}) {
            if (lower.startsWith(p)) {
                String rest = base.substring(p.length());
                if (rest.isEmpty()) return true;
                char c = rest.charAt(0);
                if (!Character.isLetterOrDigit(c) || Character.isUpperCase(c)) return true;
            }
        }
        return false;
    }

    public static boolean isJunkPeName(String name) {
        String n = name.trim();
        return n.isEmpty() || JUNK_NAME_RE.matcher(n).matches();
    }

    public static String cleanedFallbackName(File exeFile) {
        String exeBase = FileUtils.getBasename(exeFile.getName());
        boolean preferFolder = EXE_NOISE_RE.matcher(exeBase).find() || isLauncherExeName(exeBase) || exeBase.equalsIgnoreCase("game") || exeBase.equalsIgnoreCase("start");
        String folder = exeFile.getParentFile() != null ? exeFile.getParentFile().getName() : null;
        String raw = (preferFolder && folder != null && !folder.isEmpty()) ? folder : exeBase;
        return cleanName(raw);
    }

    public static String cleanName(String raw) {
        String s = raw.replaceAll("\\[[^\\]]*\\]|\\([^)]*\\)", " ");
        s = VERSION_TOKEN_RE.matcher(s).replaceAll(" ");
        s = EXE_SUFFIX_RE.matcher(s).replaceAll(" ");
        s = s.replaceAll("[._]+", " ");
        String[] parts = s.split("[\\s-]+");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isEmpty() && !REPACK_TOKENS.contains(p.toLowerCase())) {
                if (sb.length() > 0) sb.append(" ");
                sb.append(p);
            }
        }
        return sb.length() > 0 ? sb.toString().trim() : null;
    }

    private static String readSmallTextFile(File f) {
        if (f.isFile() && f.length() > 0 && f.length() <= 1024 * 1024) {
            return FileUtils.readString(f);
        }
        return null;
    }

    private static String vdfValue(String text, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*\"([^\"]*)\"", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    private static String jsonStringValue(String text, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
        Matcher m = p.matcher(text);
        if (m.find()) {
            String val = m.group(1);
            if (val != null) return val.replace("\\\"", "\"").replace("\\\\", "\\").trim();
        }
        return null;
    }

    private static Map<String, String> readPeVersionInfo(File file) {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            byte[] dos = new byte[64];
            raf.readFully(dos);
            if (dos[0] != 0x4D || dos[1] != 0x5A) return null;
            int peOff = u32(dos, 0x3C);

            raf.seek(peOff);
            byte[] coff = new byte[24];
            raf.readFully(coff);
            if (coff[0] != 0x50 || coff[1] != 0x45) return null;
            int numSections = u16(coff, 6);
            int sizeOpt = u16(coff, 20);

            long optOff = peOff + 24;
            raf.seek(optOff);
            byte[] opt = new byte[sizeOpt];
            raf.readFully(opt);
            int magic = u16(opt, 0);
            int ddOff = (magic == 0x20B) ? 112 : 96;
            int resDirEntry = ddOff + 16;
            if (resDirEntry + 8 > opt.length) return null;
            int resRVA = u32(opt, resDirEntry);
            if (resRVA == 0) return null;

            long secTabOff = optOff + sizeOpt;
            raf.seek(secTabOff);
            byte[] secTab = new byte[numSections * 40];
            raf.readFully(secTab);

            int secVA = 0; long secPtr = 0; int secRaw = 0; boolean found = false;
            for (int i = 0; i < numSections; i++) {
                int o = i * 40;
                int va = u32(secTab, o + 12);
                int vSize = u32(secTab, o + 8);
                int raw = u32(secTab, o + 16);
                long pRaw = u32L(secTab, o + 20);
                if (resRVA >= va && resRVA < va + Math.max(vSize, raw)) {
                    secVA = va; secPtr = pRaw; secRaw = raw; found = true; break;
                }
            }
            if (!found || secRaw <= 0 || secRaw > 64 * 1024 * 1024) return null;

            raf.seek(secPtr);
            byte[] res = new byte[secRaw];
            raf.readFully(res);

            int resDirStart = resRVA - secVA;
            Integer verTypeDir = findIdSubdir(res, resDirStart, 16, resDirStart);
            if (verTypeDir == null) return null;
            Integer verData = firstDataEntry(res, verTypeDir, resDirStart);
            if (verData == null) return null;

            int dataRva = u32(res, verData);
            int dataSize = u32(res, verData + 4);
            int dataStart = dataRva - secVA;
            if (dataSize <= 0 || dataStart < 0 || dataStart + dataSize > res.length) return null;
            byte[] blob = Arrays.copyOfRange(res, dataStart, dataStart + dataSize);

            return parseStringFileInfo(blob);
        } catch (Exception e) {
            return null;
        }
    }

    private static String bestNameFromPe(Map<String, String> map) {
        if (map == null) return null;
        String product = map.get("ProductName");
        if (product != null) product = product.trim();
        String desc = map.get("FileDescription");
        if (desc != null) desc = desc.trim();

        if (product != null && !product.isEmpty() && desc != null && !desc.isEmpty() &&
            !product.contains(" ") && desc.contains(" ") && desc.length() <= 60) {
            return desc;
        }
        return (product != null && !product.isEmpty()) ? product : ((desc != null && !desc.isEmpty()) ? desc : null);
    }

    private static Integer findIdSubdir(byte[] res, int dirIdx, int wantId, int resDirStart) {
        if (dirIdx + 16 > res.length) return null;
        int total = u16(res, dirIdx + 12) + u16(res, dirIdx + 14);
        int start = dirIdx + 16;
        for (int i = 0; i < total; i++) {
            int e = start + i * 8;
            if (e + 8 > res.length) break;
            long id = u32L(res, e);
            long off = u32L(res, e + 4);
            if ((id & 0x80000000L) == 0 && (int)id == wantId) {
                if ((off & 0x80000000L) != 0) return resDirStart + (int)(off & 0x7FFFFFFFL);
                return null;
            }
        }
        return null;
    }

    private static Integer firstDataEntry(byte[] res, int dirIdx, int resDirStart) {
        if (dirIdx + 16 > res.length) return null;
        if (u16(res, dirIdx + 12) + u16(res, dirIdx + 14) == 0) return null;
        int e = dirIdx + 16;
        if (e + 8 > res.length) return null;
        long off = u32L(res, e + 4);
        if ((off & 0x80000000L) != 0) {
            return firstDataEntry(res, resDirStart + (int)(off & 0x7FFFFFFFL), resDirStart);
        } else return dirIdx;
    }

    private static Map<String, String> parseStringFileInfo(byte[] b) {
        Map<String, String> out = new LinkedHashMap<>();
        if (b.length < 6) return out;
        int rootLen = Math.min(u16(b, 0), b.length);
        int rootValLen = u16(b, 2);
        int pos = align4(6 + (utf16zLen(b, 6) + 1) * 2);
        pos = align4(pos + rootValLen);

        while (pos + 6 <= rootLen) {
            int childLen = u16(b, pos);
            if (childLen < 6 || pos + childLen > b.length) break;
            String key = utf16z(b, pos + 6, b.length);
            if (key.equals("StringFileInfo")) {
                int p = align4(pos + 6 + (utf16zLen(b, pos + 6) + 1) * 2);
                while (p + 6 <= pos + childLen) {
                    int tableLen = u16(b, p);
                    if (tableLen < 6 || p + tableLen > pos + childLen) break;
                    int q = align4(p + 6 + (utf16zLen(b, p + 6) + 1) * 2);
                    while (q + 6 <= p + tableLen) {
                        int strLen = u16(b, q);
                        if (strLen < 6 || q + strLen > p + tableLen) break;
                        String sKey = utf16z(b, q + 6, b.length);
                        int valOff = align4(q + 6 + (sKey.length() + 1) * 2);
                        if (!sKey.isEmpty() && valOff < q + strLen) {
                            String value = utf16z(b, valOff, q + strLen).trim();
                            if (!value.isEmpty()) out.put(sKey, value);
                        }
                        q = align4(q + strLen);
                    }
                    p = align4(p + tableLen);
                }
            }
            pos = align4(pos + childLen);
        }
        return out;
    }

    private static int align4(int x) { return (x + 3) & ~3; }

    private static int utf16zLen(byte[] b, int off) {
        int i = off;
        while (i + 1 < b.length) {
            if (b[i] == 0 && b[i + 1] == 0) break;
            i += 2;
        }
        return (i - off) / 2;
    }

    private static String utf16z(byte[] b, int off, int limit) {
        StringBuilder sb = new StringBuilder();
        int i = off;
        int end = Math.min(limit, b.length);
        while (i + 1 < end) {
            int c = (b[i] & 0xFF) | ((b[i + 1] & 0xFF) << 8);
            if (c == 0) break;
            sb.append((char)c);
            i += 2;
        }
        return sb.toString();
    }

    private static int u16(byte[] b, int off) { return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8); }
    private static int u32(byte[] b, int off) { return (int)u32L(b, off); }
    private static long u32L(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8) | ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }
}
