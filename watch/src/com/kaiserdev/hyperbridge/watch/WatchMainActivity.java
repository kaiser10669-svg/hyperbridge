package com.kaiserdev.hyperbridge.watch;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;

import java.util.Locale;

public class WatchMainActivity extends Activity {

    private TextView tvStatus;
    private TextView tvPhoneBattery;
    private TextView tvWatchSelfBattery;
    private TextView tvWatchHeartRate;
    private TextView tvWatchSteps;
    private TextView tvLastMsg;
    private Button btnTestVibrate;
    private Button btnFindPhone;

    private WatchUiReceiver uiReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_watch_main);

        tvStatus = findViewById(R.id.tvStatus);
        tvPhoneBattery = findViewById(R.id.tvPhoneBattery);
        tvWatchSelfBattery = findViewById(R.id.tvWatchSelfBattery);
        tvWatchHeartRate = findViewById(R.id.tvWatchHeartRate);
        tvWatchSteps = findViewById(R.id.tvWatchSteps);
        tvLastMsg = findViewById(R.id.tvLastMsg);
        btnTestVibrate = findViewById(R.id.btnTestVibrate);
        btnFindPhone = findViewById(R.id.btnFindPhone);

        btnTestVibrate.setOnClickListener(v -> {
            Vibrator vib = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vib != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createWaveform(new long[]{0, 100, 70, 140}, -1));
                } else {
                    vib.vibrate(250);
                }
            }
        });

        btnFindPhone.setOnClickListener(v -> {
            try {
                JSONObject obj = new JSONObject();
                obj.put("type", "FIND_PHONE");
                WatchBridgeService.sendToPhone(obj);
                Toast.makeText(this, "Поиск телефона отправлен!", Toast.LENGTH_SHORT).show();
            } catch (Exception ignored) {}
        });

        // Check permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                perms = new String[]{
                        "android.permission.BODY_SENSORS",
                        "android.permission.ACTIVITY_RECOGNITION"
                };
            } else {
                perms = new String[]{
                        "android.permission.BODY_SENSORS"
                };
            }
            requestPermissions(perms, 101);
        }

        // Start service
        Intent sIntent = new Intent(this, WatchBridgeService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(sIntent);
        } else {
            startService(sIntent);
        }

        uiReceiver = new WatchUiReceiver(this);
        updateUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(uiReceiver, new IntentFilter("com.kaiserdev.hyperbridge.UPDATE_UI"));
        updateUi();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(uiReceiver); } catch (Exception ignored) {}
    }

    public void updateUi() {
        runOnUiThread(() -> {
            if (WatchBridgeService.isConnected) {
                tvStatus.setText("● ПОДКЛЮЧЕНО");
                tvStatus.setTextColor(0xFF00E676); // Neon green
            } else {
                tvStatus.setText("○ ОЖИДАНИЕ СВЯЗИ");
                tvStatus.setTextColor(0xFFFFD600); // Amber
            }

            if (WatchBridgeService.phoneBattery >= 0) {
                tvPhoneBattery.setText(WatchBridgeService.phoneBattery + "%");
            } else {
                tvPhoneBattery.setText("--%");
            }

            int wBat = WatchBridgeService.getBatteryLevel(this);
            tvWatchSelfBattery.setText(wBat + "%");

            tvWatchHeartRate.setText(String.valueOf(WatchBridgeService.currentHeartRate));
            tvWatchSteps.setText(String.format(Locale.getDefault(), "%,d", WatchBridgeService.currentSteps));

            if (WatchBridgeService.lastNotification != null) {
                tvLastMsg.setText(WatchBridgeService.lastNotification);
            }
        });
    }
}
