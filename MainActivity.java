package com.bubble.autoplayer;

import android.app.Activity;
import android.os.Bundle;
import android.provider.Settings;
import android.content.Intent;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    TextView status;
    Switch autoSwitch;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(40, 50, 40, 30);

        TextView title = new TextView(this);
        title.setText("Bubble Pop Auto Player");
        title.setTextSize(26);
        title.setTextColor(Color.BLACK);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, 90));

        TextView info = new TextView(this);
        info.setText("6-hour auto-play • Ad Safety • Never tap Install/Play Store");
        info.setTextSize(16);
        info.setPadding(0, 20, 0, 30);
        root.addView(info);

        Button settings = new Button(this);
        settings.setText("1. Enable Accessibility");
        settings.setOnClickListener(v ->
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        root.addView(settings);

        autoSwitch = new Switch(this);
        autoSwitch.setText("2. Auto Play");
        autoSwitch.setTextSize(18);
        autoSwitch.setPadding(0, 30, 0, 30);
        autoSwitch.setChecked(false);
        autoSwitch.setOnCheckedChangeListener((v, checked) -> {
            BubbleAccessibilityService.setEnabledFromUi(checked);
            updateStatus();
        });
        root.addView(autoSwitch);

        Button openGame = new Button(this);
        openGame.setText("Open Bubble Pop Legends");
        openGame.setOnClickListener(v -> {
            Intent i = getPackageManager().getLaunchIntentForPackage("com.bubble.pop.legends");
            if (i != null) startActivity(i);
            else Toast.makeText(this, "Game package not found. Open the game manually.", Toast.LENGTH_LONG).show();
        });
        root.addView(openGame);

        status = new TextView(this);
        status.setTextSize(16);
        status.setPadding(0, 35, 0, 0);
        root.addView(status);

        TextView note = new TextView(this);
        note.setText("\nSafety: ad/install screens are treated as pause states. Close/Skip/X may be pressed; Install/Download/Play Store is never targeted.");
        note.setTextSize(14);
        root.addView(note);

        setContentView(root);
        updateStatus();
    }

    private void updateStatus() {
        status.setText(BubbleAccessibilityService.isRunning()
                ? "Status: " + BubbleAccessibilityService.statusText()
                : "Status: Accessibility service not connected");
    }

    @Override protected void onResume() {
        super.onResume();
        if (autoSwitch != null) autoSwitch.setChecked(BubbleAccessibilityService.isEnabledFromUi());
        updateStatus();
    }
}
