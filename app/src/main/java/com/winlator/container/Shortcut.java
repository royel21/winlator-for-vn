package com.winlator.container;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import android.util.LruCache;

import com.winlator.core.FileUtils;
import com.winlator.core.StringUtils;
import com.winlator.winhandler.GamepadHandler;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Iterator;

public class Shortcut {
    public final Container container;
    public final String name;
    public final String path;
    private Bitmap icon;
    public final File file;
    public final File iconFile;
    public final String wmClass;
    private final JSONObject extraData = new JSONObject();
    private static final LruCache<String, Bitmap> iconCache = new LruCache<>(64);

    public Shortcut(Container container, File file) {
        this.container = container;
        this.file = file;

        if (file.isDirectory()) {
            this.name = file.getName();
            this.path = null;
            this.icon = null;
            this.iconFile = null;
            this.wmClass = "";
        }
        else {
            String execArgs = "";
            Bitmap icon = null;
            File iconFile = null;
            String wmClass = "";
            String section = "";

            final short[] iconSizes = {64, 48, 32, 24, 16, 128, 256};
            int index;
            for (String line : FileUtils.readLines(file, true)) {
                if (line.startsWith("#")) continue;
                if (line.startsWith("[")) {
                    section = line.substring(1, line.indexOf("]"));
                }
                else {
                    index = line.indexOf("=");
                    if (index == -1) continue;
                    String key = line.substring(0, index);
                    String value = line.substring(index+1);

                    if (section.equals("Desktop Entry")) {
                        if (key.equals("Exec")) execArgs = value;
                        if (key.equals("Icon")) {
                            for (short iconSize : iconSizes) {
                                iconFile = new File(container.getIconsDir(iconSize), value+".png");
                                if (iconFile.isFile()) break;
                            }
                        }
                        if (key.equals("StartupWMClass")) wmClass = value;
                    }
                    else if (section.equals("Extra Data")) {
                        try {
                            extraData.put(key, value);
                        }
                        catch (JSONException e) {}
                    }
                }
            }

            this.name = FileUtils.getBasename(file.getPath());
            this.iconFile = iconFile;
            this.wmClass = wmClass;

            int indexOf;
            int lastIndexOf = execArgs.lastIndexOf("wine ");
            String path = execArgs;
            if (lastIndexOf != -1) {
                path = StringUtils.unescapeDOSPath(execArgs.substring(lastIndexOf + 5));
            }
            int index2 = path.indexOf("start.exe ");
            path = index2 != -1 ? path.substring(index2 + 10) : path;
            int indexOf2 = path.indexOf("\"");
            if (indexOf2 != -1 && (indexOf = path.indexOf("\"", indexOf2 + 1)) != -1) {
                path = path.substring(indexOf2 + 1, indexOf);
            }
            this.path = path;
            Container.checkObsoleteOrMissingProperties(extraData);
        }
    }

    public String getExtra(String name) {
        return getExtra(name, "");
    }

    public String getExtra(String name, String fallback) {
        try {
            return extraData.has(name) ? extraData.getString(name) : fallback;
        }
        catch (JSONException e) {
            return fallback;
        }
    }

    public void putExtra(String name, String value) {
        try {
            if (value != null) {
                extraData.put(name, value);
            }
            else extraData.remove(name);
        }
        catch (JSONException e) {}
    }

