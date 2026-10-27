package com.kaiserdev.hyperbridge.watch;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONObject;

public class NotificationPopupActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

        setContentView(R.layout.activity_watch_notification);

        long id = getIntent().getLongExtra("id", 0);
        String app = getIntent().getStringExtra("app");
        String title = getIntent().getStringExtra("title");
        String text = getIntent().getStringExtra("text");
        String color = getIntent().getStringExtra("color");

        TextView tvNotifApp = findViewById(R.id.tvNotifApp);
        TextView tvNotifTitle = findViewById(R.id.tvNotifTitle);
        TextView tvNotifText = findViewById(R.id.tvNotifText);
        Button btnReplyOk = findViewById(R.id.btnReplyOk);
        Button btnReplyCall = findViewById(R.id.btnReplyCall);
        Button btnDismiss = findViewById(R.id.btnDismiss);

        if (app != null) tvNotifApp.setText(app);
        if (title != null) tvNotifTitle.setText(title);
        if (text != null) tvNotifText.setText(text);

        if (color != null) {
            try {
                tvNotifApp.setTextColor(Color.parseColor(color));
            } catch (Exception ignored) {}
        }

        btnReplyOk.setOnClickListener(v -> {
            sendAction(id, "ОК");
            finish();
        });

        btnReplyCall.setOnClickListener(v -> {
            sendAction(id, "Перезвоню позже");
            finish();
        });

        btnDismiss.setOnClickListener(v -> finish());
    }

    private void sendAction(long notifId, String reply) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", BridgeProtocol.TYPE_NOTIFICATION_ACTION);
            obj.put("id", notifId);
            obj.put("reply", reply);
            WatchBridgeService.sendToPhone(obj);
        } catch (Exception ignored) {}
    }
}
