package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.Container;
import com.winlator.container.Shortcut;
import com.winlator.contentdialog.BulkImportDialog;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.ShortcutSettingsDialog;
import com.winlator.core.AppUtils;
import com.winlator.core.ArrayUtils;
import com.winlator.core.FileUtils;
import com.winlator.core.GameFolderScanner;
import com.winlator.core.StringUtils;
import com.winlator.core.WineUtils;
import com.winlator.win32.PEParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class ShortcutsFragment extends BaseFileManagerFragment<Shortcut> {
    private Container selectedContainerForShortcut;
    private final HashSet<Shortcut> selectedShortcuts = new HashSet<>();
    private View selectionOptionsContainer;
    private EditText etFilter;
    private View llFilter;
    private String filterText = "";
    private List<Shortcut> displayedShortcuts = new ArrayList<>();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewStyle = ViewStyle.valueOf(preferences.getString("shortcuts_view_style", "GRID"));
        filterText = preferences.getString("shortcuts_filter_text", "").toLowerCase(Locale.ENGLISH);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        selectionOptionsContainer = view.findViewById(R.id.LLSelectionOptions);
        llFilter = view.findViewById(R.id.LLFilter);
        
        etFilter = view.findViewById(R.id.ETFilter);
        final View btClearFilter = view.findViewById(R.id.remove_button);
        if (etFilter != null) {
            String savedFilterText = preferences.getString("shortcuts_filter_text", "");
            etFilter.setText(savedFilterText);

            if (btClearFilter != null) {
                btClearFilter.setVisibility(!savedFilterText.isEmpty() ? View.VISIBLE : View.GONE);
                btClearFilter.setOnClickListener((v) -> etFilter.setText(""));
            }

            etFilter.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterText = s.toString().toLowerCase(Locale.ENGLISH);
                    if (btClearFilter != null) btClearFilter.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                    preferences.edit().putString("shortcuts_filter_text", s.toString()).apply();
                    refreshContent();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        if (llFilter != null) {
            boolean filterVisible = preferences.getBoolean("shortcuts_filter_visible", false);
            llFilter.setVisibility(filterVisible ? View.VISIBLE : View.GONE);
        }

        view.findViewById(R.id.BTSelectAll).setOnClickListener((v) -> {
            if (selectedShortcuts.containsAll(displayedShortcuts)) {
                selectedShortcuts.removeAll(displayedShortcuts);
            }
            else {
                selectedShortcuts.addAll(displayedShortcuts);
            }
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter != null) adapter.notifyDataSetChanged();
            if (selectionOptionsContainer != null) selectionOptionsContainer.setVisibility(selectedShortcuts.isEmpty() ? View.GONE : View.VISIBLE);
        });

        view.findViewById(R.id.BTCancelSelection).setOnClickListener((v) -> {
            selectedShortcuts.clear();
            selectionOptionsContainer.setVisibility(View.GONE);
            RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
            if (adapter != null) adapter.notifyDataSetChanged();
        });

        view.findViewById(R.id.BTRemoveSelected).setOnClickListener((v) -> {
            ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_file, () -> {
                for (Shortcut shortcut : selectedShortcuts) shortcut.remove();
                selectedShortcuts.clear();
                selectionOptionsContainer.setVisibility(View.GONE);
                refreshContent();
            });
        });

        view.findViewById(R.id.BTExportAll).setOnClickListener((View v)->{
            if (selectedShortcuts.isEmpty()) return;
            Context context = getContext();
            File exportDir = new File(AppUtils.INTERNAL_STORAGE, "Winlator/Shortcuts");
            if (!exportDir.exists()) exportDir.mkdirs();

            for (Shortcut shortcut : new ArrayList<>(selectedShortcuts)) {
                if (shortcut.file.isDirectory()) continue;
                File outFile = new File(exportDir, shortcut.name + ".json");
                manager.exportShortcutConfigAsync(shortcut, outFile, () -> {});
            }

            AppUtils.showToast(context, "Shortcuts exported to Winlator/Shortcuts");
            selectedShortcuts.clear();
            selectionOptionsContainer.setVisibility(View.GONE);
            refreshContent();
        });
    }

    @Override
    public void refreshContent() {
        super.refreshContent();
        if (selectionOptionsContainer != null) selectionOptionsContainer.setVisibility(selectedShortcuts.isEmpty() ? View.GONE : View.VISIBLE);

        Shortcut selectedFolder = !folderStack.isEmpty() ? folderStack.peek() : null;
        ArrayList<Shortcut> shortcuts = manager.loadShortcuts(selectedFolder);

        if (!filterText.isEmpty()) {
            ArrayList<Shortcut> filteredShortcuts = new ArrayList<>();
            for (Shortcut shortcut : shortcuts) {
                if (shortcut.name.toLowerCase(Locale.ENGLISH).contains(filterText)) {
                    filteredShortcuts.add(shortcut);
                }
            }
            shortcuts = filteredShortcuts;
        }

        displayedShortcuts = shortcuts;
        recyclerView.setAdapter(new ShortcutsAdapter(shortcuts));
        emptyTextView.setVisibility(shortcuts.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onOptionsMenuClicked() {
        if (!folderStack.isEmpty()) selectedShortcuts.clear();
        return super.onOptionsMenuClicked();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.shortcuts_menu, menu);
        refreshViewStyleMenuItem(menu.findItem(R.id.menu_item_view_style));
    }

    @Override
    protected void pasteFiles() {
        if (folderStack.isEmpty()) {
            clearClipboard();
            AppUtils.showToast(getContext(), R.string.you_cannot_paste_files_here);
            return;
        }

        clipboard.targetDir = folderStack.peek().file;
        super.pasteFiles();
    }

    private void instantiateClipboard(Shortcut shortcut, boolean cutMode) {
        clearClipboard();
        File linkFile = shortcut.getLinkFile();
        File shortcutFile = new File(shortcut.file.getParentFile(), shortcut.file.getName());
        File[] files = {shortcutFile};
        if (shortcut.file.isFile()) files = ArrayUtils.concat(files, new File[]{new File(linkFile.getParentFile(), linkFile.getName())});

        clipboard = new Clipboard(files, cutMode);
        pasteButton.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        int itemId = menuItem.getItemId();
        if (itemId == R.id.menu_item_view_style) {
            setViewStyle(viewStyle == ViewStyle.GRID ? ViewStyle.LIST : ViewStyle.GRID);
            preferences.edit().putString("shortcuts_view_style", viewStyle.name()).apply();
            refreshViewStyleMenuItem(menuItem);
            return true;
        }
        else if (itemId == R.id.menu_item_create_shortcut) {
            createShortcutFromStorage();
            return true;
        }
        else if (itemId == R.id.menu_item_filter_shortcuts) {
            showFilterShortcuts();
            return true;
        }
        else if (itemId == R.id.menu_item_import) {
            final Shortcut selectedFolder = !folderStack.isEmpty() ? folderStack.peek() : null;
            if (selectedFolder == null) {
                AppUtils.showToast(getContext(), "Please enter a container folder to import shortcuts");
                return true;
            }

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/json");
            MainActivity activity = (MainActivity)getActivity();
            if (activity != null) {
                activity.setOpenFileCallback((uri) -> {
                    if (uri != null) {
                        File cacheDir = getContext().getCacheDir();
                        if (cacheDir != null) {
                            File tempFile = new File(cacheDir, "import_shortcut.json");
                            try (ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "r");
                                 FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                                byte[] buffer = new byte[4096];
                                int len;
                                while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
                                fos.close();

                                manager.importShortcutConfigAsync(selectedFolder, tempFile, () -> {
                                    refreshContent();
                                    AppUtils.showToast(getContext(), "Imported successfully");
                                });
                            }
                            catch (IOException e) {}
                        }
                    }
                });
                activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
            }
            return true;
        }
        else return super.onOptionsItemSelected(menuItem);
    }

    private void createShortcutFromStorage() {
        final ArrayList<Container> containers = manager.getContainers();
        if (containers.isEmpty()) {
            AppUtils.showToast(getContext(), "Please create a container first.");
            return;
        }
        final Context context = getContext();

        final ContentDialog dialog = new ContentDialog(context, R.layout.create_shortcut_dialog);
        dialog.setTitle(R.string.create_shortcut);

        final Spinner sContainer = dialog.findViewById(R.id.SContainer);
        final Spinner sImportMethod = dialog.findViewById(R.id.SImportMethod);

        ArrayList<String> containerNames = new ArrayList<>();
        for (Container container : containers) containerNames.add(container.getName());
        sContainer.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, containerNames));

        String[] importMethods = {"Single EXE", "Scan Folder for Games"};
        sImportMethod.setAdapter(new ArrayAdapter<>(context, android.R.layout.simple_spinner_dropdown_item, importMethods));

        dialog.setOnConfirmCallback(() -> {
            int position = sContainer.getSelectedItemPosition();
            if (position >= 0) {
                selectedContainerForShortcut = containers.get(position);
                int method = sImportMethod.getSelectedItemPosition();

                if (method == 0) { // Single EXE
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                    MainActivity activity = (MainActivity)getActivity();
                    if (activity != null) {
                        activity.setOpenFileCallback((uri) -> {
                            if (uri != null) processSelectedExe(selectedContainerForShortcut, uri);
                        });
                        activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
                    }
                } else { // Scan Folder
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    MainActivity activity = (MainActivity)getActivity();
                    if (activity != null) {
                        activity.setOpenFileCallback((uri) -> {
                            if (uri != null) scanFolderForGames(selectedContainerForShortcut, uri);
                        });
                        activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
                    }
                }
            }
        });
        dialog.show();
    }

    private void scanFolderForGames(Container container, Uri uri) {
        String path = FileUtils.getFilePathFromUri(uri);
        if (path == null) path = uri.getPath();
        if (path == null || path.isEmpty()) return;

        File root = new File(path);
        HashSet<String> existingExes = new HashSet<>();
        ArrayList<Shortcut> existingShortcuts = manager.loadShortcuts(null);
        for (Shortcut s : existingShortcuts) {
            if (s.container.id == container.id) {
                try {
                    existingExes.add(s.file.getCanonicalPath());
                } catch (IOException ignored) {}
            }
        }

        List<GameFolderScanner.Candidate> candidates = GameFolderScanner.scan(root, existingExes);
        if (candidates.isEmpty()) {
            AppUtils.showToast(getContext(), "No games found in folder.");
            return;
        }

        BulkImportDialog bulkDialog = new BulkImportDialog(getContext(), candidates);
        bulkDialog.setOnConfirmBulkCallback(selectedCandidates -> {
            for (GameFolderScanner.Candidate c : selectedCandidates) {
                createShortcutForCandidate(container, c);
            }
            refreshContent();
            AppUtils.showToast(getContext(), selectedCandidates.size() + " shortcuts created.");
        });
        bulkDialog.show();
    }

    private String saveShortcutIcon(Container container, File exeFile, String shortcutName) {
        File gameFolder = exeFile.getParentFile();
        if (gameFolder == null) return "";

        Bitmap icon = null;
        File[] icoFiles = gameFolder.listFiles((dir, fileName)-> {
            String name = fileName.toLowerCase();
            return name.endsWith(".ico") || name.endsWith(".png") ||  name.endsWith(".jpg") || name.endsWith(".bmp");
        });

        if(icoFiles != null){

            for (File item : icoFiles) {
                String name = item.getName();
                if(name.toLowerCase().endsWith(".ico")){
                    icon = com.winlator.win32.MSIcon.decodeFile(item);
                    break;
                }

                if(name.toLowerCase().endsWith(".bmp")){
                    icon = com.winlator.win32.MSBitmap.decodeFile(item);
                    break;
                }

                if(name.toLowerCase().endsWith(".png") || name.toLowerCase().endsWith(".jpg")){
                    icon = BitmapFactory.decodeFile(item.getPath());
                    break;
                }
            }
        }


        if (icon == null) {
            icon = PEParser.extractIcon(exeFile);
        }

        if (icon != null) {
            String iconName = StringUtils.clearReservedChars(shortcutName).toLowerCase(Locale.ENGLISH);
            File iconDir = container.getIconsDir(48);
            if (!iconDir.exists()) iconDir.mkdirs();
            File iconFile = new File(iconDir, iconName + ".png");
            try (FileOutputStream out = new FileOutputStream(iconFile)) {
                icon.compress(Bitmap.CompressFormat.PNG, 100, out);
                return iconName;
            } catch (IOException e) {
                return "";
            }
        }
        return "";
    }

    private void createShortcutForCandidate(Container container, GameFolderScanner.Candidate candidate) {
        File gameFolder = candidate.exe.getParentFile();
        if (gameFolder == null) return;

        File libraryFolder = gameFolder.getParentFile();
        File driveFolder = gameFolder;

        if (libraryFolder != null && !libraryFolder.getAbsolutePath().equals(AppUtils.INTERNAL_STORAGE)) {
            driveFolder = libraryFolder;
        }

        String driveFolderPath = StringUtils.removeEndSlash(driveFolder.getAbsolutePath());
        if (!container.hasDrive(driveFolderPath)) {
            container.addDrive(driveFolderPath);
            container.saveData();
        }

        String dosPath = WineUtils.unixToDOSPath(candidate.exe.getAbsolutePath(), container);
        if (!dosPath.contains(":")) return;

        String iconName = saveShortcutIcon(container, candidate.exe, candidate.name);

        JSONObject data = new JSONObject();
        try {
            data.put("name", candidate.name);
            data.put("path", dosPath);
            if (!iconName.isEmpty()) data.put("icon", iconName);

            if (candidate.appId != null) {
                JSONObject extraData = new JSONObject();
                extraData.put("steamAppId", String.valueOf(candidate.appId));
                data.put("extraData", extraData);
            }

            Shortcut selectedFolder = !folderStack.isEmpty() ? folderStack.peek() : null;
            File destinationDir = selectedFolder != null ? selectedFolder.file : new File(container.getUserDir(), "Desktop");
            manager.createShortcut(container, data, destinationDir);
        } catch (JSONException ignored) {}
    }

    private void showFilterShortcuts(){
        if (llFilter != null && etFilter != null) {
            if (llFilter.getVisibility() == View.VISIBLE) {
                llFilter.setVisibility(View.GONE);
                etFilter.setText("");
                filterText = "";
                preferences.edit().putBoolean("shortcuts_filter_visible", false).apply();
                refreshContent();
            }
            else {
                llFilter.setVisibility(View.VISIBLE);
                etFilter.requestFocus();
                preferences.edit().putBoolean("shortcuts_filter_visible", true).apply();
                AppUtils.showKeyboard((AppCompatActivity)getActivity());
            }
        }
    }

    private void processSelectedExe(Container container, Uri uri) {
        Activity activity = getActivity();
        if (activity == null || container == null) return;

        String path = FileUtils.getFilePathFromUri(uri);
        if (path == null) path = uri.getPath(); // Fallback to URI path
        
        if (path == null || path.isEmpty()) {
            AppUtils.showToast(activity, "Unable to resolve file path.");
            return;
        }

        File exeFile = new File(path);
        Log.d("ShortcutsFragment", "Processing selected EXE: " + exeFile.getAbsolutePath());
        if (!exeFile.getName().toLowerCase().endsWith(".exe")) {
            AppUtils.showToast(activity, "Please select an executable file (.exe)");
            return;
        }

        File gameFolder = exeFile.getParentFile();
        if (gameFolder == null) {
            AppUtils.showToast(activity, "Unable to determine game folder.");
            return;
        }

        File libraryFolder = gameFolder.getParentFile();
        File driveFolder = gameFolder;

        if (libraryFolder != null && !libraryFolder.getAbsolutePath().equals(AppUtils.INTERNAL_STORAGE)) {
            driveFolder = libraryFolder;
        }

        String driveFolderPath = StringUtils.removeEndSlash(driveFolder.getAbsolutePath());

        // Add drive folder as disk if not already present
        if (!container.hasDrive(driveFolderPath)) {
            container.addDrive(driveFolderPath);
            container.saveData();
            AppUtils.showToast(activity, activity.getString(R.string.game_folder_added_as_disk));
        }

        // Create shortcut
        String name = gameFolder.getName();
        String dosPath = WineUtils.unixToDOSPath(exeFile.getAbsolutePath(), container);

        if (!dosPath.contains(":")) {
             AppUtils.showToast(activity, "Error: Could not map to DOS path.");
             return;
        }

        String iconName = saveShortcutIcon(container, exeFile, name);

        JSONObject data = new JSONObject();
        try {
            data.put("name", name);
            data.put("path", dosPath);
            if (!iconName.isEmpty()) data.put("icon", iconName);
            
            Shortcut selectedFolder = !folderStack.isEmpty() ? folderStack.peek() : null;
            File destinationDir = selectedFolder != null ? selectedFolder.file : new File(container.getUserDir(), "Desktop");
            manager.createShortcut(container, data, destinationDir);
            
            File shortcutFile = new File(destinationDir, name + ".desktop");
            if (shortcutFile.exists()) {
                activity.runOnUiThread(() -> {
                    refreshContent();
                    AppUtils.showToast(activity, activity.getString(R.string.shortcut_created_successfully));
                });
            } else {
                activity.runOnUiThread(() -> AppUtils.showToast(activity, "Error: Failed to create shortcut file."));
            }
        } catch (JSONException e) {
            AppUtils.showToast(activity, "Error creating shortcut data.");
        }
    }

    @Override
    protected String getHomeTitle() {
        return getString(R.string.shortcuts);
    }

    private class ShortcutsAdapter extends RecyclerView.Adapter<ShortcutsAdapter.ViewHolder> {
        private final List<Shortcut> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView runButton;
            private final ImageView menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView subtitle;
            private final LinearLayout SelectItem;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.subtitle = view.findViewById(R.id.TVSubtitle);
                this.runButton = view.findViewById(R.id.BTRun);
                this.menuButton = view.findViewById(R.id.BTMenu);
                this.SelectItem = view.findViewById(R.id.item_select);
            }
        }

        public ShortcutsAdapter(List<Shortcut> data) {
            this.data = data;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int resource = viewStyle == ViewStyle.LIST ? R.layout.file_list_item : R.layout.file_grid_item;
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(resource, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            final Shortcut item = data.get(position);

            if (item.icon == null) {
                int iconResId = item.file.isDirectory() ? R.drawable.container_folder : R.drawable.container_file_link;
                holder.imageView.setImageResource(iconResId);
            }
            else holder.imageView.setImageBitmap(item.icon);

            holder.title.setText(item.name);
            holder.subtitle.setText(item.container.getName());

            if (item.file.isDirectory()) {
                holder.runButton.setImageResource(R.drawable.icon_open);
            }
            else holder.runButton.setImageResource(R.drawable.icon_run);

            holder.imageView.setOnClickListener((v) -> runFromShortcut(item));
            holder.runButton.setOnClickListener((v) -> runFromShortcut(item));
            holder.menuButton.setOnClickListener((v) -> showListItemMenu(v, item));

            if (holder.SelectItem != null) {
                holder.SelectItem.setSelected(selectedShortcuts.contains(item));
                holder.SelectItem.setOnClickListener((v) -> selectShortcut(item));
            }
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, final Shortcut shortcut) {
            MainActivity activity = (MainActivity)getActivity();
            if (activity == null) return;
            final Context context = getContext();
            PopupMenu listItemMenu = new PopupMenu(context, anchorView);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) listItemMenu.setForceShowIcon(true);

            listItemMenu.inflate(R.menu.shortcut_popup_menu);
            if(shortcut.file.isDirectory()) {
                Menu menu = listItemMenu.getMenu();
                menu.findItem(R.id.menu_item_settings).setVisible(false);
                menu.findItem(R.id.menu_item_export).setVisible(false);
                menu.findItem(R.id.menu_item_import).setVisible(false);
            }
            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.menu_item_settings) {
                    clearClipboard();
                    (new ShortcutSettingsDialog(ShortcutsFragment.this, shortcut)).show();
                } else if (itemId == R.id.menu_item_copy || itemId == R.id.menu_item_cut) {
                    instantiateClipboard(shortcut, itemId == R.id.menu_item_cut);
                } else if (itemId == R.id.menu_item_remove) {
                    clearClipboard();
                    ContentDialog.confirm(context, R.string.do_you_want_to_remove_this_file, () -> {
                        shortcut.remove();
                        refreshContent();
                    });
                }else if(itemId == R.id.menu_item_export){
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, shortcut.name + ".json");
                    activity.setCreateFileCallback((uri) -> {
                        if (uri != null) {
                            File cacheDir = context.getCacheDir();
                            if (cacheDir != null) {
                                File tempFile = new File(cacheDir, "export.json");
                                manager.exportShortcutConfigAsync(shortcut, tempFile, () -> {
                                    try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "w");
                                         FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                                         FileInputStream fis = new FileInputStream(tempFile)) {
                                        byte[] buffer = new byte[4096];
                                        int len;
                                        while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
                                        AppUtils.showToast(context, "Exported successfully");
                                    } catch (IOException e) {
                                    }
                                });
                            }
                        }
                    });
                    activity.startActivityForResult(intent, MainActivity.CREATE_FILE_REQUEST_CODE);
                }else if(itemId == R.id.menu_item_import){
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("application/json");
                    intent.putExtra(Intent.EXTRA_TITLE, shortcut.name + ".json");
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

                                    try {
                                        JSONObject data = new JSONObject(FileUtils.readString(tempFile));

                                        Iterator<String> keys = data.keys();
                                        while (keys.hasNext()) {
                                            String key = keys.next();
                                            if(key.equals("extraData")) continue;
                                            shortcut.putExtra(key, data.getString(key));
                                        }

                                        JSONObject extraData = data.optJSONObject("extraData");
                                        if(extraData != null) {
                                            keys = extraData.keys();
                                            while (keys.hasNext()) {
                                                String key = keys.next();
                                                shortcut.putExtra(key, extraData.getString(key));
                                            }
                                            shortcut.saveData();
                                            AppUtils.showToast(getContext(), "Imported successfully");
                                        }
                                    }
                                    catch (JSONException e) {
                                        AppUtils.showToast(getContext(), "Error Importing File Malformed");}
                                }
                                catch (IOException e) {
                                    AppUtils.showToast(getContext(), "Error While Opening File");
                                }
                            }
                        }
                    });
                    activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
                }
                return true;
            });
            listItemMenu.show();
        }
        private void selectShortcut(final Shortcut shortcut){
            if (selectedShortcuts.contains(shortcut)) {
                selectedShortcuts.remove(shortcut);
            }
            else {
                selectedShortcuts.add(shortcut);
            }
            notifyDataSetChanged();
            if (selectionOptionsContainer != null) selectionOptionsContainer.setVisibility(selectedShortcuts.isEmpty() ? View.GONE : View.VISIBLE);
        }
        private void runFromShortcut(Shortcut shortcut) {
            AppCompatActivity activity = (AppCompatActivity)getActivity();
            if (activity == null) return;

            if (shortcut.file.isDirectory()) {
                selectedShortcuts.clear();
                folderStack.push(shortcut);
                refreshContent();

                ActionBar actionBar = activity.getSupportActionBar();
                if (actionBar != null) {
                    actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);
                    actionBar.setTitle(shortcut.name);
                }
            }
            else {
                Intent intent = new Intent(activity, XServerDisplayActivity.class);
                intent.putExtra("container_id", shortcut.container.id);
                intent.putExtra("shortcut_path", shortcut.file.getPath());
                activity.startActivity(intent);
            }
        }
    }
}
