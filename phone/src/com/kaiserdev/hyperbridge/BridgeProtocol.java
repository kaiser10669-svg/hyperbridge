package com.kaiserdev.hyperbridge;

import org.json.JSONObject;

public class BridgeProtocol {
    public static final String DEFAULT_BT_UUID = "00001101-0000-1000-8000-00805F9B34FB";
    public static final int TCP_PORT = 48899;

    public static final String TYPE_PING                = "PING";
    public static final String TYPE_PONG                = "PONG";
    public static final String TYPE_TELEMETRY           = "TELEMETRY";
    public static final String TYPE_FIND_WATCH          = "FIND_WATCH";
    public static final String TYPE_LAUNCH_APP          = "LAUNCH_APP";
    public static final String TYPE_NOTIFICATION        = "NOTIFICATION";
    public static final String TYPE_NOTIFICATION_ACTION = "NOTIF_ACTION";
    public static final String TYPE_CALL_INCOMING       = "CALL_INCOMING";
    public static final String TYPE_CALL_ENDED          = "CALL_ENDED";
    public static final String TYPE_CALL_ACTION         = "CALL_ACTION";

    public static JSONObject createPing(int battery, boolean isCharging) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TYPE_PING);
            obj.put("battery", battery);
            obj.put("isCharging", isCharging);
            obj.put("time", System.currentTimeMillis());
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject createPong(int battery, boolean isCharging, int heartRate, int steps) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TYPE_PONG);
            obj.put("battery", battery);
            obj.put("isCharging", isCharging);
            obj.put("heartRate", heartRate);
            obj.put("steps", steps);
            obj.put("time", System.currentTimeMillis());
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject createTelemetry(int battery, boolean isCharging, int heartRate, int steps) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TYPE_TELEMETRY);
            obj.put("battery", battery);
            obj.put("isCharging", isCharging);
            obj.put("heartRate", heartRate);
            obj.put("steps", steps);
            obj.put("time", System.currentTimeMillis());
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject createFindWatch() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TYPE_FIND_WATCH);
            obj.put("time", System.currentTimeMillis());
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject createLaunchApp(String pkg) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TYPE_LAUNCH_APP);
            obj.put("package", pkg);
            obj.put("time", System.currentTimeMillis());
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject createNotification(long id, String pkg, String appName, String title, String text, String color, boolean canReply) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TYPE_NOTIFICATION);
            obj.put("id", id);
            obj.put("package", pkg);
            obj.put("appName", appName);
            obj.put("title", title);
            obj.put("text", text);
            obj.put("color", color);
            obj.put("canReply", canReply);
            obj.put("time", System.currentTimeMillis());
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public static JSONObject createIncomingCall(String number, String name) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("type", TYPE_CALL_INCOMING);
            obj.put("number", number);
            obj.put("name", name);
            obj.put("time", System.currentTimeMillis());
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }
}
