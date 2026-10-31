package com.winlator;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.winlator.core.AppUtils;
import com.winlator.core.LocaleHelper;
import com.winlator.inputcontrols.Binding;
import com.winlator.inputcontrols.ControlsProfile;
import com.winlator.inputcontrols.ExternalController;
import com.winlator.inputcontrols.ExternalControllerBinding;
import com.winlator.inputcontrols.GamepadState;
import com.winlator.inputcontrols.InputControlsManager;
import com.winlator.math.Mathf;
import com.winlator.xserver.Keyboard;
import com.winlator.xserver.XKeycode;

public class ExternalControllerBindingsActivity extends AppCompatActivity {
    private TextView emptyTextView;
    private ControlsProfile profile;
    private ExternalController controller;
    private RecyclerView recyclerView;
    private ControllerBindingsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppUtils.setActivityTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.external_controller_bindings_activity);

        Intent intent = getIntent();
        int profileId = intent.getIntExtra("profile_id", 0);
        profile = InputControlsManager.loadProfile(this, ControlsProfile.getProfileFile(this, profileId));
        String controllerId = intent.getStringExtra("controller_id");

        controller = profile.getController(controllerId);
        if (controller == null) {
            controller = profile.addController(controllerId);
            profile.save();
        }

        Toolbar toolbar = findViewById(R.id.Toolbar);
        toolbar.setTitle(controller.getName());
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setHomeAsUpIndicator(R.drawable.icon_action_bar_back);

        emptyTextView = findViewById(R.id.TVEmptyText);
        recyclerView = findViewById(R.id.RecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        DividerItemDecoration itemDecoration = new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        itemDecoration.setDrawable(ContextCompat.getDrawable(this, R.drawable.list_item_divider));
        recyclerView.addItemDecoration(itemDecoration);
        recyclerView.setAdapter(adapter = new ControllerBindingsAdapter());
        updateEmptyTextView();
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.setSystemLocale(newBase));
    }

    private void updateControllerBinding(int keyCode, Binding binding) {
        if (keyCode == KeyEvent.KEYCODE_UNKNOWN) return;

        ExternalControllerBinding controllerBinding = controller.getControllerBinding(keyCode);
        int position;
        if (controllerBinding == null) {
            controllerBinding = new ExternalControllerBinding();
            controllerBinding.setKeyCode(keyCode);
            controllerBinding.setBinding(binding);

            controller.addControllerBinding(controllerBinding);
            profile.save();
            adapter.notifyDataSetChanged();
            updateEmptyTextView();
            position = controller.getPosition(controllerBinding);
        }
        else animateItemView(position = controller.getPosition(controllerBinding));
        recyclerView.scrollToPosition(position);
    }

    private void processJoystickInput() {
        int keyCode = KeyEvent.KEYCODE_UNKNOWN;
        Binding binding = Binding.NONE;
        final int[] axes = {MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
        GamepadState state = controller.getGamepadState();
        final float[] values = {state.thumbLX, state.thumbLY, state.thumbRX, state.thumbRY, state.getDPadX(), state.getDPadY()};

        byte sign;
        for (int i = 0; i < axes.length; i++) {
            if ((sign = Mathf.sign(values[i])) != 0) {
                if (axes[i] == MotionEvent.AXIS_X) {
                    binding = sign > 0 ? Binding.GAMEPAD_LEFT_THUMB_RIGHT : Binding.GAMEPAD_LEFT_THUMB_LEFT;
                }
                else if (axes[i] == MotionEvent.AXIS_Y) {
                    binding = sign > 0 ? Binding.GAMEPAD_LEFT_THUMB_DOWN : Binding.GAMEPAD_LEFT_THUMB_UP;
                }
                else if (axes[i] == MotionEvent.AXIS_Z) {
                    binding = sign > 0 ? Binding.GAMEPAD_RIGHT_THUMB_RIGHT : Binding.GAMEPAD_RIGHT_THUMB_LEFT;
                }
                else if (axes[i] == MotionEvent.AXIS_RZ) {
                    binding = sign > 0 ? Binding.GAMEPAD_RIGHT_THUMB_DOWN : Binding.GAMEPAD_RIGHT_THUMB_UP;
                }
                else if (axes[i] == MotionEvent.AXIS_HAT_X) {
                    binding = sign > 0 ? Binding.GAMEPAD_DPAD_RIGHT : Binding.GAMEPAD_DPAD_LEFT;
                }
                else if (axes[i] == MotionEvent.AXIS_HAT_Y) {
                    binding = sign > 0 ? Binding.GAMEPAD_DPAD_DOWN : Binding.GAMEPAD_DPAD_UP;
                }

                keyCode = ExternalControllerBinding.getKeyCodeForAxis(axes[i], sign);
                break;
            }
        }

        updateControllerBinding(keyCode, binding);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (event.getDeviceId() == controller.getDeviceId() && controller.updateStateFromMotionEvent(event)) {
            GamepadState state = controller.getGamepadState();
            if (state.isPressed(ExternalController.IDX_BUTTON_L2)) updateControllerBinding(KeyEvent.KEYCODE_BUTTON_L2, Binding.GAMEPAD_BUTTON_L2);
            if (state.isPressed(ExternalController.IDX_BUTTON_R2)) updateControllerBinding(KeyEvent.KEYCODE_BUTTON_R2, Binding.GAMEPAD_BUTTON_R2);
            processJoystickInput();
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getDeviceId() == controller.getDeviceId() && event.getRepeatCount() == 0) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) updateControllerBinding(event.getKeyCode(), getAutoBinding(event));
            return true;
        }
        else return super.dispatchKeyEvent(event);
    }

    /** The binding a newly pressed key starts with: the same gamepad button, or the same keyboard key. */
    private static Binding getAutoBinding(KeyEvent event) {
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return Binding.GAMEPAD_BUTTON_A;
            case KeyEvent.KEYCODE_BUTTON_B: return Binding.GAMEPAD_BUTTON_B;
            case KeyEvent.KEYCODE_BUTTON_X: return Binding.GAMEPAD_BUTTON_X;
            case KeyEvent.KEYCODE_BUTTON_Y: return Binding.GAMEPAD_BUTTON_Y;
            case KeyEvent.KEYCODE_BUTTON_L1: return Binding.GAMEPAD_BUTTON_L1;
            case KeyEvent.KEYCODE_BUTTON_R1: return Binding.GAMEPAD_BUTTON_R1;
            case KeyEvent.KEYCODE_BUTTON_L2: return Binding.GAMEPAD_BUTTON_L2;
            case KeyEvent.KEYCODE_BUTTON_R2: return Binding.GAMEPAD_BUTTON_R2;
            case KeyEvent.KEYCODE_BUTTON_SELECT: return Binding.GAMEPAD_BUTTON_SELECT;
            case KeyEvent.KEYCODE_BUTTON_START: return Binding.GAMEPAD_BUTTON_START;
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return Binding.GAMEPAD_BUTTON_L3;
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return Binding.GAMEPAD_BUTTON_R3;
        }

        // arrow keys arrive as DPAD key codes from both gamepads and keyboards
        InputDevice device = event.getDevice();
        boolean fromGamepad = device != null && (device.supportsSource(InputDevice.SOURCE_GAMEPAD) || device.supportsSource(InputDevice.SOURCE_JOYSTICK));
        if (fromGamepad) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP: return Binding.GAMEPAD_DPAD_UP;
                case KeyEvent.KEYCODE_DPAD_RIGHT: return Binding.GAMEPAD_DPAD_RIGHT;
                case KeyEvent.KEYCODE_DPAD_DOWN: return Binding.GAMEPAD_DPAD_DOWN;
                case KeyEvent.KEYCODE_DPAD_LEFT: return Binding.GAMEPAD_DPAD_LEFT;
            }
        }

        XKeycode xKeycode = Keyboard.getXKeycode(keyCode);
        return xKeycode != null ? Binding.fromXKeycode(xKeycode) : Binding.NONE;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        finish();
        return true;
    }

    private class ControllerBindingsAdapter extends RecyclerView.Adapter<ControllerBindingsAdapter.ViewHolder> {
        private class ViewHolder extends RecyclerView.ViewHolder {
            private final ImageButton removeButton;
            private final TextView title;
            private final Spinner bindingType;
            private final Spinner binding;

            private ViewHolder(View view) {
                super(view);
                this.title = view.findViewById(R.id.TVTitle);
                this.bindingType = view.findViewById(R.id.SBindingType);
                this.binding = view.findViewById(R.id.SBinding);
                this.removeButton = view.findViewById(R.id.BTRemove);
            }
        }

        @Override
        public final ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.external_controller_binding_list_item, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            final ExternalControllerBinding item = controller.getControllerBindingAt(position);
            holder.title.setText(item.toString());
            loadBindingSpinner(holder, item);
            holder.removeButton.setOnClickListener((view) -> {
                controller.removeControllerBinding(item);
                profile.save();
                notifyDataSetChanged();
                updateEmptyTextView();
            });
        }

        @Override
        public final int getItemCount() {
            return controller.getControllerBindingCount();
        }

        private void loadBindingSpinner(ViewHolder holder, final ExternalControllerBinding item) {
            final Context $this = ExternalControllerBindingsActivity.this;

            Runnable update = () -> {
                String[] bindingEntries = null;
                switch (holder.bindingType.getSelectedItemPosition()) {
                    case 0:
                        bindingEntries = Binding.keyboardBindingLabels();
                        break;
                    case 1:
                        bindingEntries = Binding.mouseBindingLabels();
                        break;
                    case 2:
                        bindingEntries = Binding.gamepadBindingLabels();
                        break;
                }

                holder.binding.setAdapter(new ArrayAdapter<>($this, android.R.layout.simple_spinner_dropdown_item, bindingEntries));
                AppUtils.setSpinnerSelectionFromValue(holder.binding, item.getBinding().toString());
            };

            holder.bindingType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    update.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            Binding selectedBinding = item.getBinding();
            if (selectedBinding.isKeyboard()) {
                holder.bindingType.setSelection(0, false);
            }
            else if (selectedBinding.isMouse()) {
                holder.bindingType.setSelection(1, false);
            }
            else if (selectedBinding.isGamepad()) {
                holder.bindingType.setSelection(2, false);
            }

            holder.binding.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    Binding binding = Binding.NONE;
                    switch (holder.bindingType.getSelectedItemPosition()) {
                        case 0:
                            binding = Binding.keyboardBindingValues()[position];
                            break;
                        case 1:
                            binding = Binding.mouseBindingValues()[position];
                            break;
                        case 2:
                            binding = Binding.gamepadBindingValues()[position];
                            break;
                    }

                    if (binding != item.getBinding()) {
                        item.setBinding(binding);
                        profile.save();
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });

            update.run();
        }
    }

    private void updateEmptyTextView() {
        emptyTextView.setVisibility(adapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void animateItemView(int position) {
        final ControllerBindingsAdapter.ViewHolder holder = (ControllerBindingsAdapter.ViewHolder)recyclerView.findViewHolderForAdapterPosition(position);
        if (holder != null) {
            final int color = AppUtils.getThemeColor(this, R.attr.colorAccent);
            final ValueAnimator animator = ValueAnimator.ofFloat(0.4f, 0.0f);
            animator.setDuration(200);
            animator.setInterpolator(new AccelerateDecelerateInterpolator());
            animator.addUpdateListener((animation) -> {
                float alpha = (float)animation.getAnimatedValue();
                holder.itemView.setBackgroundColor(Color.argb((int)(alpha * 255), Color.red(color), Color.green(color), Color.blue(color)));
            });
            animator.start();
        }
    }
}