    public JSONObject getData() {
        try {
            JSONObject data = new JSONObject();
            data.put("name", name);
            data.put("path", path != null ? path : "");
            data.put("wmClass", wmClass != null ? wmClass : "");
            data.put("icon", iconFile != null ? iconFile.getName().replace(".png", "") : "");
            data.put("execArgs", getExtra("execArgs", ""));
            data.put("forceFullscreen", getExtra("forceFullscreen", "0"));

            JSONObject extra = new JSONObject(extraData.toString());

            // Remove unique/local identifiers from extraData payload
            extra.remove("id");
            extra.remove("containerId");
            extra.remove("name");
            extra.remove("wmClass");
            extra.remove("path");

            if (!extra.has("screenSize")) extra.put("screenSize", getExtra("screenSize", container.getScreenSize()));
            if (!extra.has("screenOrientation")) extra.put("screenOrientation", getExtra("screenOrientation", container.getScreenOrientation()));
            if (!extra.has("swapResolution")) extra.put("swapResolution", getExtra("swapResolution", String.valueOf(container.isSwapResolution())));
            if (!extra.has("graphicsDriver")) extra.put("graphicsDriver", getExtra("graphicsDriver", container.getGraphicsDriver()));
            if (!extra.has("dxwrapper")) extra.put("dxwrapper", getExtra("dxwrapper", container.getDXWrapper()));
            if (!extra.has("dxwrapperConfig")) extra.put("dxwrapperConfig", getExtra("dxwrapperConfig", container.getDXWrapperConfig()));
            if (!extra.has("graphicsDriverConfig")) extra.put("graphicsDriverConfig", getExtra("graphicsDriverConfig", container.getGraphicsDriverConfig()));
            if (!extra.has("audioDriver")) extra.put("audioDriver", getExtra("audioDriver", container.getAudioDriver()));
            if (!extra.has("audioDriverConfig")) extra.put("audioDriverConfig", getExtra("audioDriverConfig", container.getAudioDriverConfig()));
            if (!extra.has("wincomponents")) extra.put("wincomponents", getExtra("wincomponents", container.getWinComponents()));
            if (!extra.has("envVars")) extra.put("envVars", getExtra("envVars", container.getEnvVars()));
            if (!extra.has("box64Preset")) extra.put("box64Preset", getExtra("box64Preset", container.getBox64Preset()));
            if (!extra.has("box64Version")) extra.put("box64Version", getExtra("box64Version", container.getBox64Version()));
            if (!extra.has("controlsProfile")) extra.put("controlsProfile", getExtra("controlsProfile", String.valueOf(container.getControlsProfile())));
            if (!extra.has("dinputMapperType")) extra.put("dinputMapperType", getExtra("dinputMapperType", String.valueOf(GamepadHandler.DINPUT_MAPPER_TYPE_XINPUT)));
            if (!extra.has("preferredInputApi")) extra.put("preferredInputApi", getExtra("preferredInputApi", String.valueOf(GamepadHandler.PreferredInputApi.AUTO.ordinal())));
            if (!extra.has("forceFullscreen")) extra.put("forceFullscreen", getExtra("forceFullscreen", "0"));
            if (!extra.has("startAsFullscreen")) extra.put("startAsFullscreen", getExtra("startAsFullscreen", String.valueOf(container.isStartAsFullscreen())));
            if (!extra.has("useEGL")) extra.put("useEGL", getExtra("useEGL", container.getExtra("useEGL", "y")));
            if (!extra.has("mouseWarpOverride")) extra.put("mouseWarpOverride", getExtra("mouseWarpOverride", container.getMouseWarpOverride()));

            data.put("extraData", extra);
            return data;
        }
        catch (JSONException e) {
            return null;
        }
    }
    public JSONObject getFullData() {
        try {
            JSONObject data = new JSONObject();
            data.put("name", name);
            data.put("path", path);
            data.put("wmClass", wmClass);
            data.put("extraData", extraData);
            return data;
        }
        catch (JSONException e) {
            return null;
        }
    }


    public void saveData() {
        String content = "[Desktop Entry]\n";
        for (String line : FileUtils.readLines(file)) {
            if (line.contains("[Extra Data]")) break;
            if (!line.contains("[Desktop Entry]") && !line.isEmpty()) content += line+"\n";
        }

        if (extraData.length() > 0) {
            content += "\n[Extra Data]\n";
            Iterator<String> keys = extraData.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                try {
                    content += key + "=" + extraData.getString(key) + "\n";
                }
                catch (JSONException e) {}
            }
        }

        FileUtils.writeString(file, content);
    }

    public Bitmap getIcon() {
        if (icon == null && iconFile != null && iconFile.exists()) {
            String cacheKey = iconFile.getAbsolutePath();
            icon = iconCache.get(cacheKey);
            if (icon == null) {
                icon = BitmapFactory.decodeFile(cacheKey);
                if (icon != null) iconCache.put(cacheKey, icon);
            }
        }
        return icon;
    }

    public File getLinkFile() {
        String name = file.getName().replace(".desktop", ".lnk");
        return new File(file.getParentFile(), name);
    }

    public void remove() {
        if (file.isDirectory()) {
            FileUtils.delete(file);
        }
        else {
            File linkFile = getLinkFile();
            if (file.delete()) {
                if (iconFile != null) iconFile.delete();
                if (linkFile.isFile()) linkFile.delete();
            }
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Shortcut) {
            return ((Shortcut)obj).file.getAbsolutePath().equals(file.getAbsolutePath());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return file.getAbsolutePath().hashCode();
    }
}
