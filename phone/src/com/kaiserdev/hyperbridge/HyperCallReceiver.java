package com.kaiserdev.hyperbridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.telephony.TelephonyManager;
import android.util.Log;
import org.json.JSONObject;

public class HyperCallReceiver extends BroadcastReceiver {
    private static final String TAG = "HyperCallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(intent.getAction())) return;

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;

        Log.d(TAG, "Phone state changed: " + state);

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            String incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
            String callerName = "Входящий вызов";

            if (incomingNumber != null && !incomingNumber.isEmpty()) {
                String contact = getContactName(context, incomingNumber);
                if (contact != null && !contact.isEmpty()) {
                    callerName = contact;
                } else {
                    callerName = incomingNumber;
                }
            }

            JSONObject callJson = BridgeProtocol.createIncomingCall(
                    incomingNumber != null ? incomingNumber : "",
                    callerName
            );
            PhoneBridgeService.sendToWatch(callJson);

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state) || TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            try {
                JSONObject callEnd = new JSONObject();
                callEnd.put("type", BridgeProtocol.TYPE_CALL_ENDED);
                callEnd.put("time", System.currentTimeMillis());
                PhoneBridgeService.sendToWatch(callEnd);
            } catch (Exception ignored) {}
        }
    }

    private String getContactName(Context context, String phoneNumber) {
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
            String[] projection = new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME};
            Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        int index = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME);
                        if (index >= 0) {
                            return cursor.getString(index);
                        }
                    }
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Contact lookup failed: " + e.getMessage());
        }
        return null;
    }
}
