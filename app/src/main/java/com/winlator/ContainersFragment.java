package com.winlator;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.contentdialog.ContentDialog;
import com.winlator.contentdialog.StorageInfoDialog;
import com.winlator.core.PreloaderDialog;
import com.winlator.core.AppUtils;
import com.winlator.xenvironment.RootFS;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ContainersFragment extends Fragment {
    private RecyclerView recyclerView;
    private TextView emptyTextView;
    private ContainerManager manager;
    private PreloaderDialog preloaderDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        preloaderDialog = new PreloaderDialog(getActivity());
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        manager = new ContainerManager(getContext());
        loadContainersList();
        ((AppCompatActivity)getActivity()).getSupportActionBar().setTitle(R.string.containers);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        FrameLayout frameLayout = (FrameLayout)inflater.inflate(R.layout.containers_fragment, container, false);
        recyclerView = frameLayout.findViewById(R.id.RecyclerView);
        Context context = recyclerView.getContext();
        emptyTextView = frameLayout.findViewById(R.id.TVEmptyText);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));

        DividerItemDecoration itemDecoration = new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(ContextCompat.getDrawable(context, R.drawable.list_item_divider));
        recyclerView.addItemDecoration(itemDecoration);
        return frameLayout;
    }

    private void loadContainersList() {
        ArrayList<Container> containers = manager.getContainers();
        recyclerView.setAdapter(new ContainersAdapter(containers));
        emptyTextView.setVisibility(containers.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.containers_menu, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == R.id.menu_item_add) {
            if (!RootFS.find(getContext()).isValid()) return false;
            FragmentManager fragmentManager = getParentFragmentManager();
            fragmentManager.beginTransaction()
                .addToBackStack(null)
                .replace(R.id.FLFragmentContainer, new ContainerDetailFragment())
                .commit();
            return true;
        }
        else return super.onOptionsItemSelected(menuItem);
    }

    private class ContainersAdapter extends RecyclerView.Adapter<ContainersAdapter.ViewHolder> {
        private final List<Container> data;

        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageView runButton;
            private final ImageView menuButton;
            private final ImageView imageView;
            private final TextView title;
            private final TextView wineVersion;

            private ViewHolder(View view) {
                super(view);
                this.imageView = view.findViewById(R.id.ImageView);
                this.title = view.findViewById(R.id.TVTitle);
                this.wineVersion = view.findViewById(R.id.TVWineVersion);
                this.runButton = view.findViewById(R.id.BTRun);
                this.menuButton = view.findViewById(R.id.BTMenu);
            }
        }

        public ContainersAdapter(List<Container> data) {
            this.data = data;
        }

        @Override
        public final ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.container_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            final Container item = data.get(position);
            holder.imageView.setImageResource(R.drawable.icon_container);
            holder.title.setText(item.getName());
            holder.wineVersion.setText(item.getWineVersion());
            holder.runButton.setOnClickListener((view) -> runContainer(item));
            holder.menuButton.setOnClickListener((view) -> showListItemMenu(view, item));
        }

        @Override
        public final int getItemCount() {
            return data.size();
        }

        private void showListItemMenu(View anchorView, Container container) {
            MainActivity activity = (MainActivity)getActivity();
            PopupMenu listItemMenu = new PopupMenu(activity, anchorView);
            listItemMenu.inflate(R.menu.container_popup_menu);

            listItemMenu.setOnMenuItemClickListener((menuItem) -> {
                int itemId = menuItem.getItemId();
                if (itemId == R.id.menu_item_file_manager) {
                    activity.showFragment(new ContainerFileManagerFragment(container.id));
                } else if (itemId == R.id.menu_item_edit) {
                    activity.showFragment(new ContainerDetailFragment(container.id));
                } else if (itemId == R.id.menu_item_duplicate) {
                    ContentDialog.confirm(getContext(), R.string.do_you_want_to_duplicate_this_container, () -> {
                        preloaderDialog.show(R.string.duplicating_container);
                        manager.duplicateContainerAsync(container, () -> {
                            preloaderDialog.close();
                            loadContainersList();
                        });
                    });
                } else if (itemId == R.id.menu_item_remove) {
                    ContentDialog.confirm(getContext(), R.string.do_you_want_to_remove_this_container, () -> {
                        preloaderDialog.show(R.string.removing_container);
                        manager.removeContainerAsync(container, () -> {
                            preloaderDialog.close();
                            loadContainersList();
                        });
                    });
                } else if (itemId == R.id.menu_item_export) {
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
                                    manager.exportConfigAsync(container, tempFile, () -> {
                                        try (ParcelFileDescriptor pfd = getContext().getContentResolver().openFileDescriptor(uri, "w");
                                             FileOutputStream fos = new FileOutputStream(pfd.getFileDescriptor());
                                             FileInputStream fis = new FileInputStream(tempFile)) {
                                            byte[] buffer = new byte[4096];
                                            int len;
                                            while ((len = fis.read(buffer)) > 0) fos.write(buffer, 0, len);
                                            AppUtils.showToast(getContext(), "Exported successfully");
                                        }
                                        catch (IOException e) {}
                                    });
                                }
                            }
                        });
                        activity.startActivityForResult(intent, MainActivity.CREATE_FILE_REQUEST_CODE);
                    }
                } else if (itemId == R.id.menu_item_import) {
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

                                        preloaderDialog.show(R.string.updating_system_files);
                                        manager.importConfigAsync(container, tempFile, () -> {
                                            preloaderDialog.close();
                                            loadContainersList();
                                            AppUtils.showToast(getContext(), "Imported successfully");
                                        });
                                    }
                                    catch (IOException e) {}
                                }
                            }
                        });
                        activity.startActivityForResult(intent, MainActivity.OPEN_FILE_REQUEST_CODE);
                    }
                } else if(itemId == R.id.menu_item_update_patch){
                    container.putExtra("needPatchUpdate", "t");
                    container.saveData();
                }
                else if (itemId == R.id.menu_item_info) {
                    (new StorageInfoDialog(activity, container)).show();
                }
                return true;
            });
            listItemMenu.show();
        }

        private void runContainer(Container container) {
            Activity activity = getActivity();
            Intent intent = new Intent(activity, XServerDisplayActivity.class);
            intent.putExtra("container_id", container.id);
            activity.startActivity(intent);
        }
    }
}
