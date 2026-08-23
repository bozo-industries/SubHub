package com.betasafe.app.capture;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.betasafe.app.R;
import com.betasafe.app.databinding.ActivityCustomImagesBinding;
import com.betasafe.app.security.ControllerEditMode;
import com.betasafe.app.security.ControllerPinManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Adds, enables, previews, and deletes private custom censor images. */
public final class CustomImagesActivity extends AppCompatActivity {
    private ActivityCustomImagesBinding binding;
    private CustomImageManager manager;
    private ExecutorService executor;
    private final List<Bitmap> thumbnails = new ArrayList<>();
    private ActivityResultLauncher<String[]> picker;
    private ControllerEditMode editMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityCustomImagesBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        manager = new CustomImageManager(this);
        executor = Executors.newSingleThreadExecutor();
        picker = registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(), this::importImages);
        binding.buttonBack.setOnClickListener(view -> finish());
        binding.buttonAdd.setOnClickListener(view -> picker.launch(new String[]{"image/*"}));
        editMode = ControllerEditMode.bind(
                this, binding.buttonEditLock, editing -> applyEditState());
        rebuild();
    }

    @Override protected void onResume() {
        super.onResume();
        if (editMode != null) editMode.refresh();
    }

    private void applyEditState() {
        if (binding == null) return;
        binding.buttonAdd.setEnabled(ControllerPinManager.isSessionUnlocked());
        rebuild();
    }

    private void importImages(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        binding.buttonAdd.setEnabled(false);
        binding.status.setText(R.string.custom_images_importing);
        executor.execute(() -> {
            int added = manager.addImages(uris);
            runOnUiThread(() -> {
                if (binding == null) return;
                binding.buttonAdd.setEnabled(ControllerPinManager.isSessionUnlocked());
                binding.status.setText(getString(R.string.custom_images_added, added));
                rebuild();
            });
        });
    }

    private void rebuild() {
        releaseThumbnails();
        binding.imageList.removeAllViews();
        List<CustomImageManager.Entry> entries = manager.listEntries();
        if (entries.isEmpty()) {
            TextView empty = text(getString(R.string.custom_images_empty));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(24), 0, dp(24));
            binding.imageList.addView(empty);
            return;
        }
        for (CustomImageManager.Entry entry : entries) {
            binding.imageList.addView(row(entry));
        }
    }

    private LinearLayout row(CustomImageManager.Entry entry) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(12), dp(12));
        row.setBackgroundResource(R.drawable.bg_card);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = dp(10);
        row.setLayoutParams(rowParams);

        ImageView preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap bitmap = manager.thumbnail(entry.getId(), 256);
        if (bitmap != null) {
            thumbnails.add(bitmap);
            preview.setImageBitmap(bitmap);
        }
        row.addView(preview, new LinearLayout.LayoutParams(dp(82), dp(82)));

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(12), 0, 0, 0);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(controls, controlsParams);

        SwitchMaterial enabled = new SwitchMaterial(this);
        enabled.setText(R.string.custom_images_enabled);
        enabled.setTextColor(getColor(R.color.text_primary));
        enabled.setChecked(entry.isEnabled());
        enabled.setEnabled(ControllerPinManager.isSessionUnlocked());
        enabled.setOnCheckedChangeListener((button, checked) ->
                manager.setEnabled(entry.getId(), checked));
        controls.addView(enabled, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        Button delete = new Button(this);
        delete.setText(R.string.delete);
        delete.setTextColor(getColor(R.color.accent));
        delete.setTextSize(12);
        delete.setAllCaps(false);
        delete.setBackgroundResource(R.drawable.bg_outline_button);
        delete.setEnabled(ControllerPinManager.isSessionUnlocked());
        delete.setOnClickListener(view -> {
            manager.delete(entry.getId());
            rebuild();
        });
        controls.addView(delete, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        return row;
    }

    private TextView text(String value) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(getColor(R.color.text_secondary));
        view.setTextSize(14);
        return view;
    }

    private void releaseThumbnails() {
        for (Bitmap bitmap : thumbnails) if (!bitmap.isRecycled()) bitmap.recycle();
        thumbnails.clear();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        releaseThumbnails();
        if (executor != null) executor.shutdownNow();
        binding = null;
        super.onDestroy();
    }
}
