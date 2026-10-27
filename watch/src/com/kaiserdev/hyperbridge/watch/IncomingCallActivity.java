package com.kaiserdev.hyperbridge.watch;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import org.json.JSONObject;

public class IncomingCallActivity extends Activity {

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

        setContentView(R.layout.activity_watch_call);

        String name = getIntent().getStringExtra("name");
        String number = getIntent().getStringExtra("number");

        TextView tvCallerName = findViewById(R.id.tvCallerName);
        TextView tvCallerNumber = findViewById(R.id.tvCallerNumber);
        Button btnRejectCall = findViewById(R.id.btnRejectCall);
        Button btnAcceptCall = findViewById(R.id.btnAcceptCall);

        if (name != null) tvCallerName.setText(name);
        if (number != null) tvCallerNumber.setText(number);

        btnRejectCall.setOnClickListener(v -> {
            sendCallAction("REJECT");
            finish();
        });

        btnAcceptCall.setOnClickListener(v -> {
            sendCallAction("ANSWER");
            finish();
        });
    }

    private void sendCallAction(String action) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", BridgeProtocol.TYPE_CALL_ACTION);
            obj.put("action", action);
            WatchBridgeService.sendToPhone(obj);
        } catch (Exception ignored) {}
    }
}
