package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.PreferenceManager;

import com.winlator.box64.Box64Preset;
import com.winlator.box64.Box64PresetManager;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.Drive;
import com.winlator.container.GraphicsDrivers;
import com.winlator.contentdialog.AddEnvVarDialog;
import com.winlator.contentdialog.AudioDriverConfigDialog;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.VortekConfigDialog;
import com.winlator.contents.ContentsManager;
import com.winlator.contents.ContentProfile;
import com.winlator.core.AppUtils;
import com.winlator.core.Callback;
import com.winlator.container.DXWrapperPicker;
import com.winlator.core.DefaultVersion;
import com.winlator.core.EnvVars;
import com.winlator.core.FileUtils;
import com.winlator.container.GraphicsDriverPicker;
import com.winlator.core.GeneralComponents;
import com.winlator.core.KeyValueSet;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.StringUtils;
import com.winlator.core.WineInfo;
import com.winlator.core.WineInstaller;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;
import com.winlator.fex.FEXPreset;
import com.winlator.fex.FEXPresetManager;
import com.winlator.widget.CPUListView;
import com.winlator.widget.ColorPickerView;
import com.winlator.widget.EnvVarsView;
import com.winlator.widget.FrameRating;
import com.winlator.widget.ImagePickerView;
import com.winlator.widget.SeekBar;
import com.winlator.win32.MSLogFont;
import com.winlator.win32.WinVersions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class ContainerDetailFragment extends Fragment {
    private ContainerManager manager;
    private final int containerId;
    private Container container;
    private PreloaderDialog preloaderDialog;
    private Callback<String> openDirectoryCallback;
    private EditText etName;
    private Spinner sWineVersion;
    private Spinner sWinVersion;
    private GraphicsDriverPicker graphicsDriverPicker;
    private DXWrapperPicker dxwrapperPicker;
    private Spinner sAudioDriver;
    private View vAudioDriverConfig;
    private Spinner sHUDMode;
    private Spinner sStartupSelection;
    private Spinner sBox64Version;
    private Spinner sBox64Preset;
    private Spinner sFEXVersion;
    private Spinner sFEXPreset;
    private Spinner sFEXPresetCustom;
    private EnvVarsView envVarsView;
    private CPUListView cpuListView;
    private CPUListView cpuListViewWoW64;

    private SeekBar sbLogPixelsView;

    private Spinner sSystemFont;

    public ContainerDetailFragment() {
        this(0);
    }

    public ContainerDetailFragment(int containerId) {
        this.containerId = containerId;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == MainActivity.OPEN_DIRECTORY_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String path = FileUtils.getFilePathFromUri(data.getData());
                if (path != null) {
                    if (openDirectoryCallback != null) openDirectoryCallback.call(path);
                } else {
                    AppUtils.showToast(getContext(), R.string.unable_to_import_profile);
                }
            }
            openDirectoryCallback = null;
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(isEditMode() ? R.string.edit_container : R.string.new_container);
    }

    public boolean isEditMode() {
        return container != null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup root, @Nullable Bundle savedInstanceState) {
        final Context context = getContext();
        final View view = inflater.inflate(R.layout.container_detail_fragment, root, false);
        manager = new ContainerManager(context);
        container = containerId > 0 ? manager.getContainerById(containerId) : null;

        etName = view.findViewById(R.id.ETName);
        sWineVersion = view.findViewById(R.id.SWineVersion);
        final View flBox64 = view.findViewById(R.id.FLBox64);
        final View flFEX = view.findViewById(R.id.FLFEX);

        sWineVersion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                String wineVersionIdentifier = sWineVersion.getSelectedItem().toString();
                WineInfo wineInfo = WineInfo.fromIdentifier(context, wineVersionIdentifier);
                boolean isArm64EC = wineInfo != null && wineInfo.getArch() != null && wineInfo.getArch().equals("arm64ec");
                flBox64.setVisibility(isArm64EC ? View.GONE : View.VISIBLE);
                flFEX.setVisibility(isArm64EC ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        sAudioDriver = view.findViewById(R.id.SAudioDriver);
        vAudioDriverConfig = view.findViewById(R.id.BTAudioDriverConfig);
        vAudioDriverConfig.setOnClickListener((v) -> (new AudioDriverConfigDialog(v)).show());

        sHUDMode = view.findViewById(R.id.SHUDMode);
        sStartupSelection = view.findViewById(R.id.SStartupSelection);
        sWinVersion = view.findViewById(R.id.SWinVersion);
        sBox64Version = view.findViewById(R.id.SBox64Version);
        sBox64Preset = view.findViewById(R.id.SBox64Preset);
        sFEXVersion = view.findViewById(R.id.SFEXVersion);
        sFEXPreset = view.findViewById(R.id.SFEXPreset);
        sFEXPresetCustom = view.findViewById(R.id.SFEXPresetCustom);
        cpuListView = view.findViewById(R.id.CPUListView);
        cpuListViewWoW64 = view.findViewById(R.id.CPUListViewWoW64);
        envVarsView = view.findViewById(R.id.EnvVarsView);
        sSystemFont = view.findViewById(R.id.SSystemFont);
        sbLogPixelsView = view.findViewById(R.id.SBLogPixels);

        loadUIFromContainer(container, view);

        AppUtils.setupTabLayout(view, R.id.TabLayout, (tabResId) -> {
            if (tabResId == R.id.LLTabAdvanced) if ((byte)sWinVersion.getTag() == -1) WinVersions.loadSpinner(container, sWinVersion);
        }, R.id.LLTabWineConfiguration, R.id.LLTabWinComponents, R.id.LLTabEnvVars, R.id.LLTabDrives, R.id.LLTabAdvanced);

        view.findViewById(R.id.BTConfirm).setOnClickListener((v) -> {
            try {
                if (isEditMode()) {
                    updateContainerFromUI(container, view);
                    container.saveData();
                    saveWineRegistryKeys(view);

                    if (container.getGraphicsDriver().equals(GraphicsDrivers.VORTEK) && VortekConfigDialog.isRequireRestart(container.getGraphicsDriverConfig(), graphicsDriverPicker.getGraphicsDriverConfig())) {
                        ContentDialog.confirm(context, R.string.the_settings_have_been_changed_do_you_want_to_restart_the_app, () -> AppUtils.restartApplication(context));
                    }

                    getActivity().onBackPressed();
                }
                else {
                    Container dummyContainer = new Container(0);
                    updateContainerFromUI(dummyContainer, view);
                    JSONObject data = dummyContainer.getData();

                    String wineVersion = sWineVersion.getSelectedItem().toString();
                    if (!wineVersion.isEmpty()) {
                        data.put("wineVersion", wineVersion);
                    }

                    preloaderDialog.show(R.string.creating_container);
                    manager.createContainerAsync(data, (newContainer) -> {
                        if (newContainer != null) {
                            this.container = newContainer;
                            saveWineRegistryKeys(view);
                        }
                        preloaderDialog.close();
                        getActivity().onBackPressed();
                    });
                }
            }
            catch (JSONException e) {}
        });
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.container_detail_menu, menu);
        if (!isEditMode()) menu.findItem(R.id.menu_item_export).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        MainActivity activity = (MainActivity)getActivity();
        int itemId = menuItem.getItemId();
        if (itemId == R.id.menu_item_export) {
            if (isEditMode()) {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, container.getName() + ".json");
                if (activity != null) {
                    activity.setCreateFileCallback((uri) -> {
                        if (uri != null) {
                            File cacheDir = getContext().getCacheDir();
                            if (cacheDir != null) {
                                File tempFile = new File(cacheDir, "export.json");
                                updateContainerFromUI(container, getView());
                                manager.exportConfigAsync(container, tempFile, () -> {
                                    try (ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "w");
                                         FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                                         FileInputStream fis = new FileInputStream(tempFile)) {
                                        byte[] buffer = new byte[4096];
                                        int len;
                                        while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
                                        AppUtils.showToast(getContext(), "Exported successfully");
                                    }
                                    catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                        }
                    });
                    activity.startActivityForResult(intent, MainActivity.CREATE_FILE_REQUEST_CODE);
                }
            }
            return true;
        }
        else if (itemId == R.id.menu_item_import) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            if (activity != null) {
                activity.setOpenFileCallback((uri) -> {
                    if (uri != null) {
                        File cacheDir = getContext().getCacheDir();
                        if (cacheDir != null) {
                            File tempFile = new File(cacheDir, "import.json");
                            try (ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r");
                                 FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                                byte[] buffer = new byte[4096];
                                int len;
                                while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
                                fos.close();

                                final String json = FileUtils.readString(tempFile);
                                getActivity().runOnUiThread(() -> {
                                    View fragmentView = getView();
                                    if (fragmentView == null) return;
                                    try {
                                        JSONObject data;
                                        if (json.trim().startsWith("[")) {
                                            JSONArray array = new JSONArray(json);
                                            if (array.length() > 0) data = array.getJSONObject(0);
                                            else return;
                                        }
                                        else data = new JSONObject(json);

                                        Container dummyContainer = new Container(0);
                                        dummyContainer.loadData(data);
                                        loadUIFromContainer(dummyContainer, fragmentView);
                                        AppUtils.showToast(getContext(), "Imported settings to UI. Press confirm to save.");
                                    }
                                    catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                });
                            }
                            catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                });
                activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
            }
            return true;
        }
        else return super.onOptionsItemSelected(menuItem);
    }

    private void loadUIFromContainer(Container container, View view) {
        final Context context = getContext();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);

        if (container != null) {
            etName.setText(container.getName());
        }
        else etName.setText(getString(R.string.container)+"-"+manager.getNextContainerId());

        final ArrayList<WineInfo> wineInfos = WineInstaller.getInstalledWineInfos(context);
        loadWineVersionSpinner(view, sWineVersion, wineInfos, container);

        loadScreenSizeSpinner(view, container != null ? container.getScreenSize() : Container.DEFAULT_SCREEN_SIZE);
        loadScreenOrientationSpinner(view, container != null ? container.getScreenOrientation() : Container.DEFAULT_SCREEN_ORIENTATION);
        ((CheckBox)view.findViewById(R.id.CBSwapResolution)).setChecked(container != null ? container.isSwapResolution() : Container.DEFAULT_SWAP_RESOLUTION);
        ((CheckBox)view.findViewById(R.id.CBStartAsFullscreen)).setChecked(container == null || container.isStartAsFullscreen());

        final String oldGraphicsDriverConfig = container != null ? container.getGraphicsDriverConfig() : "";
        String selectedGraphicsDriver = container != null ? container.getGraphicsDriver() : GraphicsDrivers.getDefaultDriver(context);
        graphicsDriverPicker = new GraphicsDriverPicker(view.findViewById(R.id.LLGraphicsDriver), selectedGraphicsDriver, oldGraphicsDriverConfig);

        String oldDXWrapperConfig = container != null ? container.getDXWrapperConfig() : "";
        String selectedDXWrapper = container != null ? container.getDXWrapper() : Container.DEFAULT_DXWRAPPER;
        dxwrapperPicker = new DXWrapperPicker(view.findViewById(R.id.LLDXWrapper), graphicsDriverPicker, selectedDXWrapper, oldDXWrapperConfig);

        AppUtils.setSpinnerSelectionFromIdentifier(sAudioDriver, container != null ? container.getAudioDriver() : Container.DEFAULT_AUDIO_DRIVER);
        vAudioDriverConfig.setTag(container != null ? container.getAudioDriverConfig() : "");

        sHUDMode.setSelection(container != null ? container.getHUDMode() : FrameRating.Mode.SIMPLE.ordinal());

        byte oldStartupSelection = container != null ? container.getStartupSelection() : -1;
        sStartupSelection.setSelection(oldStartupSelection != -1 ? oldStartupSelection : Container.STARTUP_SELECTION_ESSENTIAL);

        sWinVersion.setTag((byte)-1);

        String box64Version = container != null ? container.getBox64Version() : DefaultVersion.BOX64;
        GeneralComponents.initViews(GeneralComponents.Type.BOX64, view.findViewById(R.id.Box64Toolbox), sBox64Version, box64Version, DefaultVersion.BOX64);

        Box64PresetManager.loadSpinner(sBox64Preset, container != null ? container.getBox64Preset() : preferences.getString("box64_preset", Box64Preset.DEFAULT));

        ContentsManager contentsManager = new ContentsManager(context);
        contentsManager.syncContents();
        updateFEXVersionSpinner(context, contentsManager, sFEXVersion);
        if (container != null) {
            AppUtils.setSpinnerSelectionFromValue(sFEXVersion, container.getFexVersion());
        } else {
            AppUtils.setSpinnerSelectionFromValue(sFEXVersion, preferences.getString("fex_version", "FEX-2603"));
        }

        FEXPresetManager.loadSpinner(sFEXPresetCustom, container != null ? container.getFexPresetCustom() : preferences.getString("fex_preset", FEXPreset.COMPATIBILITY));

        if (container != null) {
            sFEXPreset.setSelection(container.getFexPreset());
        } else {
            sFEXPreset.setSelection(0);
        }

        cpuListView.setCheckedCPUList(container != null ? container.getCPUList(true) : Container.getFallbackCPUList());
        cpuListViewWoW64.setCheckedCPUList(container != null ? container.getCPUListWoW64(true) : Container.getFallbackCPUListWoW64());

        createWineConfigurationTab(view);

        final String[] mouseWarpOverrideValues = new String[]{"disable", "enable", "force"};
        Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
        String mouseWarpOverride = container != null ? container.getMouseWarpOverride() : "disable";
        for (int i = 0; i < mouseWarpOverrideValues.length; i++) {
            if (mouseWarpOverrideValues[i].equals(mouseWarpOverride)) {
                sMouseWarpOverride.setSelection(i);
                break;
            }
        }

        envVarsView.setEnvVars(new EnvVars(container != null ? container.getEnvVars() : Container.DEFAULT_ENV_VARS));
        view.findViewById(R.id.BTAddEnvVar).setOnClickListener((v) -> (new AddEnvVarDialog(context, envVarsView)).show());
        
        ViewGroup llTabWinComponents = view.findViewById(R.id.LLTabWinComponents);
        ((ViewGroup)llTabWinComponents.findViewById(R.id.LLWinComponentsDirectX)).removeAllViews();
        ((ViewGroup)llTabWinComponents.findViewById(R.id.LLWinComponentsGeneral)).removeAllViews();
        createWinComponentsTab(view, container != null ? container.getWinComponents() : Container.DEFAULT_WINCOMPONENTS);
        
        ((LinearLayout)view.findViewById(R.id.LLDrives)).removeAllViews();
        createDrivesTab(view);

        sbLogPixelsView.setValue(container != null ? container.getLogPixels() : 96);
        
        WinVersions.loadSpinner(container, sWinVersion);
    }

    private void updateContainerFromUI(Container container, View view) {
        container.setName(etName.getText().toString());
        container.setScreenSize(getScreenSize(view));
        container.setScreenOrientation(getScreenOrientation(view));
        container.setSwapResolution(isSwapResolution(view));
        container.setStartAsFullscreen(((CheckBox)view.findViewById(R.id.CBStartAsFullscreen)).isChecked());
        
        String graphicsDriver = graphicsDriverPicker.getGraphicsDriver();
        container.setGraphicsDriver(graphicsDriver);
        container.setDXWrapper(dxwrapperPicker.getDXWrapper());
        container.setDXWrapperConfig(dxwrapperPicker.getDXWrapperConfig());
        container.setGraphicsDriverConfig(graphicsDriverPicker.getGraphicsDriverConfig());
        
        String envVars = envVarsView.getEnvVars();
        if (graphicsDriver.startsWith(GraphicsDrivers.VORTEK)) {
            EnvVars env = new EnvVars(envVars);
            env.put("MANGOHUD", "0");
            envVars = env.toString();
        }
        container.setEnvVars(envVars);
        
        container.setCPUList(cpuListView.getCheckedCPUListAsString());
        container.setCPUListWoW64(cpuListViewWoW64.getCheckedCPUListAsString());
        container.setAudioDriver(StringUtils.parseIdentifier(sAudioDriver.getSelectedItem()));
        container.setAudioDriverConfig(vAudioDriverConfig.getTag().toString());
        container.setWinComponents(getWinComponents(view));
        container.setDrives(getDrives(view));
        container.setWineVersion(sWineVersion.getSelectedItem().toString());
        container.setHUDMode((byte)sHUDMode.getSelectedItemPosition());
        container.setStartupSelection((byte)sStartupSelection.getSelectedItemPosition());
        container.setBox64Preset(Box64PresetManager.getSpinnerSelectedId(sBox64Preset));
        container.setBox64Version(StringUtils.parseIdentifier(sBox64Version.getSelectedItem()));
        container.setFexVersion(sFEXVersion.getSelectedItem().toString());
        container.setFexPreset(sFEXPreset.getSelectedItemPosition());
        container.setFexPresetCustom(FEXPresetManager.getSpinnerSelectedId(sFEXPresetCustom));
        container.setDesktopTheme(getDesktopTheme(view));

        Object selectedWinVersion = sWinVersion.getSelectedItem();
        String winVersion = selectedWinVersion instanceof WinVersions.WinVersion ? ((WinVersions.WinVersion)selectedWinVersion).version : (container != null ? container.getWinVersion() : WinVersions.DEFAULT_VERSION);
        container.setWinVersion(winVersion);

        int logPixels = (int)sbLogPixelsView.getValue();
        container.setLogPixels(logPixels);

        final String[] mouseWarpOverrideValues = new String[]{"disable", "enable", "force"};
        container.setMouseWarpOverride(mouseWarpOverrideValues[((Spinner)view.findViewById(R.id.SMouseWarpOverride)).getSelectedItemPosition()]);
    }

    private void saveWineRegistryKeys(View view) {
        File userRegFile = new File(container.getRootDir(), ".wine/user.reg");
        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            WineUtils.setSystemFont(registryEditor, sSystemFont.getSelectedItem().toString());

            registryEditor.setDwordValue("Control Panel\\Desktop", "LogPixels", (int)sbLogPixelsView.getValue());

            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);

            final String[] mouseWarpOverrideValues = new String[]{"disable", "enable", "force"};
            registryEditor.setStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", mouseWarpOverrideValues[sMouseWarpOverride.getSelectedItemPosition()]);

            registryEditor.setStringValue("Software\\Wine\\Direct3D", "shader_backend", "glsl");
            registryEditor.setStringValue("Software\\Wine\\Direct3D", "UseGLSL", "enabled");
        }

        int oldPosition = (byte)sWinVersion.getTag();
        if (oldPosition != -1) {
            int newPosition = sWinVersion.getSelectedItemPosition();
            if (oldPosition != newPosition) WineUtils.setWinVersion(container, newPosition);
        }
    }

    private void createWineConfigurationTab(View view) {
        Context context = getContext();

        WineThemeManager.ThemeInfo desktopTheme = new WineThemeManager.ThemeInfo(isEditMode() ? container.getDesktopTheme() : WineThemeManager.DEFAULT_DESKTOP_THEME);
        RadioGroup rgDesktopTheme = view.findViewById(R.id.RGDesktopTheme);
        rgDesktopTheme.check(desktopTheme.theme == WineThemeManager.Theme.LIGHT ? R.id.RBLight : R.id.RBDark);
        final ImagePickerView ipvDesktopBackgroundImage = view.findViewById(R.id.IPVDesktopBackgroundImage);
        ipvDesktopBackgroundImage.setSelectedSource(desktopTheme.wallpaperId);
        final ColorPickerView cpvDesktopBackgroundColor = view.findViewById(R.id.CPVDesktopBackgroundColor);
        cpvDesktopBackgroundColor.setColor(desktopTheme.backgroundColor);

        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        sDesktopBackgroundType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[position];
                ipvDesktopBackgroundImage.setVisibility(View.GONE);
                cpvDesktopBackgroundColor.setVisibility(View.GONE);

                if (type == WineThemeManager.BackgroundType.IMAGE) {
                    ipvDesktopBackgroundImage.setVisibility(View.VISIBLE);
                }
                else if (type == WineThemeManager.BackgroundType.COLOR) {
                    cpvDesktopBackgroundColor.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        sDesktopBackgroundType.setSelection(desktopTheme.backgroundType.ordinal());

        File containerDir = isEditMode() ? container.getRootDir() : null;
        File userRegFile = new File(containerDir, ".wine/user.reg");

        try (WineRegistryEditor registryEditor = new WineRegistryEditor(userRegFile)) {
            MSLogFont msLogFont = (new MSLogFont()).fromByteArray(registryEditor.getHexValues("Control Panel\\Desktop\\WindowMetrics", "CaptionFont"));
            AppUtils.setSpinnerSelectionFromValue(sSystemFont, msLogFont.getFaceName());

            int logPixels = isEditMode() ? container.getLogPixels() : 96;
            sbLogPixelsView.setValue(registryEditor.getDwordValue("Control Panel\\Desktop", "LogPixels", logPixels));

            List<String> mouseWarpOverrideList = Arrays.asList(context.getString(R.string.disable), context.getString(R.string.enable), context.getString(R.string.force));
            Spinner sMouseWarpOverride = view.findViewById(R.id.SMouseWarpOverride);
            sMouseWarpOverride.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, mouseWarpOverrideList));
            
            String mouseWarpOverride = isEditMode() ? container.getMouseWarpOverride() : "disable";
            AppUtils.setSpinnerSelectionFromValue(sMouseWarpOverride, registryEditor.getStringValue("Software\\Wine\\DirectInput", "MouseWarpOverride", mouseWarpOverride));
        }
    }

    public static String getScreenSize(View view) {
        Spinner sScreenSize = view.findViewById(R.id.SScreenSize);
        String value = sScreenSize.getSelectedItem().toString();
        if (sScreenSize.getSelectedItemPosition() == 0) {
            value = Container.DEFAULT_SCREEN_SIZE;
            String strWidth = ((EditText)view.findViewById(R.id.ETScreenWidth)).getText().toString().trim();
            String strHeight = ((EditText)view.findViewById(R.id.ETScreenHeight)).getText().toString().trim();
            if (strWidth.matches("[0-9]+") && strHeight.matches("[0-9]+")) {
                int width = Integer.parseInt(strWidth);
                int height = Integer.parseInt(strHeight);
                if ((width % 2) == 0 && (height % 2) == 0) return width+"x"+height;
            }
        }
        return StringUtils.parseIdentifier(value);
    }

    private String getDesktopTheme(View view) {
        Spinner sDesktopBackgroundType = view.findViewById(R.id.SDesktopBackgroundType);
        WineThemeManager.BackgroundType type = WineThemeManager.BackgroundType.values()[sDesktopBackgroundType.getSelectedItemPosition()];
        RadioGroup rgDesktopTheme = view.findViewById(R.id.RGDesktopTheme);
        ImagePickerView ipvDesktopBackgroundImage = view.findViewById(R.id.IPVDesktopBackgroundImage);
        ColorPickerView cpvDesktopBackground = view.findViewById(R.id.CPVDesktopBackgroundColor);
        WineThemeManager.Theme theme = rgDesktopTheme.getCheckedRadioButtonId() == R.id.RBLight ? WineThemeManager.Theme.LIGHT : WineThemeManager.Theme.DARK;

       String desktopTheme = theme+","+type+","+cpvDesktopBackground.getColorAsString();
        if (type == WineThemeManager.BackgroundType.IMAGE) {
            String selectedSource = ipvDesktopBackgroundImage.getSelectedSource();
            String wallpaperId = !selectedSource.equals(WineThemeManager.DEFAULT_WALLPAPER_ID) && selectedSource.startsWith("wallpaper-") ? selectedSource : "0";
            File userWallpaperFile = WineThemeManager.getUserWallpaperFile(getContext());
            desktopTheme += ","+(userWallpaperFile.isFile() && selectedSource.equals("user-wallpaper") ? userWallpaperFile.lastModified() : wallpaperId);
        }
        return desktopTheme;
    }

    public static void loadScreenSizeSpinner(View view, String selectedValue) {
        final Spinner sScreenSize = view.findViewById(R.id.SScreenSize);

        final LinearLayout llCustomScreenSize = view.findViewById(R.id.LLCustomScreenSize);
        sScreenSize.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                llCustomScreenSize.setVisibility(sScreenSize.getSelectedItemPosition() == 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        boolean found = AppUtils.setSpinnerSelectionFromIdentifier(sScreenSize, selectedValue);
        if (!found) {
            sScreenSize.setSelection(0);
            String[] screenSize = selectedValue.split("x");
            ((EditText)view.findViewById(R.id.ETScreenWidth)).setText(screenSize[0]);
            ((EditText)view.findViewById(R.id.ETScreenHeight)).setText(screenSize[1]);
        }
    }

    public static String getScreenOrientation(View view) {
        Spinner sScreenOrientation = view.findViewById(R.id.SScreenOrientation);
        String[] orientationValues = new String[]{"landscape", "portrait", "auto"};
        return orientationValues[sScreenOrientation.getSelectedItemPosition()];
    }

    public static void loadScreenOrientationSpinner(View view, String selectedValue) {
        Spinner sScreenOrientation = view.findViewById(R.id.SScreenOrientation);
        String[] orientationValues = new String[]{"landscape", "portrait", "auto"};
        for (int i = 0; i < orientationValues.length; i++) {
            if (orientationValues[i].equals(selectedValue)) {
                sScreenOrientation.setSelection(i);
                return;
            }
        }
        sScreenOrientation.setSelection(0);
    }

    public static boolean isSwapResolution(View view) {
        CheckBox cbSwapResolution = view.findViewById(R.id.CBSwapResolution);
        return cbSwapResolution.isChecked();
    }

    public static String getWinComponents(View view) {
        ViewGroup parent = view.findViewById(R.id.LLTabWinComponents);
        ArrayList<View> views = new ArrayList<>();
        AppUtils.findViewsWithClass(parent, Spinner.class, views);
        String[] wincomponents = new String[views.size()];

        for (int i = 0; i < views.size(); i++) {
            Spinner spinner = (Spinner)views.get(i);
            wincomponents[i] = spinner.getTag()+"="+spinner.getSelectedItemPosition();
        }
        return String.join(",", wincomponents);
    }

    public static void createWinComponentsTab(View view, String wincomponents) {
        Context context = view.getContext();
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup tabView = view.findViewById(R.id.LLTabWinComponents);
        ViewGroup directxSectionView = tabView.findViewById(R.id.LLWinComponentsDirectX);
        ViewGroup generalSectionView = tabView.findViewById(R.id.LLWinComponentsGeneral);

        for (String[] wincomponent : new KeyValueSet(wincomponents)) {
            final String name = wincomponent[0];
            ViewGroup parent = name.startsWith("direct") || name.startsWith("x") ? directxSectionView : generalSectionView;
            View itemView = inflater.inflate(R.layout.wincomponent_list_item, parent, false);
            ((TextView)itemView.findViewById(R.id.TextView)).setText(StringUtils.getString(context, name));
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setSelection(Integer.parseInt(wincomponent[1]), false);
            spinner.setTag(name);
            parent.addView(itemView);
        }
    }

    private String getDrives(View view) {
        LinearLayout parent = view.findViewById(R.id.LLDrives);
        String drives = "";

        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            Spinner spinner = child.findViewById(R.id.Spinner);
            EditText editText = child.findViewById(R.id.EditText);
            String path = editText.getText().toString().replace(":", "").trim();
            if (!path.isEmpty()) drives += spinner.getSelectedItem()+path;
        }
        return drives;
    }

    private void createDrivesTab(View view) {
        final Context context = getContext();

        final LinearLayout parent = view.findViewById(R.id.LLDrives);
        final View emptyTextView = view.findViewById(R.id.TVDrivesEmptyText);
        LayoutInflater inflater = LayoutInflater.from(context);
        final String drives = isEditMode() ? container.getDrives() : Container.DEFAULT_DRIVES;
        final String[] driveLetters = new String[Container.MAX_DRIVE_LETTERS];
        for (int i = 0; i < driveLetters.length; i++) driveLetters[i] = ((char)(i + 68))+":";

        Callback<Drive> addItem = (drive) -> {
            final View itemView = inflater.inflate(R.layout.drive_list_item, parent, false);
            Spinner spinner = itemView.findViewById(R.id.Spinner);
            spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, driveLetters));
            AppUtils.setSpinnerSelectionFromValue(spinner, drive.letter+":");

            final EditText editText = itemView.findViewById(R.id.EditText);
            editText.setText(drive.path);

            itemView.findViewById(R.id.BTSearch).setOnClickListener((v) -> showDriveSearchPopupMenu(v, drive, editText));
            itemView.findViewById(R.id.BTRemove).setOnClickListener((v) -> {
                parent.removeView(itemView);
                if (parent.getChildCount() == 0) emptyTextView.setVisibility(View.VISIBLE);
            });
            parent.addView(itemView);
        };
        for (Drive drive : Container.drivesIterator(drives)) addItem.call(drive);

        view.findViewById(R.id.BTAddDrive).setOnClickListener((v) -> {
            if (parent.getChildCount() >= Container.MAX_DRIVE_LETTERS) return;
            final String nextDriveLetter = String.valueOf(driveLetters[parent.getChildCount()].charAt(0));
            addItem.call(new Drive(nextDriveLetter, ""));
        });

        if (drives.isEmpty()) emptyTextView.setVisibility(View.VISIBLE);
    }

    private void showDriveSearchPopupMenu(View anchorView, final Drive drive, final EditText editText) {
        final FragmentActivity activity = getActivity();
        if (activity == null) return;
        final Fragment $this = ContainerDetailFragment.this;

        PopupMenu popupMenu = new PopupMenu(activity, anchorView);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) popupMenu.setForceShowIcon(true);
        popupMenu.inflate(R.menu.drive_search_popup_menu);
        Menu menu = popupMenu.getMenu();
        SubMenu subMenu = menu.findItem(R.id.menu_item_locations).getSubMenu();
        ArrayList<Container> containers = manager.getContainers();

        ArrayList<String> externalPaths = FileUtils.getExternalStoragePaths(activity);
        for (int i = 0; i < externalPaths.size(); i++) {
            subMenu.add(0, 1001, i, getString(R.string.external_storage) + " " + (i + 1)).setIntent(new Intent().putExtra("path", externalPaths.get(i)));
        }

        popupMenu.setOnMenuItemClickListener((menuItem) -> {
            int itemId = menuItem.getItemId();
            if (itemId == R.id.menu_item_open_directory) {
                openDirectoryCallback = (path) -> {
                    drive.path = path;
                    editText.setText(path);
                };

                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                $this.startActivityForResult(intent, MainActivity.OPEN_DIRECTORY_REQUEST_CODE);
            } else if (itemId == R.id.menu_item_downloads) {
                drive.path = AppUtils.DIRECTORY_DOWNLOADS;
                editText.setText(AppUtils.DIRECTORY_DOWNLOADS);
            } else if (itemId == R.id.menu_item_internal_storage) {
                drive.path = AppUtils.INTERNAL_STORAGE;
                editText.setText(AppUtils.INTERNAL_STORAGE);
            } else if (itemId == 1001) {
                if (menuItem.getIntent() != null) {
                    String externalPath = menuItem.getIntent().getStringExtra("path");
                    drive.path = externalPath;
                    editText.setText(externalPath);
                }
            } else {
                Container container = manager.getContainerById(menuItem.getOrder());
                if (container != null) {
                    String path = container.getRootDir() + "/.wine/drive_c";
                    drive.path = path;
                    editText.setText(path);
                }
            }
            return true;
        });

        popupMenu.show();
    }

    private void loadWineVersionSpinner(final View view, Spinner sWineVersion, final ArrayList<WineInfo> wineInfos, Container container) {
        final Context context = getContext();
        
        if (isEditMode()) {
            sWineVersion.setEnabled(false);
            sWineVersion.setAlpha(0.5f);
        } else {
            sWineVersion.setEnabled(true);
            sWineVersion.setAlpha(1.0f);
        }
        
        view.findViewById(R.id.LLWineVersion).setVisibility(View.VISIBLE);
        
        ArrayList<String> wineVersions = new ArrayList<>();
        for (WineInfo wineInfo : wineInfos) {
            wineVersions.add(wineInfo.identifier());
        }
        
        try {
            ContentsManager contentsManager = new ContentsManager(context);
            contentsManager.syncContents();
            for (ContentProfile profile : contentsManager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_WINE)) {
                String entryName = ContentsManager.getEntryName(profile);
                if (!wineVersions.contains(entryName)) {
                    wineVersions.add(entryName);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        sWineVersion.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, wineVersions));
        
        if (container == null) {
            AppUtils.setSpinnerSelectionFromValue(sWineVersion, WineInfo.WINE_X86_64.identifier());
        } else {
            AppUtils.setSpinnerSelectionFromValue(sWineVersion, container.getWineVersion());
        }
    }

    public static void updateFEXVersionSpinner(Context context, ContentsManager manager, Spinner spinner) {
        List<String> itemList = new ArrayList<>();
        itemList.add("FEX-2512");
        itemList.add("FEX-2601");
        itemList.add("FEX-2603");
        for (ContentProfile profile : manager.getProfiles(ContentProfile.ContentType.CONTENT_TYPE_FEX))
            itemList.add(ContentsManager.getEntryName(profile));
        spinner.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, itemList));
    }
}
