package com.kaiserdev.hyperbridge.watch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;

public class WatchBridgeService extends Service implements SensorEventListener {
    private static final String TAG = "WatchBridgeService";

    private static final String CHANNEL_FG = "hyperbridge_watch_fg";
    private static final String CHANNEL_NOTIF = "hyper_notif_channel";
    private static final String CHANNEL_CALL = "hyper_call_channel";

    public static volatile boolean isConnected = false;
    public static volatile int phoneBattery = -1;
    public static volatile int currentHeartRate = 0;
    public static volatile int currentSteps = 0;
    public static volatile String lastNotification = null;

    private static OutputStream activeOut = null;
    private static final Object lock = new Object();

    private Thread btThread;
    private Thread tcpThread;
    private Thread telemetryThread;
    private volatile boolean running = true;

    private SensorManager sensorManager;
    private Sensor heartRateSensor;
    private Sensor stepSensor;
    private Sensor stepDetectorSensor;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        Notification notif = buildForegroundNotification("HyperBridge активен", "Ожидание подключения...");
        startForeground(1001, notif);

        initSensors();
        startBtServer();
        startTcpServer();
        startTelemetryLoop();
    }

    private void initSensors() {
        try {
            sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE);
                if (heartRateSensor != null) {
                    sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST);
                    Log.i(TAG, "Heart Rate sensor registered: " + heartRateSensor.getName());
                } else {
                    Log.w(TAG, "Heart Rate sensor not found");
                }

                stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
                if (stepSensor != null) {
                    sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
                    Log.i(TAG, "Step Counter sensor registered: " + stepSensor.getName());
                }

                stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
                if (stepDetectorSensor != null) {
                    sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_UI);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Sensor init error", e);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null || event.values == null || event.values.length == 0) return;
        int type = event.sensor.getType();
        if (type == Sensor.TYPE_HEART_RATE) {
            int hr = (int) event.values[0];
            if (hr > 30 && hr < 240) {
                currentHeartRate = hr;
                sendTelemetry();
                sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_UI"));
            }
        } else if (type == Sensor.TYPE_STEP_COUNTER) {
            int steps = (int) event.values[0];
            if (steps >= 0) {
                currentSteps = steps;
                sendTelemetry();
                sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_UI"));
            }
        } else if (type == Sensor.TYPE_STEP_DETECTOR) {
            if (event.values[0] == 1.0f) {
                currentSteps++;
                sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_UI"));
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            // 1. Foreground ongoing service channel
            NotificationChannel fg = new NotificationChannel(
                    CHANNEL_FG,
                    "HyperBridge Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            fg.setShowBadge(false);
            nm.createNotificationChannel(fg);

            // 2. High priority notification heads-up channel
            NotificationChannel notifCh = new NotificationChannel(
                    CHANNEL_NOTIF,
                    "Уведомления со смартфона",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notifCh.enableVibration(true);
            notifCh.setVibrationPattern(new long[]{0, 250, 100, 250});
            notifCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notifCh.setBypassDnd(true);
            nm.createNotificationChannel(notifCh);

            // 3. Max priority incoming call channel
            NotificationChannel callCh = new NotificationChannel(
                    CHANNEL_CALL,
                    "Входящие звонки",
                    NotificationManager.IMPORTANCE_HIGH
            );
            callCh.enableVibration(true);
            callCh.setVibrationPattern(new long[]{0, 500, 200, 500, 200, 500});
            callCh.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            callCh.setBypassDnd(true);
            nm.createNotificationChannel(callCh);
        }
    }

    private Notification buildForegroundNotification(String title, String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_FG);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();
    }

    private void startBtServer() {
        btThread = new Thread(() -> {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return;

            while (running) {
                try {
                    UUID uuid = UUID.fromString(BridgeProtocol.DEFAULT_BT_UUID);
                    BluetoothServerSocket server = adapter.listenUsingInsecureRfcommWithServiceRecord("HyperBridge", uuid);
                    Log.d(TAG, "BT Server listening on SPP...");
                    BluetoothSocket socket = server.accept();
                    server.close();
                    if (socket != null) {
                        new Thread(() -> {
                            try {
                                handleClient(socket.getInputStream(), socket.getOutputStream(), "Bluetooth");
                            } catch (Exception ignored) {}
                        }).start();
                    }
                } catch (Exception e) {
                    try { Thread.sleep(3000); } catch (Exception ignored) {}
                }
            }
        });
        btThread.start();
    }

    private void startTcpServer() {
        tcpThread = new Thread(() -> {
            while (running) {
                try (ServerSocket server = new ServerSocket(BridgeProtocol.TCP_PORT)) {
                    Log.d(TAG, "TCP Server listening on port " + BridgeProtocol.TCP_PORT);
                    while (running) {
                        Socket socket = server.accept();
                        new Thread(() -> {
                            try {
                                handleClient(socket.getInputStream(), socket.getOutputStream(), "Wi-Fi");
                            } catch (Exception ignored) {}
                        }).start();
                    }
                } catch (Exception e) {
                    try { Thread.sleep(3000); } catch (Exception ignored) {}
                }
            }
        });
        tcpThread.start();
    }

    private void startTelemetryLoop() {
        telemetryThread = new Thread(() -> {
            while (running) {
                if (isConnected && activeOut != null) {
                    sendTelemetry();
                }
                try {
                    Thread.sleep(5000);
                } catch (Exception ignored) {}
            }
        });
        telemetryThread.start();
    }

    private void handleClient(InputStream in, OutputStream out, String medium) {
        synchronized (lock) {
            if (activeOut != null && activeOut != out) {
                try { activeOut.close(); } catch (Exception ignored) {}
            }
            activeOut = out;
            isConnected = true;
        }
        updateStatus("Подключено (" + medium + ")");
        sendPong();

        try {
            DataInputStream dis = new DataInputStream(in);
            while (running) {
                int len = dis.readInt();
                if (len <= 0 || len > 65536) break;
                byte[] buf = new byte[len];
                dis.readFully(buf);
                String jsonStr = new String(buf, "UTF-8");
                processMessage(new JSONObject(jsonStr));
            }
        } catch (Exception e) {
            Log.e(TAG, "Connection lost: " + e.getMessage());
        } finally {
            synchronized (lock) {
                if (activeOut == out) {
                    activeOut = null;
                    isConnected = false;
                }
            }
            updateStatus("Ожидание подключения...");
        }
    }

    private void processMessage(JSONObject msg) {
        try {
            String type = msg.optString("type");
            if (BridgeProtocol.TYPE_PING.equals(type)) {
                phoneBattery = msg.optInt("battery", -1);
                sendPong();
                sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_UI"));
            } else if (BridgeProtocol.TYPE_NOTIFICATION.equals(type)) {
                handleNotification(msg);
            } else if (BridgeProtocol.TYPE_CALL_INCOMING.equals(type)) {
                handleCall(msg);
            } else if (BridgeProtocol.TYPE_CALL_ENDED.equals(type)) {
                handleCallEnded();
            } else if (BridgeProtocol.TYPE_LAUNCH_APP.equals(type)) {
                handleLaunchApp(msg.optString("package"));
            } else if (BridgeProtocol.TYPE_FIND_WATCH.equals(type)) {
                handleFindWatch();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing msg", e);
        }
    }

    private void handleLaunchApp(String pkg) {
        try {
            Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch app: " + pkg, e);
        }
    }

    private void wakeScreen(int timeoutMs) {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                PowerManager.WakeLock wl = pm.newWakeLock(
                        PowerManager.SCREEN_BRIGHT_WAKE_LOCK |
                        PowerManager.ACQUIRE_CAUSES_WAKEUP |
                        PowerManager.ON_AFTER_RELEASE,
                        "hyperbridge:wake_screen"
                );
                wl.acquire(timeoutMs);
            }
        } catch (Exception e) {
            Log.e(TAG, "WakeScreen error", e);
        }
    }

    private void handleFindWatch() {
        wakeScreen(10000);
        vibrateCall();

        Intent intent = new Intent(this, NotificationPopupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("id", System.currentTimeMillis());
        intent.putExtra("app", "HyperBridge");
        intent.putExtra("title", "🔍 ПОИСК ЧАСОВ");
        intent.putExtra("text", "Часы найдены! Сигнал отправлен с Vivo X300 Ultra.");
        intent.putExtra("color", "#FFD600");

        PendingIntent pi = PendingIntent.getActivity(
                this,
                9999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nb = new Notification.Builder(this, CHANNEL_NOTIF);
            } else {
                nb = new Notification.Builder(this);
                nb.setPriority(Notification.PRIORITY_MAX);
            }

            nb.setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("🔍 ПОИСК ЧАСОВ")
                    .setContentText("Часы найдены! Сигнал отправлен с Vivo X300 Ultra.")
                    .setFullScreenIntent(pi, true)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_ALARM);

            nm.notify(9999, nb.build());
        }

        try {
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void handleNotification(JSONObject msg) {
        wakeScreen(5000);
        vibrateNotification();

        long id = msg.optLong("id", System.currentTimeMillis());
        String appName = msg.optString("appName", "Уведомление");
        String title = msg.optString("title", "");
        String text = msg.optString("text", "");
        String color = msg.optString("color", "#00F0FF");

        lastNotification = (!title.isEmpty() ? title : appName) + ": " + text;

        Intent intent = new Intent(this, NotificationPopupActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("id", id);
        intent.putExtra("app", appName);
        intent.putExtra("title", title);
        intent.putExtra("text", text);
        intent.putExtra("color", color);

        int reqCode = (int) (id & 0x7FFFFFFF);
        PendingIntent pi = PendingIntent.getActivity(
                this,
                reqCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nb = new Notification.Builder(this, CHANNEL_NOTIF);
            } else {
                nb = new Notification.Builder(this);
                nb.setPriority(Notification.PRIORITY_MAX);
            }

            String displayTitle = appName + (title.isEmpty() ? "" : " • " + title);
            nb.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(displayTitle)
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text).setSummaryText(appName))
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_MESSAGE);

            nm.notify((int) (id & 0x7FFFFFFF), nb.build());
        }

        try {
            startActivity(intent);
        } catch (Exception ignored) {}

        sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_UI"));
    }

    private void handleCall(JSONObject msg) {
        wakeScreen(15000);
        vibrateCall();

        String name = msg.optString("name", "Входящий вызов");
        String number = msg.optString("number", "");

        Intent intent = new Intent(this, IncomingCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("name", name);
        intent.putExtra("number", number);

        PendingIntent pi = PendingIntent.getActivity(
                this,
                2002,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            Notification.Builder nb;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nb = new Notification.Builder(this, CHANNEL_CALL);
            } else {
                nb = new Notification.Builder(this);
                nb.setPriority(Notification.PRIORITY_MAX);
            }

            nb.setSmallIcon(android.R.drawable.sym_call_incoming)
                    .setContentTitle("📞 " + name)
                    .setContentText(number.isEmpty() ? "Входящий вызов" : number)
                    .setContentIntent(pi)
                    .setFullScreenIntent(pi, true)
                    .setOngoing(true)
                    .setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_CALL);

            nm.notify(2002, nb.build());
        }

        try {
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void handleCallEnded() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(2002);
        }
    }

    private void vibrateNotification() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 180, 80, 220}, -1));
            } else {
                v.vibrate(250);
            }
        }
    }

    private void vibrateCall() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 500, 200, 500, 200, 500}, -1));
            } else {
                v.vibrate(1000);
            }
        }
    }

    private void updateStatus(String status) {
        Notification notif = buildForegroundNotification("HyperBridge", status);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(1001, notif);
        sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_UI"));
    }

    public static void sendToPhone(JSONObject obj) {
        new Thread(() -> {
            synchronized (lock) {
                if (activeOut == null) return;
                try {
                    byte[] bytes = obj.toString().getBytes("UTF-8");
                    DataOutputStream dos = new DataOutputStream(activeOut);
                    dos.writeInt(bytes.length);
                    dos.write(bytes);
                    dos.flush();
                } catch (Exception e) {
                    Log.e(TAG, "sendToPhone error", e);
                }
            }
        }).start();
    }

    private void sendPong() {
        int bat = getBatteryLevel(this);
        boolean chg = isCharging(this);
        int hr = currentHeartRate;
        int st = currentSteps;
        JSONObject pong = BridgeProtocol.createPong(bat, chg, hr, st);
        sendToPhone(pong);
    }

    private void sendTelemetry() {
        if (!isConnected) return;
        int bat = getBatteryLevel(this);
        boolean chg = isCharging(this);
        int hr = currentHeartRate;
        int st = currentSteps;
        JSONObject tel = BridgeProtocol.createTelemetry(bat, chg, hr, st);
        sendToPhone(tel);
    }

    public static int getBatteryLevel(Context ctx) {
        try {
            Intent batIntent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batIntent != null) {
                int level = batIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    return (int) ((level / (float) scale) * 100);
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    public static boolean isCharging(Context ctx) {
        try {
            Intent batIntent = ctx.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batIntent != null) {
                int status = batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = batIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                return (status == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0);
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        synchronized (lock) {
            if (activeOut != null) {
                try { activeOut.close(); } catch (Exception ignored) {}
                activeOut = null;
            }
        }
        super.onDestroy();
    }
}
