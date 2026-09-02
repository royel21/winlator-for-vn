package com.winlator.xenvironment.components;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;

import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.box64.Box64PresetManager;
import com.winlator.contents.ContentProfile;
import com.winlator.contents.ContentsManager;
import com.winlator.core.Callback;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.GeneralComponents;
import com.winlator.core.LocaleHelper;
import com.winlator.core.ProcessHelper;
import com.winlator.core.WineInfo;
import com.winlator.widget.LogView;
import com.winlator.xconnector.UnixSocketConfig;
import com.winlator.xenvironment.EnvironmentComponent;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.util.List;

public class GuestProgramLauncherComponent extends EnvironmentComponent {
    private String guestExecutable;
    private static int pid = -1;
    private EnvVars envVars;
    private String box64Preset = Box64Preset.CONSERVATIVE;
    private String box64Version = DefaultVersion.BOX64;
    private String fexVersion = "";
    private int fexPreset = 0;
    private String fexPresetCustom = "";
    private String wineVersion = "";
    private Callback<Integer> terminationCallback;
    private static final Object lock = new Object();

    @Override
    public void start() {
        synchronized (lock) {
            stop();
            extractBox64File();
            copyDefaultBox64RCFile();
            pid = execGuestProgram();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            killWineServer();
            if (pid != -1) {
                Process.killProcess(pid);
                pid = -1;
            }
        }
    }

    private void killWineServer() {
        if (environment == null || wineVersion.isEmpty() || envVars == null) return;
        RootFS rootFS = environment.getRootFS();
        File rootDir = rootFS.getRootDir();

        String winePath = rootFS.getWinePath();
        while (winePath.startsWith("/")) winePath = winePath.substring(1);
        File wineDirAbs = new File(rootDir, winePath);

        WineInfo wineInfo = WineInfo.fromIdentifier(environment.getContext(), wineVersion);
        boolean isArm64EC = wineInfo != null && "arm64ec".equals(wineInfo.getArch());

        String[] options = {"-k", "-w"};
        for (String opt : options) {
            String command;
            if (!isArm64EC) {
                command = rootDir.getPath() + "/usr/local/bin/box64 " + wineDirAbs.getPath() + "/bin/wineserver " + opt;
            } else {
                command = wineDirAbs.getPath() + "/bin/wineserver " + opt;
            }

            if (command.contains("rootfs/opt")) {
                command = command.replace("rootfs/opt", "rootfs//opt");
            }

            try {
                java.lang.Process process = Runtime.getRuntime().exec(ProcessHelper.splitCommand(command), envVars.toStringArray(), rootDir);
                process.waitFor();
            } catch (Exception e) {
                Log.e("Winlator", "killWineServer failed for option " + opt, e);
            }
        }
    }

    public Callback<Integer> getTerminationCallback() {
        return terminationCallback;
    }

    public void setTerminationCallback(Callback<Integer> terminationCallback) {
        this.terminationCallback = terminationCallback;
    }

    public String getGuestExecutable() {
        return guestExecutable;
    }

    public void setGuestExecutable(String guestExecutable) {
        this.guestExecutable = guestExecutable;
    }

    public EnvVars getEnvVars() {
        return envVars;
    }

    public void setEnvVars(EnvVars envVars) {
        this.envVars = new EnvVars();
        this.envVars.putAll(envVars);
    }

    public String getBox64Preset() {
        return box64Preset;
    }

    public void setBox64Preset(String box64Preset) {
        this.box64Preset = box64Preset;
    }

    public String getBox64Version() {
        return box64Version;
    }

    public void setBox64Version(String box64Version) {
        this.box64Version = box64Version;
    }

    public String getFexVersion() {
        return fexVersion;
    }

    public void setFexVersion(String fexVersion) {
        this.fexVersion = fexVersion;
    }

    public int getFexPreset() {
        return fexPreset;
    }

    public void setFexPreset(int fexPreset) {
        this.fexPreset = fexPreset;
    }

    public String getFexPresetCustom() {
        return fexPresetCustom;
    }

    public void setFexPresetCustom(String fexPresetCustom) {
        this.fexPresetCustom = fexPresetCustom;
    }

    public String getWineVersion() {
        return wineVersion;
    }

    public void setWineVersion(String wineVersion) {
        this.wineVersion = wineVersion;
    }

