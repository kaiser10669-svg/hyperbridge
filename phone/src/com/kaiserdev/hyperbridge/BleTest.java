package com.kaiserdev.hyperbridge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.os.Build;
import android.os.Looper;

public class BleTest {
    private static final String MAC = "B0:A3:F2:D7:CE:68";

    public static void main(String[] args) {
        System.out.println("=== STARTING BLE TEST FOR XIAOMI WATCH 5 (" + MAC + ") ===");
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) {
            System.out.println("Bluetooth adapter not available or disabled");
            return;
        }

        BluetoothDevice device = adapter.getRemoteDevice(MAC);
        System.out.println("Device Name: " + device.getName());
        System.out.println("Device Type: " + device.getType() + " (1=CLASSIC, 2=LE, 3=DUAL)");
        System.out.println("Bond State: " + device.getBondState() + " (10=NONE, 11=BONDING, 12=BONDED)");

        BluetoothLeScanner scanner = adapter.getBluetoothLeScanner();
        if (scanner != null) {
            System.out.println("Starting BLE Scanner for 6 seconds...");
            ScanSettings settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();

            scanner.startScan(null, settings, new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    BluetoothDevice d = result.getDevice();
                    if (MAC.equalsIgnoreCase(d.getAddress())) {
                        System.out.println(">>> FOUND WATCH IN BLE SCAN! RSSI: " + result.getRssi());
                        if (result.getScanRecord() != null) {
                            System.out.println("ScanRecord: " + result.getScanRecord().getDeviceName());
                        }
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    System.out.println("Scan failed: " + errorCode);
                }
            });
        }

        new Thread(() -> {
            try {
                Thread.sleep(7000);
                System.out.println("Test complete, exiting.");
                System.exit(0);
            } catch (Exception ignored) {}
        }).start();

        Looper.loop();
    }
}
