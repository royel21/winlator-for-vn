package com.winlator.container;

import com.winlator.box64.Box64Preset;
import com.winlator.core.AppUtils;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.core.KeyValueSet;
import com.winlator.core.WineInfo;
import com.winlator.core.WineThemeManager;
import com.winlator.win32.WinVersions;
import com.winlator.widget.FrameRating;
import com.winlator.xenvironment.RootFS;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;

public class Container {
    public static final String DEFAULT_ENV_VARS = "LC_ALL=ja_JP.utf8 ZINK_DESCRIPTORS=lazy ZINK_DEBUG=compact MESA_SHADER_CACHE_MAX_SIZE=512MB TU_DEBUG=sysmem,noconform,nofsdt,gmem MESA_GL_VERSION_OVERRIDE=3.1 TZ=Asia/Tokyo MESA_VK_WSI_DEBUG=sw MESA_EXTENSION_MAX_YEAR=2025 BOX64_DYNAREC_WEAKBARRIER=-1 mesa_glthread=true WINEESYNC=1 MESA_SHADER_CACHE_DISABLE=false DXVK_ASYNC=1 BOX64_MMAP32=1 LIBGL_ALWAYS_SOFTWARE=0 DRAW_USE_LLVM=0 GST_DEBUG=0 MANGOHUD=0 MANGOHUD_CONFIG=fps,frame_timing=0,ram,gpu_name,vulkan_driver,cpu_mhz,arch,exec_name,swap,font_size=24,engine_version,position=top-left,background_alpha=0.0,hud_no_margin";
    public static final String DEFAULT_SCREEN_SIZE = "1280x720";
    public static final String DEFAULT_SCREEN_ORIENTATION = "landscape";
    public static final boolean DEFAULT_SWAP_RESOLUTION = false;
    public static final String DEFAULT_AUDIO_DRIVER = AudioDrivers.PULSEAUDIO;
    public static final String DEFAULT_DXWRAPPER = DXWrappers.DXVK;
    public static final String DEFAULT_WINCOMPONENTS = "direct3d=1,directsound=1,directmusic=1,directshow=1,directplay=0,xaudio=1,vcrun2005=0,vcrun2010=1,wmdecoder=1";
    public static final String FALLBACK_WINCOMPONENTS = "direct3d=0,directsound=0,directmusic=0,directshow=0,directplay=0,xaudio=0,vcrun2005=0,vcrun2010=0,wmdecoder=0";
    public static final String DEFAULT_DRIVES = "E:"+AppUtils.DIRECTORY_DOWNLOADS +"D:"+AppUtils.INTERNAL_STORAGE;
    public static final byte STARTUP_SELECTION_NORMAL = 0;
    public static final byte STARTUP_SELECTION_ESSENTIAL = 1;
    public static final byte STARTUP_SELECTION_AGGRESSIVE = 2;
    public static final byte MAX_DRIVE_LETTERS = 8;
    public final int id;
    private String name;
    private String screenSize = DEFAULT_SCREEN_SIZE;
    private String screenOrientation = DEFAULT_SCREEN_ORIENTATION;
    private boolean swapResolution = DEFAULT_SWAP_RESOLUTION;
    private String envVars = DEFAULT_ENV_VARS;
    private String graphicsDriver = GraphicsDrivers.DEFAULT_VULKAN_DRIVER+","+ GraphicsDrivers.DEFAULT_OPENGL_DRIVER;
    private String dxwrapper = DEFAULT_DXWRAPPER;
    private String dxwrapperConfig = "";
    private String graphicsDriverConfig = "";
    private String audioDriverConfig = "";
    private String wincomponents = DEFAULT_WINCOMPONENTS;
    private String audioDriver = DEFAULT_AUDIO_DRIVER;
    private String drives = DEFAULT_DRIVES;
    private String wineVersion = WineInfo.MAIN_WINE_VERSION.identifier();
    private byte hudMode = (byte)FrameRating.Mode.SIMPLE.ordinal();
    private byte startupSelection = STARTUP_SELECTION_ESSENTIAL;
    private boolean startAsFullscreen = true;
    private String cpuList;
    private String cpuListWoW64;
    private String desktopTheme = WineThemeManager.DEFAULT_DESKTOP_THEME;
    private String winVersion = WinVersions.DEFAULT_VERSION;
    private int logPixels = 96;
    private String mouseWarpOverride = "disable";
    private String box64Preset = Box64Preset.DEFAULT;
    private String box64Version = DefaultVersion.BOX64;
    private String fexVersion = "FEX-2603";
    private int fexPreset = 0;
    private String fexPresetCustom = com.winlator.fex.FEXPreset.COMPATIBILITY;
    private File rootDir;
    private JSONObject extraData;

