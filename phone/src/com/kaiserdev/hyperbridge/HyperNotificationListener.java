package com.kaiserdev.hyperbridge;

import android.app.Notification;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class HyperNotificationListener extends NotificationListenerService {
    private static final String TAG = "HyperNotifListener";

    private static final Map<String, String[]> APP_STYLES = new HashMap<>();
    static {
        // [appName, neonColor]
        APP_STYLES.put("org.telegram.messenger", new String[]{"Telegram", "#0088cc"});
        APP_STYLES.put("org.telegram.messenger.web", new String[]{"Telegram", "#0088cc"});
        APP_STYLES.put("org.telegram.plus", new String[]{"Plus Messenger", "#0088cc"});
        APP_STYLES.put("org.thunderdog.challegram", new String[]{"Telegram X", "#0088cc"});
        APP_STYLES.put("com.whatsapp", new String[]{"WhatsApp", "#25D366"});
        APP_STYLES.put("com.whatsapp.w4b", new String[]{"WhatsApp Business", "#25D366"});
        APP_STYLES.put("com.vkontakte.android", new String[]{"VKontakte", "#2787F5"});
        APP_STYLES.put("com.google.android.apps.messaging", new String[]{"Сообщения", "#4285F4"});
        APP_STYLES.put("com.android.mms", new String[]{"SMS", "#4285F4"});
        APP_STYLES.put("com.instagram.android", new String[]{"Instagram", "#E1306C"});
        APP_STYLES.put("ru.tinkoff.activities", new String[]{"Т-Банк", "#FFDD2D"});
        APP_STYLES.put("com.idamob.tinkoff.android", new String[]{"Т-Банк", "#FFDD2D"});
        APP_STYLES.put("ru.sberbankmobile", new String[]{"СберБанк", "#21A038"});
        APP_STYLES.put("ru.sberbank.sberinvestor", new String[]{"СберИнвестиции", "#21A038"});
        APP_STYLES.put("ru.vtb24.mobilebanking.android", new String[]{"ВТБ", "#002882"});
        APP_STYLES.put("ru.alfabank.mobile.android", new String[]{"Альфа-Банк", "#EF3124"});
        APP_STYLES.put("ru.raiffeisennews", new String[]{"Райффайзен", "#FEE600"});
        APP_STYLES.put("ru.gazprombank.android.mobilebank.app", new String[]{"Газпромбанк", "#003882"});
        APP_STYLES.put("ru.sovcomcard.halva.v1", new String[]{"Совкомбанк", "#FF4A00"});
        APP_STYLES.put("ru.yandex.bank", new String[]{"Яндекс Банк", "#FC3F1D"});
        APP_STYLES.put("ru.ozon.app.android", new String[]{"Ozon Банк", "#005BFF"});
        APP_STYLES.put("com.wildberries", new String[]{"Wildberries", "#CB11AB"});
        APP_STYLES.put("ru.mw", new String[]{"МТС Банк", "#E30611"});
        APP_STYLES.put("com.yandex.mail", new String[]{"Яндекс Почта", "#FF3333"});
        APP_STYLES.put("com.google.android.gm", new String[]{"Gmail", "#EA4335"});
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;

        // Skip our own notifications or ongoing (like music progress / persistent services)
        if (sbn.getPackageName().equals(getPackageName())) return;
        if (sbn.isOngoing()) return;

        Notification n = sbn.getNotification();
        if (n == null) return;

        Bundle extras = n.extras;
        if (extras == null) return;

        CharSequence titleCs = extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence textCs = extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigTextCs = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);

        String title = titleCs != null ? titleCs.toString().trim() : "";
        String text = bigTextCs != null ? bigTextCs.toString().trim() : (textCs != null ? textCs.toString().trim() : "");

        if (title.isEmpty() && text.isEmpty()) return;

        String pkg = sbn.getPackageName();
        String appName = getAppName(pkg);
        String color = "#00f0ff"; // Default cyan

        if (APP_STYLES.containsKey(pkg)) {
            String[] info = APP_STYLES.get(pkg);
            if (info != null && info.length >= 2) {
                appName = info[0];
                color = info[1];
            }
        }

        long notifId = sbn.getId();
        JSONObject json = BridgeProtocol.createNotification(notifId, pkg, appName, title, text, color, true);
        Log.d(TAG, "Forwarding notification: " + appName + " -> " + title + ": " + text);

        PhoneBridgeService.sendToWatch(json);
    }

    private String getAppName(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence label = pm.getApplicationLabel(ai);
            if (label != null) return label.toString();
        } catch (Exception ignored) {}
        return pkg;
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // Can optionally sync notification removals
    }
}
