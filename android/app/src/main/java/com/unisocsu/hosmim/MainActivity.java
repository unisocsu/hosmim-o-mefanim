package com.unisocsu.hosmim;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.LinearLayout;

public class MainActivity extends Activity {
    private GameView gameView;
    private static final String PREFS = "game_settings";
    private static final String HIDE_STATUS = "hide_status_bar";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        gameView = new GameView(this);
        setContentView(gameView);
        applyStatusBarSetting();
    }

    public void showGameSettings() {
        final CheckBox hideStatus = new CheckBox(this);
        hideStatus.setText("הסתר את שורת הסטטוס");
        hideStatus.setTextSize(18);
        hideStatus.setChecked(getPreferences(0).getBoolean(HIDE_STATUS, true));

        LinearLayout box = new LinearLayout(this);
        box.setPadding(32, 16, 32, 8);
        box.addView(hideStatus);

        new AlertDialog.Builder(this)
                .setTitle("הגדרות")
                .setView(box)
                .setPositiveButton("שמירה", (dialog, which) -> {
                    getPreferences(0).edit()
                            .putBoolean(HIDE_STATUS, hideStatus.isChecked())
                            .apply();
                    applyStatusBarSetting();
                    gameView.requestFocus();
                    gameView.invalidate();
                })
                .setNegativeButton("ביטול", null)
                .show();
    }

    private void applyStatusBarSetting() {
        boolean hide = getPreferences(0).getBoolean(HIDE_STATUS, true);
        if (hide) {
            getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }
}