    private int execGuestProgram() {
        RootFS rootFS = environment.getRootFS();
        File rootDir = rootFS.getRootDir();

        EnvVars envVars = new EnvVars();
        
        // 检测 Wine 架构
        WineInfo wineInfo = WineInfo.fromIdentifier(environment.getContext(), wineVersion);
        boolean isArm64EC = wineInfo != null && "arm64ec".equals(wineInfo.getArch());
        android.util.Log.d("Winlator-log", "Wine version: " + wineVersion);
        android.util.Log.d("Winlator-log", "Wine info: " + (wineInfo != null ? wineInfo.identifier() : "null"));
        android.util.Log.d("Winlator-log", "Wine arch: " + (wineInfo != null ? wineInfo.getArch() : "null"));
        android.util.Log.d("Winlator-log", "Is arm64ec: " + isArm64EC);

        if (!isArm64EC) {
            // x86_64: 使用 Box64（原始逻辑）
            addBox64EnvVars(envVars);
        } else {
            // arm64ec: 使用 FEX
            addFEXEnvVars(envVars);
        }
        
        LocaleHelper.setEnvVars(envVars);

        envVars.put("HOME", rootDir+RootFS.HOME_PATH);
        envVars.put("USER", RootFS.USER);
        envVars.put("TMPDIR", rootDir+"/tmp");
        envVars.put("DISPLAY", ":0");
        
        // 修复路径拼接
        String winePath = rootFS.getWinePath();
        while (winePath.startsWith("/")) winePath = winePath.substring(1);
        
        File wineDirAbs = new File(rootDir, winePath);
        File wineLibDir = new File(wineDirAbs, "lib");
        File wineLibWineDir = new File(wineLibDir, "wine");
        if (!wineLibWineDir.isDirectory()) wineLibWineDir.mkdirs();
        
        envVars.put("PATH", wineDirAbs.getPath() + "/bin:" + rootDir + "/usr/local/bin:" + rootDir + "/usr/bin");
        
        // 根据架构设置不同的库路径
        String ldLibraryPath;
        File wineUnixLibDir;
        if (!isArm64EC) {
            // x86_64: 原始逻辑
            ldLibraryPath = rootFS.getLibDir().getPath();
            wineUnixLibDir = new File(wineLibWineDir, "x86_64-unix");
        } else {
            // arm64ec: 参考 glibc 项目设置特殊的库路径
            wineUnixLibDir = new File(wineLibWineDir, "aarch64-unix");
            ldLibraryPath = wineUnixLibDir.getPath() + ":" + wineLibDir.getPath() + ":" + rootFS.getLibDir().getPath();
        }

        // Fix: Wine 10.x+ layout compatibility - link all files from arch-specific unix dir to parent wine dir
        if (wineUnixLibDir.exists()) {
            File[] files = wineUnixLibDir.listFiles();
            if (files != null) {
                String relPrefix = wineUnixLibDir.getName() + "/";
                for (File f : files) {
                    if (f.isFile()) {
                        FileUtils.symlink(relPrefix + f.getName(), new File(wineLibWineDir, f.getName()).getAbsolutePath());
                    }
                }
            }
        }

        envVars.put("BOX64_LD_LIBRARY_PATH", rootDir + "/lib/x86_64-linux-gnu:" + wineUnixLibDir.getPath() + ":" + wineLibDir.getPath());
        envVars.put("WINEDLLPATH", wineLibWineDir.getPath() + "/wine");
        
        envVars.put("LD_LIBRARY_PATH", ldLibraryPath);
        envVars.put("ANDROID_SYSVSHM_SERVER", rootDir.getPath() + UnixSocketConfig.SYSVSHM_SERVER_PATH);
        envVars.put("WINE_HOST_XDG_CURRENT_DESKTOP", "1");//新版wine桌面创建快捷方式需要这个
        envVars.put("BOX64_ROOT", rootDir.getPath());

        if (this.envVars != null) envVars.putAll(this.envVars);

        File shmDir = new File(rootDir, "tmp/shm");
        if (!shmDir.isDirectory()) shmDir.mkdirs();

        // 根据架构生成启动命令
        android.util.Log.d("Winlator-log", "Wine path: '" + winePath + "'");
        
        String command;
        if (!isArm64EC) {
            // x86_64: Use box64 to translate (with full path concatenation)
            command = rootDir.getPath() + "/usr/local/bin/box64 " + wineDirAbs.getPath() + "/bin/" + guestExecutable;
        } else {
            // arm64ec: Execute directly (paste the full path)
            command = wineDirAbs.getPath() + "/bin/" + guestExecutable;
        }
        
        // 统一修复 //opt 问题
        for (String key : new String[]{"PATH", "BOX64_LD_LIBRARY_PATH", "WINEDLLPATH", "LD_LIBRARY_PATH"}) {
            String val = envVars.get(key);
            if (val != null && val.contains("rootfs/opt")) {
                envVars.put(key, val.replace("rootfs/opt", "rootfs//opt"));
            }
        }
        
        if (command.contains("rootfs/opt")) {
            command = command.replace("rootfs/opt", "rootfs//opt");
        }

        Log.d("Winlator-box", "BOX64_LD_LIBRARY_PATH set to: " + envVars.get("BOX64_LD_LIBRARY_PATH"));
        Log.d("Winlator-box", "Executing command: "+ command);

        this.envVars = envVars;

        return ProcessHelper.exec(command, envVars, rootDir, (status) -> {
            synchronized (lock) {
                pid = -1;
            }
            if (terminationCallback != null) terminationCallback.call(status);
        });
    }

