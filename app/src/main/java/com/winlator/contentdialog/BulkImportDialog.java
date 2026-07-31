package com.winlator.contentdialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.R;
import com.winlator.core.GameFolderScanner;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BulkImportDialog extends ContentDialog {
    private final List<GameFolderScanner.Candidate> candidates;
    private final Set<String> selectedExes = new HashSet<>();
    private OnConfirmCallback onConfirmCallback;

    public interface OnConfirmCallback {
        void onConfirm(List<GameFolderScanner.Candidate> selectedCandidates);
    }

    public BulkImportDialog(Context context, List<GameFolderScanner.Candidate> candidates) {
        super(context, R.layout.bulk_import_dialog);
        this.candidates = candidates;
        setTitle("Found Games");

        for (GameFolderScanner.Candidate candidate : candidates) {
            if (!candidate.alreadyAdded) selectedExes.add(candidate.exe.getAbsolutePath());
        }

        RecyclerView recyclerView = findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        recyclerView.setAdapter(new CandidateAdapter());

        setOnConfirmCallback(() -> {
            List<GameFolderScanner.Candidate> selected = new ArrayList<>();
            for (GameFolderScanner.Candidate candidate : candidates) {
                if (selectedExes.contains(candidate.exe.getAbsolutePath())) selected.add(candidate);
            }
            if (onConfirmCallback != null) onConfirmCallback.onConfirm(selected);
        });
    }

    public void setOnConfirmBulkCallback(OnConfirmCallback callback) {
        this.onConfirmCallback = callback;
    }

    private class CandidateAdapter extends RecyclerView.Adapter<CandidateAdapter.ViewHolder> {
        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.bulk_import_item, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            GameFolderScanner.Candidate candidate = candidates.get(position);
            holder.checkBox.setOnCheckedChangeListener(null);
            holder.tvName.setText(candidate.name);
            holder.tvExe.setText(candidate.exe.getName());
            holder.checkBox.setChecked(selectedExes.contains(candidate.exe.getAbsolutePath()));
            holder.checkBox.setEnabled(!candidate.alreadyAdded);
            holder.itemView.setAlpha(candidate.alreadyAdded ? 0.5f : 1.0f);

            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedExes.add(candidate.exe.getAbsolutePath());
                else selectedExes.remove(candidate.exe.getAbsolutePath());
            });

            holder.btChangeExe.setOnClickListener(v -> {
                PopupMenu popupMenu = new PopupMenu(getContext(), v);
                popupMenu.getMenu().add(candidate.exe.getName());
                for (File alt : candidate.alternatives) popupMenu.getMenu().add(alt.getName());

                popupMenu.setOnMenuItemClickListener(item -> {
                    String selectedName = item.getTitle().toString();
                    if (selectedName.equals(candidate.exe.getName())) return true;

                    File newExe = null;
                    for (File alt : candidate.alternatives) {
                        if (alt.getName().equals(selectedName)) {
                            newExe = alt;
                            break;
                        }
                    }

                    if (newExe != null) {
                        boolean wasSelected = selectedExes.remove(candidate.exe.getAbsolutePath());
                        candidate.exe = newExe;
                        if (wasSelected) selectedExes.add(candidate.exe.getAbsolutePath());
                        notifyItemChanged(position);
                    }
                    return true;
                });
                popupMenu.show();
            });
        }

        @Override
        public int getItemCount() {
            return candidates.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            CheckBox checkBox;
            TextView tvName;
            TextView tvExe;
            ImageButton btChangeExe;

            ViewHolder(View view) {
                super(view);
                checkBox = view.findViewById(R.id.CheckBox);
                tvName = view.findViewById(R.id.TVName);
                tvExe = view.findViewById(R.id.TVExe);
                btChangeExe = view.findViewById(R.id.BTChangeExe);
            }
        }
    }
}
