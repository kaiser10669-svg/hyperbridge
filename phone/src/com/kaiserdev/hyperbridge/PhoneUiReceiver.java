package com.kaiserdev.hyperbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class PhoneUiReceiver extends BroadcastReceiver {
    private final PhoneMainActivity activity;

    public PhoneUiReceiver(PhoneMainActivity activity) {
        this.activity = activity;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (activity != null) {
            activity.updateUi();
        }
    }
}