    private void extractBox64File() {
        Context context = environment.getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String currentBox64Version = preferences.getString("current_box64_version", "");

        if (!box64Version.equals(currentBox64Version)) {
            GeneralComponents.extractFile(GeneralComponents.Type.BOX64, context, box64Version, DefaultVersion.BOX64);
            preferences.edit().putString("current_box64_version", box64Version).apply();
        }
    }

    private void copyDefaultBox64RCFile() {
        Context context = environment.getContext();
        RootFS rootFS = environment.getRootFS();
        FileUtils.copy(context, "box64/default.box64rc", new File(rootFS.getRootDir(), "/etc/config.box64rc"));
    }

    private void addBox64EnvVars(EnvVars envVars) {
        Context context = environment.getContext();
        RootFS rootFS = environment.getRootFS();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        int box64Logs = preferences.getInt("box64_logs", 0);
        boolean saveToFile = preferences.getBoolean("save_logs_to_file", false);

        envVars.put("BOX64_NOBANNER", box64Logs >= 1 ? "0" : "1");
        envVars.put("BOX64_DYNAREC", "1");
        envVars.put("BOX64_UNITYPLAYER", "0");

        if (box64Logs >= 1) {
            envVars.put("BOX64_LOG", "1");
            envVars.put("BOX64_DYNAREC_MISSING", "1");

            if (box64Logs == 2) {
                envVars.put("BOX64_SHOWSEGV", "1");
                envVars.put("BOX64_DLSYM_ERROR", "1");
                envVars.put("BOX64_TRACE_FILE", "stderr");

                if (saveToFile) {
                    File parent = (new File(preferences.getString("log_file", LogView.getLogFile().getPath()))).getParentFile();
                    if (parent != null && parent.isDirectory()) {
                        File traceDir = new File(parent, "trace");
                        if (!traceDir.isDirectory()) traceDir.mkdirs();
                        FileUtils.clear(traceDir);

                        envVars.put("BOX64_TRACE_FILE", traceDir+"/box64-%pid.txt");
                    }
                }
            }
        }

        envVars.putAll(Box64PresetManager.getEnvVars(context, box64Preset));

        File box64RCFile = new File(rootFS.getRootDir(), "/etc/config.box64rc");
        envVars.put("BOX64_RCFILE", box64RCFile.getPath());
    }

    private void addFEXEnvVars(EnvVars envVars) {
        if (fexPreset == 0) {
            envVars.put("HODLL", "libwow64fex.dll");
        } else if (fexPreset == 1) {
            envVars.put("HODLL", "wowbox64.dll");
        } else {
            envVars.remove("HODLL");
        }
        
        if (fexPresetCustom != null && !fexPresetCustom.isEmpty()) {
            // TODO: 实现 FEX 预设管理器
            // envVars.putAll(FEXPresetManager.getEnvVars(environment.getContext(), fexPresetCustom));
        }
    }

    @Override
    public void onPause() {
        synchronized (lock) {
            if (pid != -1) {
                List<ProcessHelper.PStat> processes = ProcessHelper.getChildProcesses();
                for (int i = processes.size() - 1; i >= 0; i--) {
                    ProcessHelper.PStat process = processes.get(i);
                    if (process.guestProcess && process.state != ProcessHelper.PState.STOPPED) {
                        ProcessHelper.suspendProcess(process.pid);
                    }
                }
            }
        }
    }

    @Override
    public void onResume() {
        synchronized (lock) {
            if (pid != -1) {
                List<ProcessHelper.PStat> processes = ProcessHelper.getChildProcesses();
                for (int i = 0; i < processes.size(); i++) {
                    ProcessHelper.PStat process = processes.get(i);
                    if (process.guestProcess && process.state == ProcessHelper.PState.STOPPED) {
                        ProcessHelper.resumeProcess(process.pid);
                    }
                }
            }
        }
    }
}
