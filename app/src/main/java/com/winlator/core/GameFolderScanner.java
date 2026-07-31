package com.winlator.core;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class GameFolderScanner {
    private static final String TAG = "GameFolderScanner";
    private static final int MAX_DEPTH = 2; // User requested max depth 2
    private static final int CONFIDENT_SCORE = 60;
    private static final int AMBIGUOUS_MARGIN = 15;
    private static final int MAX_ALTERNATIVES = 20;

    private static final Set<String> SKIP_DIRS = new HashSet<>(Arrays.asList(
        "_commonredist", "commonredist", "redist", "redistributable", "redistributables",
        "directx", "dotnet", "dotnetfx", "vcredist", "vc_redist", "prerequisites", "prereq",
        "installer", "installers", "support", "extras", "docs", "documentation", "manual",
        "soundtrack", "ost", "artbook", "bonus", "dlc", "mods", "saves", "savegames",
        "crashreportclient", "easyanticheat", "battleye", "punkbuster", "steamworks shared",
        "Sound", "BGM"
    ));

    private static final Pattern JUNK_EXE_RE = Pattern.compile("(?i)^(unins\\w*|setup|install\\w*|vc_?redist.*|vcredist.*|dxsetup|dxwebsetup|directx.*|oalinst|openal.*|dotnetfx.*|ndp\\d.*|unitycrashhandler\\d*|crashreport\\w*|crashpad\\w*|crashsender\\w*|easyanticheat\\w*|eac\\w*|battleye\\w*|beservice\\w*|punkbuster\\w*|steamerrorreporter\\d*|gameoverlayui|touchup|cleanup|config|settings|benchmark|activation\\w*|register\\w*|readme|report\\w*|.*_debug|.*-debug)$");
    private static final Pattern EXCLUDE_EXE = Pattern.compile("(?i)(setup|setting|config|install)");

    public static class Candidate {
        public final File folder;
        public File exe;
        public final String name;
        public final Integer appId;
        public final boolean uncertain;
        public final boolean alreadyAdded;
        public final List<File> alternatives;

        public Candidate(File folder, File exe, String name, Integer appId, boolean uncertain, boolean alreadyAdded, List<File> alternatives) {
            this.folder = folder;
            this.exe = exe;
            this.name = name;
            this.appId = appId;
            this.uncertain = uncertain;
            this.alreadyAdded = alreadyAdded;
            this.alternatives = alternatives;
        }
    }

    public static List<Candidate> scan(File root, Set<String> existingExePaths) {
        if (!root.isDirectory()) return Collections.emptyList();
        File[] subDirsArr = root.listFiles(f -> f.isDirectory() && !isSkippedDir(f.getName()));
        List<File> folders;
        if (subDirsArr != null && subDirsArr.length > 0) {
            folders = Arrays.asList(subDirsArr);
            folders.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        } else {
            folders = Collections.singletonList(root);
        }

        List<Candidate> results = new ArrayList<>();
        for (File folder : folders) {
            try {
                Candidate candidate = candidateFor(folder, existingExePaths);
                if (candidate != null) results.add(candidate);
            } catch (Exception e) {
                Log.w(TAG, "scan failed for " + folder.getName(), e);
            }
        }
        return results;
    }

    private static Candidate candidateFor(File folder, Set<String> existingExePaths) {
        List<File> exes = collectExes(folder, 0);
        if (exes.isEmpty()) return null;

        List<Pair<File, Integer>> scored = new ArrayList<>();
        for (File exe : exes) {
            scored.add(new Pair<>(exe, scoreExe(exe, folder)));
        }
        scored.sort((a, b) -> b.second.compareTo(a.second));

        File bestExe = scored.get(0).first;
        int bestScore = scored.get(0).second;
        int runnerUp = scored.size() > 1 ? scored.get(1).second : Integer.MIN_VALUE;
        boolean uncertain = bestScore < CONFIDENT_SCORE || (runnerUp != Integer.MIN_VALUE && bestScore - runnerUp < AMBIGUOUS_MARGIN);

        GameIdentifier.GameIdentity identity = GameIdentifier.identify(bestExe);
        String name = GameIdentifier.normalizeName(folder.getName());
        
        // If the folder name is "bin", "binaries", or similar junk, try to get a better name from identity or EXE
        if (isJunkName(name) || name.isEmpty()) {
            name = (identity.name != null && !identity.name.isEmpty()) ? identity.name : FileUtils.getBasename(bestExe.getName());
        }

        List<File> alternatives = new ArrayList<>();
        for (int i = 1; i < Math.min(scored.size(), MAX_ALTERNATIVES + 1); i++) {
            alternatives.add(scored.get(i).first);
        }

        return new Candidate(
            folder,
            bestExe,
            name,
            identity.appId,
            uncertain,
            existingExePaths.contains(canonical(bestExe)),
            alternatives
        );
    }

    private static List<File> collectExes(File dir, int depth) {
        if (depth > MAX_DEPTH) return Collections.emptyList();
        File[] entries = dir.listFiles();
        if (entries == null) return Collections.emptyList();

        List<File> result = new ArrayList<>();
        for (File entry : entries) {
            String nameLower = entry.getName().toLowerCase();
            if (entry.isFile() && entry.getName().toLowerCase().endsWith(".exe")) {
                if (EXCLUDE_EXE.matcher(nameLower).find() || JUNK_EXE_RE.matcher(FileUtils.getBasename(entry.getName())).matches()) {
                    continue;
                }
                result.add(entry);
            } else if (entry.isDirectory() && !isSkippedDir(entry.getName())) {
                result.addAll(collectExes(entry, depth + 1));
            }
        }
        return result;
    }

    private static int scoreExe(File exe, File folder) {
        int score = 0;
        String base = FileUtils.getBasename(exe.getName());
        String exeKey = key(base);
        String folderKey = key(folder.getName());

        if (!exeKey.isEmpty() && !folderKey.isEmpty()) {
            if (exeKey.equals(folderKey)) score += 100;
            else if (folderKey.contains(exeKey) || exeKey.contains(folderKey)) score += 60;
        }

        if (base.toLowerCase().endsWith("-win64-shipping") || base.toLowerCase().endsWith("-win32-shipping")) score += 70;
        String path = exe.getAbsolutePath().toLowerCase();
        if (path.contains("/binaries/win64") || path.contains("/binaries/win32")) score += 30;
        if (path.contains("/bin/") || path.contains("/bin64/")) score += 10;

        if (GameIdentifier.isLauncherExeName(base)) score -= 40;
        if (GameIdentifier.isJunkPeName(base)) score -= 30;

        score -= depthBelow(folder, exe) * 8;
        score += (int)Math.min(exe.length() / (8L * 1024 * 1024), 30);
        return score;
    }

    private static int depthBelow(File folder, File exe) {
        String f = folder.getAbsolutePath();
        String e = exe.getParentFile() != null ? exe.getParentFile().getAbsolutePath() : "";
        if (e.equals(f)) return 0;
        if (e.startsWith(f)) {
            String rel = e.substring(f.length());
            int count = 0;
            for (char c : rel.toCharArray()) if (c == File.separatorChar) count++;
            return count;
        }
        return 0;
    }

    private static boolean isSkippedDir(String name) {
        return SKIP_DIRS.contains(name.toLowerCase().trim());
    }

    private static boolean isJunkName(String name) {
        String n = name.toLowerCase().trim();
        return n.equals("bin") || n.equals("binaries") || n.equals("win64") || n.equals("win32") || n.equals("x64") || n.equals("x86");
    }

    private static String key(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toLowerCase().toCharArray()) if (Character.isLetterOrDigit(c)) sb.append(c);
        return sb.toString();
    }

    private static String canonical(File f) {
        try { return f.getCanonicalPath(); } catch (Exception e) { return f.getAbsolutePath(); }
    }

    private static class Pair<T, U> {
        final T first;
        final U second;
        Pair(T first, U second) { this.first = first; this.second = second; }
    }
}