    public Container(int id) {
        this.id = id;
        this.name = "Container-"+id;
    }

    public Container(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScreenSize() {
        return screenSize;
    }

    public void setScreenSize(String screenSize) {
        this.screenSize = screenSize;
    }

    public String getScreenOrientation() {
        return screenOrientation;
    }

    public void setScreenOrientation(String screenOrientation) {
        this.screenOrientation = screenOrientation;
    }

    public boolean isSwapResolution() {
        return swapResolution;
    }

    public void setSwapResolution(boolean swapResolution) {
        this.swapResolution = swapResolution;
    }

    public String getEnvVars() {
        return envVars;
    }

    public void setEnvVars(String envVars) {
        this.envVars = envVars != null ? envVars : "";
    }

    public String getGraphicsDriver() {
        return graphicsDriver;
    }

    public void setGraphicsDriver(String graphicsDriver) {
        this.graphicsDriver = graphicsDriver;
    }

    public String getDXWrapper() {
        return dxwrapper;
    }

    public void setDXWrapper(String dxwrapper) {
        this.dxwrapper = dxwrapper;
    }

    public String getGraphicsDriverConfig() {
        return graphicsDriverConfig;
    }

    public void setGraphicsDriverConfig(String graphicsDriverConfig) {
        this.graphicsDriverConfig = graphicsDriverConfig != null ? graphicsDriverConfig : "";
    }

    public String getDXWrapperConfig() {
        return dxwrapperConfig;
    }

    public void setDXWrapperConfig(String dxwrapperConfig) {
        this.dxwrapperConfig = dxwrapperConfig != null ? dxwrapperConfig : "";
    }

    public String getAudioDriverConfig() {
        return audioDriverConfig;
    }

    public void setAudioDriverConfig(String audioDriverConfig) {
        this.audioDriverConfig = audioDriverConfig != null ? audioDriverConfig : "";
    }

    public String getAudioDriver() {
        return audioDriver;
    }

    public void setAudioDriver(String audioDriver) {
        this.audioDriver = audioDriver;
    }

    public String getWinComponents() {
        return wincomponents;
    }

    public void setWinComponents(String wincomponents) {
        this.wincomponents = wincomponents;
    }

    public String getDrives() {
        return drives;
    }

    public void setDrives(String drives) {
        this.drives = drives;
    }

    public byte getHUDMode() {
        return hudMode;
    }

    public void setHUDMode(byte hudMode) {
        this.hudMode = hudMode;
    }

    public byte getStartupSelection() {
        return startupSelection;
    }

    public void setStartupSelection(byte startupSelection) {
        this.startupSelection = startupSelection;
    }

    public boolean isStartAsFullscreen() {
        return startAsFullscreen;
    }

    public void setStartAsFullscreen(boolean startAsFullscreen) {
        this.startAsFullscreen = startAsFullscreen;
    }

    public String getCPUList() {
        return getCPUList(false);
    }

    public String getCPUList(boolean allowFallback) {
        return cpuList != null ? cpuList : (allowFallback ? getFallbackCPUList() : null);
    }

    public void setCPUList(String cpuList) {
        this.cpuList = cpuList != null && !cpuList.isEmpty() ? cpuList : null;
    }

    public String getCPUListWoW64() {
        return getCPUListWoW64(false);
    }

    public String getCPUListWoW64(boolean allowFallback) {
        return cpuListWoW64 != null ? cpuListWoW64 : (allowFallback ? getFallbackCPUListWoW64() : null);
    }

    public void setCPUListWoW64(String cpuListWoW64) {
        this.cpuListWoW64 = cpuListWoW64 != null && !cpuListWoW64.isEmpty() ? cpuListWoW64 : null;
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

    public File getRootDir() {
        return rootDir;
    }

    public void setRootDir(File rootDir) {
        this.rootDir = rootDir;
    }

    public void setExtraData(JSONObject extraData) {
        this.extraData = extraData;
    }

    public String getExtra(String name) {
        return getExtra(name, "");
    }

    public String getExtra(String name, String fallback) {
        try {
            return extraData != null && extraData.has(name) ? extraData.getString(name) : fallback;
        }
        catch (JSONException e) {
            return fallback;
        }
    }

    public void putExtra(String name, Object value) {
        if (extraData == null) extraData = new JSONObject();
        try {
            if (value != null) {
                extraData.put(name, value);
            }
            else extraData.remove(name);
        }
        catch (JSONException e) {}
    }

    public String getWineVersion() {
        return wineVersion;
    }

    public void setWineVersion(String wineVersion) {
        this.wineVersion = wineVersion;
    }

    public File getConfigFile() {
        return new File(rootDir, ".container");
    }

    public File getUserDir() {
        return new File(rootDir, ".wine/drive_c/users/"+ RootFS.USER+"/");
    }

    public File getStartMenuDir() {
        return new File(rootDir, ".wine/drive_c/ProgramData/Microsoft/Windows/Start Menu/");
    }

    public File getIconsDir(int size) {
        return new File(rootDir, ".local/share/icons/hicolor/"+size+"x"+size+"/apps/");
    }

    public String getDesktopTheme() {
        return desktopTheme;
    }

    public void setDesktopTheme(String desktopTheme) {
        this.desktopTheme = desktopTheme;
    }

    public String getWinVersion() {
        return winVersion;
    }

    public void setWinVersion(String winVersion) {
        this.winVersion = winVersion;
    }

    public int getLogPixels() {
        return logPixels;
    }

    public void setLogPixels(int logPixels) {
        this.logPixels = logPixels;
    }

    public String getMouseWarpOverride() {
        return mouseWarpOverride;
    }

    public void setMouseWarpOverride(String mouseWarpOverride) {
        this.mouseWarpOverride = mouseWarpOverride;
    }

    public boolean hasDrive(String path) {
        for (Drive drive : drivesIterator()) {
            if (drive.path.equals(path)) return true;
        }
        return false;
    }

    public void addDrive(String path) {
        if (hasDrive(path)) return;
        char nextLetter = '\0';
        for (int i = 0; i < MAX_DRIVE_LETTERS; i++) {
            char letter = (char)('D' + i);
            boolean used = false;
            for (Drive drive : drivesIterator()) {
                if (drive.letter.equalsIgnoreCase(String.valueOf(letter))) {
                    used = true;
                    break;
                }
            }
            if (!used) {
                nextLetter = letter;
                break;
            }
        }
        if (nextLetter != '\0') drives += nextLetter + ":" + path;
    }

    public Iterable<Drive> drivesIterator() {
        return drivesIterator(drives);
    }

    public static Iterable<Drive> drivesIterator(final String drives) {
        final int[] index = {drives.indexOf(":")};
        return () -> new Iterator<Drive>() {
            @Override
            public boolean hasNext() {
                return index[0] != -1;
            }

            @Override
            public Drive next() {
                String letter = String.valueOf(drives.charAt(index[0]-1));
                int nextIndex = drives.indexOf(":", index[0]+1);
                String path = drives.substring(index[0]+1, nextIndex != -1 ? nextIndex-1 : drives.length());
                index[0] = nextIndex;
                return new Drive(letter, path);
            }
        };
    }

    public JSONObject getData() {
        try {
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("name", name);
            data.put("screenSize", screenSize);
            data.put("screenOrientation", screenOrientation);
            data.put("swapResolution", swapResolution);
            data.put("envVars", envVars);
            data.put("cpuList", cpuList);
            data.put("cpuListWoW64", cpuListWoW64);
            data.put("graphicsDriver", graphicsDriver);
            data.put("dxwrapper", dxwrapper);
            if (!dxwrapperConfig.isEmpty()) data.put("dxwrapperConfig", dxwrapperConfig);
            if (!graphicsDriverConfig.isEmpty()) data.put("graphicsDriverConfig", graphicsDriverConfig);
            if (!audioDriverConfig.isEmpty()) data.put("audioDriverConfig", audioDriverConfig);
            data.put("audioDriver", audioDriver);
            data.put("wincomponents", wincomponents);
            data.put("drives", drives);
            data.put("hudMode", hudMode);
            data.put("startupSelection", startupSelection);
            data.put("startAsFullscreen", startAsFullscreen);
            data.put("box64Preset", box64Preset);
            data.put("box64Version", box64Version);
            data.put("fexVersion", fexVersion);
            data.put("fexPreset", fexPreset);
            data.put("fexPresetCustom", fexPresetCustom);
            data.put("desktopTheme", desktopTheme);
            data.put("winVersion", winVersion);
            data.put("logPixels", logPixels);
            data.put("mouseWarpOverride", mouseWarpOverride);
            data.put("extraData", extraData);

            if (wineVersion != null && !wineVersion.isEmpty()) {
                data.put("wineVersion", wineVersion);
            }
            return data;
        }
        catch (JSONException e) {
            return null;
        }
    }

    public void saveData() {
        JSONObject data = getData();
        if (data != null) FileUtils.writeString(getConfigFile(), data.toString());
    }

    public void loadData(JSONObject data) throws JSONException {
        checkObsoleteOrMissingProperties(data);

        for (Iterator<String> it = data.keys(); it.hasNext(); ) {
            String key = it.next();
            switch (key) {
                case "name" :
                    setName(data.optString(key, getName()));
                    break;
                case "screenSize" :
                    setScreenSize(data.optString(key, getScreenSize()));
                    break;
                case "screenOrientation" :
                    setScreenOrientation(data.optString(key, getScreenOrientation()));
                    break;
                case "swapResolution" :
                    setSwapResolution(data.optBoolean(key, isSwapResolution()));
                    break;
                case "envVars" :
                    setEnvVars(data.optString(key, getEnvVars()));
                    break;
                case "cpuList" :
                    setCPUList(data.optString(key, getCPUList()));
                    break;
                case "cpuListWoW64" :
                    setCPUListWoW64(data.optString(key, getCPUListWoW64()));
                    break;
                case "graphicsDriver" :
                    setGraphicsDriver(data.optString(key, getGraphicsDriver()));
                    break;
                case "wincomponents" :
                    setWinComponents(data.optString(key, getWinComponents()));
                    break;
                case "dxwrapper" :
                    setDXWrapper(data.optString(key, getDXWrapper()));
                    break;
                case "dxwrapperConfig" :
                    setDXWrapperConfig(data.optString(key, getDXWrapperConfig()));
                    break;
                case "graphicsDriverConfig" :
                    setGraphicsDriverConfig(data.optString(key, getGraphicsDriverConfig()));
                    break;
                case "audioDriver" :
                    setAudioDriver(data.optString(key, getAudioDriver()));
                    break;
                case "audioDriverConfig" :
                    setAudioDriverConfig(data.optString(key, getAudioDriverConfig()));
                    break;
                case "drives" :
                    setDrives(data.optString(key, getDrives()));
                    break;
                case "showFPS" :
                    setHUDMode((byte)(data.optBoolean(key) ? FrameRating.Mode.SIMPLE.ordinal() : FrameRating.Mode.DISABLED.ordinal()));
                    break;
                case "hudMode" :
                    setHUDMode((byte)data.optInt(key, getHUDMode()));
                    break;
                case "startupSelection" :
                    setStartupSelection((byte)data.optInt(key, getStartupSelection()));
                    break;
                case "startAsFullscreen" :
                    setStartAsFullscreen(data.optBoolean(key, isStartAsFullscreen()));
                    break;
                case "extraData" : {
                    JSONObject extraData = data.optJSONObject(key);
                    if (extraData != null) {
                        checkObsoleteOrMissingProperties(extraData);
                        setExtraData(extraData);
                    }
                    break;
                }
                case "wineVersion" :
                    setWineVersion(data.optString(key, getWineVersion()));
                    break;
                case "box64Preset" :
                    setBox64Preset(data.optString(key, getBox64Preset()));
                    break;
                case "box64Version" :
                    setBox64Version(data.optString(key, getBox64Version()));
                    break;
                case "fexVersion" :
                    setFexVersion(data.optString(key, getFexVersion()));
                    break;
                case "fexPreset" :
                    setFexPreset(data.optInt(key, getFexPreset()));
                    break;
                case "fexPresetCustom" :
                    setFexPresetCustom(data.optString(key, getFexPresetCustom()));
                    break;
                case "desktopTheme" :
                    setDesktopTheme(data.optString(key, getDesktopTheme()));
                    break;
                case "winVersion" :
                    setWinVersion(data.optString(key, getWinVersion()));
                    break;
                case "logPixels" :
                    setLogPixels(data.optInt(key, getLogPixels()));
                    break;
                case "mouseWarpOverride" :
                    setMouseWarpOverride(data.optString(key, getMouseWarpOverride()));
                    break;
            }
        }
    }

    public static void checkObsoleteOrMissingProperties(JSONObject data) {
        try {
            if (data.has("extraData")) {
                JSONObject extraData = data.getJSONObject("extraData");
                int appVersion = Integer.parseInt(extraData.optString("appVersion", "0"));

                if (appVersion < 16 && data.has("envVars")) {
                    EnvVars defaultEnvVars = new EnvVars(DEFAULT_ENV_VARS);
                    EnvVars envVars = new EnvVars(data.getString("envVars"));
                    for (String name : defaultEnvVars) if (!envVars.has(name)) envVars.put(name, defaultEnvVars.get(name));
                    data.put("envVars", envVars.toString());
                }
            }

            KeyValueSet wincomponents1 = new KeyValueSet(DEFAULT_WINCOMPONENTS);
            KeyValueSet wincomponents2 = new KeyValueSet(data.optString("wincomponents", ""));
            if (wincomponents2.iterator().hasNext()) {
                StringBuilder sb = new StringBuilder();
                for (String[] wincomponent1 : wincomponents1) {
                    String value = wincomponent1[1];
                    for (String[] wincomponent2 : wincomponents2) {
                        if (wincomponent1[0].equals(wincomponent2[0])) {
                            value = wincomponent2[1];
                            break;
                        }
                    }
                    sb.append(!sb.toString().isEmpty() ? "," : "").append(wincomponent1[0]).append("=").append(value);
                }
                data.put("wincomponents", sb.toString());
            }
        }
        catch (JSONException e) {}
    }

    public static String getFallbackCPUList() {
        String cpuList = "";
        int numProcessors = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < numProcessors; i++) cpuList += (!cpuList.isEmpty() ? "," : "")+i;
        return cpuList;
    }

    public static String getFallbackCPUListWoW64() {
        String cpuList = "";
        int numProcessors = Runtime.getRuntime().availableProcessors();
        for (int i = numProcessors / 2; i < numProcessors; i++) cpuList += (!cpuList.isEmpty() ? "," : "")+i;
        return cpuList;
    }
}
