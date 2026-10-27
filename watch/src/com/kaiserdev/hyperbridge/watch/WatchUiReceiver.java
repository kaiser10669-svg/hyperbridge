package com.kaiserdev.hyperbridge.watch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WatchUiReceiver extends BroadcastReceiver {
    private final WatchMainActivity activity;

    public WatchUiReceiver(WatchMainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (activity != null) {
            activity.updateUi();
        }
    }
}
