package com.kaiserdev.hyperbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.telecom.TelecomManager;
import android.util.Log;
import org.json.JSONObject;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.UUID;

public class PhoneBridgeService extends Service {
    private static final String TAG = "PhoneBridgeService";
    private static final String CHANNEL_ID = "hyperbridge_phone_ch";
    public static final String WATCH_MAC = "B0:A3:F2:D7:CE:68";
    public static final String WATCH_IP = "192.168.1.57";

    // Standard Bluetooth SIG UUIDs
    public static final UUID UUID_BATTERY_SERVICE = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_BATTERY_LEVEL   = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_HEART_RATE_SERVICE = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_HEART_RATE_MEASUREMENT = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb");
    public static final UUID UUID_CLIENT_CONFIG   = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // Live Honest Telemetry
    public static volatile boolean isConnected = false;
    public static volatile String currentMedium = "Отключено";

    // Phone Hardware Stats
    public static volatile int phoneBattery = 71;
    public static volatile boolean phoneIsCharging = false;
    public static volatile float phoneTemp = 36.0f;

    // Watch Real Hardware Stats
    public static volatile int watchBattery = -1; // -1 = unknown / waiting
    public static volatile boolean watchIsCharging = false;
    public static volatile int realHeartRate = 0; // 0 = waiting for watch sensor
    public static volatile int realSteps = 0; // 0 = waiting for watch sensor
    public static volatile long lastTelemetryTime = 0;

    private static OutputStream activeOut = null;
    private static BluetoothGatt activeGatt = null;
    private static final Object lock = new Object();
    private static PhoneBridgeService instance = null;

    private static class PhoneStepListener implements android.hardware.SensorEventListener {
        @Override
        public void onSensorChanged(android.hardware.SensorEvent event) {
            // Keep watch steps as primary authority; do not overwrite watch steps
        }
        @Override
        public void onAccuracyChanged(android.hardware.Sensor sensor, int accuracy) {}
    }

    private android.hardware.SensorManager sensorManager;
    private android.hardware.Sensor stepSensor;
    private final android.hardware.SensorEventListener stepListener = new PhoneStepListener();

    private Thread workerThread;
    private Thread heartbeatThread;
    private volatile boolean running = true;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        Notification notif = buildNotification("HyperBridge активен", "Поиск Xiaomi Watch 5...");
        startForeground(2001, notif);

        initPhoneSensors();
        updatePhoneBatteryStats();
        startConnectionWorker();
        startHeartbeat();
    }

    private void initPhoneSensors() {
        try {
            sensorManager = (android.hardware.SensorManager) getSystemService(Context.SENSOR_SERVICE);
            if (sensorManager != null) {
                stepSensor = sensorManager.getDefaultSensor(android.hardware.Sensor.TYPE_STEP_COUNTER);
                if (stepSensor != null) {
                    sensorManager.registerListener(stepListener, stepSensor, android.hardware.SensorManager.SENSOR_DELAY_UI);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Phone step sensor init error", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "HyperBridge Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification(String title, String text) {
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }
        return builder.setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build();
    }

    private void startConnectionWorker() {
        workerThread = new Thread(() -> {
            while (running) {
                if (!isConnected) {
                    tryConnectBluetooth();
                }
                try { Thread.sleep(4000); } catch (Exception ignored) {}
            }
        });
        workerThread.start();

        new Thread(() -> {
            while (running) {
                if (!isConnected) {
                    tryConnectWifi();
                }
                try { Thread.sleep(3000); } catch (Exception ignored) {}
            }
        }).start();
    }

    public static void connectBluetoothNow(Context ctx) {
        new Thread(() -> {
            try {
                BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
                if (adapter == null || !adapter.isEnabled()) {
                    Log.w(TAG, "Bluetooth not available or disabled");
                    return;
                }
                BluetoothDevice device = adapter.getRemoteDevice(WATCH_MAC);
                if (device != null) {
                    if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                        Log.i(TAG, "Device not bonded. Calling createBond()...");
                        device.createBond();
                    }

                    if (activeGatt != null) {
                        try { activeGatt.close(); } catch (Exception ignored) {}
                        activeGatt = null;
                    }

                    Log.i(TAG, "Initiating direct BLE GATT connect to " + WATCH_MAC + "...");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        activeGatt = device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                    } else {
                        activeGatt = device.connectGatt(ctx, false, gattCallback);
                    }
                }

                // Also launch BLE Scanner in case watch rotates its MAC address
                BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
                if (scanner != null) {
                    Log.i(TAG, "Starting BLE Scanner to find Xiaomi Watch 5 advertisement...");
                    ScanSettings settings = new ScanSettings.Builder()
                            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                            .build();
                    scanner.startScan(null, settings, new WatchBleScanCallback(ctx, scanner));
                }
            } catch (Exception e) {
                Log.e(TAG, "connectBluetoothNow error", e);
            }
        }).start();
    }

    private static class WatchBleScanCallback extends ScanCallback {
        private final Context ctx;
        private final BluetoothLeScanner scanner;

        WatchBleScanCallback(Context ctx, BluetoothLeScanner scanner) {
            this.ctx = ctx;
            this.scanner = scanner;
        }

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null || result.getDevice() == null) return;
            BluetoothDevice dev = result.getDevice();
            String name = null;
            try { name = dev.getName(); } catch (SecurityException ignored) {}
            if (name == null && result.getScanRecord() != null) {
                name = result.getScanRecord().getDeviceName();
            }
            String addr = dev.getAddress();
            if ((name != null && name.toLowerCase().contains("xiaomi")) || WATCH_MAC.equalsIgnoreCase(addr)) {
                Log.i(TAG, "BLE Scanner found target watch: " + name + " [" + addr + "]! Connecting GATT...");
                try { scanner.stopScan(this); } catch (Exception ignored) {}
                if (activeGatt != null) {
                    try { activeGatt.close(); } catch (Exception ignored) {}
                }
                activeGatt = dev.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
            }
        }
    }

    private static void tryConnectGattDirect(Context ctx) {
        connectBluetoothNow(ctx);
    }

    private boolean tryConnectBluetooth() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return false;

        try {
            BluetoothDevice device = adapter.getRemoteDevice(WATCH_MAC);
            if (device == null) return false;

            if (activeGatt == null) {
                Log.d(TAG, "Initiating BLE GATT connect to " + WATCH_MAC);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activeGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
                } else {
                    activeGatt = device.connectGatt(this, false, gattCallback);
                }
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "tryConnectBluetooth error", e);
            return false;
        }
    }

    private static class WatchGattCallback extends BluetoothGattCallback {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "GATT onConnectionStateChange: status=" + status + ", newState=" + newState);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "BLE GATT Connected to Xiaomi Watch 5!");
                activeGatt = gatt;
                isConnected = true;
                currentMedium = "Bluetooth Ultra-Link (BLE)";
                if (instance != null) {
                    instance.updateStatus("Подключено по Bluetooth к Xiaomi Watch 5");
                    instance.sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
                }
                gatt.discoverServices();
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.w(TAG, "BLE GATT Disconnected: status=" + status);
                try {
                    gatt.close();
                } catch (Exception ignored) {}
                if (activeGatt == gatt) {
                    activeGatt = null;
                }
                if (currentMedium.contains("Bluetooth")) {
                    isConnected = false;
                    currentMedium = "Отключено";
                    if (instance != null) {
                        instance.updateStatus("Поиск Xiaomi Watch 5...");
                        instance.sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
                    }
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "GATT Services Discovered successfully!");
                // 1. Read Battery
                BluetoothGattService batService = gatt.getService(UUID_BATTERY_SERVICE);
                if (batService != null) {
                    BluetoothGattCharacteristic batChar = batService.getCharacteristic(UUID_BATTERY_LEVEL);
                    if (batChar != null) {
                        gatt.readCharacteristic(batChar);
                    }
                }
                // 2. Subscribe to Heart Rate notifications
                BluetoothGattService hrService = gatt.getService(UUID_HEART_RATE_SERVICE);
                if (hrService != null) {
                    BluetoothGattCharacteristic hrChar = hrService.getCharacteristic(UUID_HEART_RATE_MEASUREMENT);
                    if (hrChar != null) {
                        gatt.setCharacteristicNotification(hrChar, true);
                        BluetoothGattDescriptor desc = hrChar.getDescriptor(UUID_CLIENT_CONFIG);
                        if (desc != null) {
                            desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                            gatt.writeDescriptor(desc);
                        }
                    }
                }
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            handleCharacteristic(characteristic);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            handleCharacteristic(characteristic);
        }

        private void handleCharacteristic(BluetoothGattCharacteristic ch) {
            if (ch == null || ch.getValue() == null) return;
            UUID u = ch.getUuid();
            byte[] val = ch.getValue();

            if (UUID_BATTERY_LEVEL.equals(u) && val.length > 0) {
                int bat = val[0] & 0xFF;
                if (bat >= 0 && bat <= 100) {
                    watchBattery = bat;
                    lastTelemetryTime = System.currentTimeMillis();
                    if (instance != null) {
                        instance.sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
                    }
                }
            } else if (UUID_HEART_RATE_MEASUREMENT.equals(u) && val.length > 1) {
                int flag = val[0] & 0xFF;
                int hr = ((flag & 1) == 0) ? (val[1] & 0xFF) : ((val[1] & 0xFF) | ((val[2] & 0xFF) << 8));
                if (hr > 30 && hr < 240) {
                    realHeartRate = hr;
                    lastTelemetryTime = System.currentTimeMillis();
                    if (instance != null) {
                        instance.sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
                    }
                }
            }
        }
    }

    private static final BluetoothGattCallback gattCallback = new WatchGattCallback();

    private boolean tryConnectWifi() {
        String[] hosts = new String[]{WATCH_IP, "127.0.0.1", "192.168.1.144"};
        for (String host : hosts) {
            try {
                Socket socket = new Socket();
                socket.connect(new InetSocketAddress(host, BridgeProtocol.TCP_PORT), 1500);
                Log.i(TAG, "TCP Socket connected to Xiaomi Watch via " + host + "!");
                String label = host.equals("127.0.0.1") ? "Ultra-Link Direct (1 ms)" : ("Wi-Fi Direct (" + host + ")");
                handleActiveConnection(socket.getInputStream(), socket.getOutputStream(), label);
                return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private void handleActiveConnection(InputStream in, OutputStream out, String medium) {
        synchronized (lock) {
            if (isConnected && activeOut != null) {
                try { in.close(); out.close(); } catch (Exception ignored) {}
                return;
            }
            activeOut = out;
            isConnected = true;
            currentMedium = medium;
        }
        updateStatus("Подключено к Xiaomi Watch 5 (" + medium + ")");
        sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
        sendPing();

        try {
            DataInputStream dis = new DataInputStream(in);
            while (running) {
                int len = dis.readInt();
                if (len <= 0 || len > 65536) break;
                byte[] buf = new byte[len];
                dis.readFully(buf);
                String jsonStr = new String(buf, "UTF-8");
                processIncomingFromWatch(new JSONObject(jsonStr));
            }
        } catch (Exception e) {
            Log.e(TAG, "Connection lost: " + e.getMessage());
        } finally {
            synchronized (lock) {
                if (activeOut == out) {
                    activeOut = null;
                    if (!currentMedium.contains("BLE")) {
                        isConnected = false;
                        currentMedium = "Отключено";
                    }
                }
            }
            updateStatus("Поиск Xiaomi Watch 5...");
            sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
        }
    }

    private void processIncomingFromWatch(JSONObject msg) {
        try {
            String type = msg.optString("type");
            if (BridgeProtocol.TYPE_PONG.equals(type) || BridgeProtocol.TYPE_TELEMETRY.equals(type)) {
                int bat = msg.optInt("battery", -1);
                if (bat >= 0) watchBattery = bat;
                watchIsCharging = msg.optBoolean("isCharging", false);

                int hr = msg.optInt("heartRate", 0);
                if (hr > 30 && hr < 240) {
                    realHeartRate = hr;
                }

                int steps = msg.optInt("steps", -1);
                if (steps >= 0) {
                    realSteps = steps;
                }

                lastTelemetryTime = System.currentTimeMillis();
                sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
            } else if (BridgeProtocol.TYPE_CALL_ACTION.equals(type)) {
                String action = msg.optString("action");
                if ("REJECT".equals(action)) {
                    endCurrentCall();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing watch msg", e);
        }
    }

    private void endCurrentCall() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                TelecomManager tm = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                if (tm != null) tm.endCall();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to end call", e);
        }
    }

    private void startHeartbeat() {
        heartbeatThread = new Thread(() -> {
            while (running) {
                updatePhoneBatteryStats();
                if (isConnected && activeOut != null) {
                    sendPing();
                }
                try { Thread.sleep(10000); } catch (Exception ignored) {}
            }
        });
        heartbeatThread.start();
    }

    private void updatePhoneBatteryStats() {
        try {
            Intent batIntent = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batIntent != null) {
                int level = batIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int status = batIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = batIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                int temp = batIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);

                if (level >= 0 && scale > 0) {
                    phoneBattery = (int) ((level / (float) scale) * 100);
                }
                phoneIsCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING || plugged != 0);
                if (temp > 0) {
                    phoneTemp = temp / 10.0f;
                }
            }
        } catch (Exception ignored) {}
    }

    private void sendPing() {
        updatePhoneBatteryStats();
        JSONObject ping = BridgeProtocol.createPing(phoneBattery, phoneIsCharging);
        sendToWatch(ping);
    }

    public static void sendToWatch(JSONObject obj) {
        new Thread(() -> {
            synchronized (lock) {
                if (activeOut != null) {
                    try {
                        byte[] bytes = obj.toString().getBytes("UTF-8");
                        DataOutputStream dos = new DataOutputStream(activeOut);
                        dos.writeInt(bytes.length);
                        dos.write(bytes);
                        dos.flush();
                    } catch (Exception e) {
                        Log.e(TAG, "sendToWatch error", e);
                    }
                }
            }
        }).start();
    }

    private void updateStatus(String status) {
        Notification notif = buildNotification("HyperBridge", status);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(2001, notif);
        sendBroadcast(new Intent("com.kaiserdev.hyperbridge.UPDATE_PHONE_UI"));
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        running = false;
        instance = null;
        if (sensorManager != null && stepListener != null) {
            sensorManager.unregisterListener(stepListener);
        }
        if (activeGatt != null) {
            try { activeGatt.close(); } catch (Exception ignored) {}
            activeGatt = null;
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
