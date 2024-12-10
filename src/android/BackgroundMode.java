package de.einfachhans.BackgroundMode;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.PluginResult.Status;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import de.einfachhans.BackgroundMode.ForegroundService.ForegroundBinder;

import static android.content.Context.BIND_AUTO_CREATE;
import static de.einfachhans.BackgroundMode.BackgroundModeExt.clearKeyguardFlags;

public class BackgroundMode extends CordovaPlugin {

    private enum Event { ACTIVATE, DEACTIVATE, FAILURE }

    private static final String JS_NAMESPACE = "cordova.plugins.backgroundMode";

    private boolean inBackground = false;
    private boolean isDisabled = true;
    private boolean isBind = false;

    private static JSONObject defaultSettings = new JSONObject();
    private ForegroundService service;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            ForegroundBinder binder = (ForegroundBinder) service;
            BackgroundMode.this.service = binder.getService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            fireEvent(Event.FAILURE, "'service disconnected'");
        }
    };

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callback) throws JSONException {
        boolean validAction = true;

        switch (action) {
            case "configure":
                configure(args.optJSONObject(0), args.optBoolean(1));
                PluginResult res = new PluginResult(Status.OK);
                callback.sendPluginResult(res);
                break;

            case "enable":
                enableMode();
                break;

            case "disable":
                disableMode();
                break;

            case "updateNotificationContent":
                updateNotificationContent(args, callback);
                break;

            default:
                validAction = false;
        }

        if (validAction && !"updateNotificationContent".equals(action)) {
            callback.success();
        } else if (!validAction) {
            callback.error("Invalid action: " + action);
        }

        return validAction;
    }

    @Override
    public void onPause(boolean multitasking) {
        try {
            inBackground = true;
            startService();
        } finally {
            clearKeyguardFlags(cordova.getActivity());
        }
    }

    @Override
    public void onStop() {
        clearKeyguardFlags(cordova.getActivity());
    }

    @Override
    public void onResume(boolean multitasking) {
        inBackground = false;
        stopService();
    }

    @Override
    public void onDestroy() {
        stopService();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    private void enableMode() {
        isDisabled = false;

        if (inBackground) {
            startService();
        }
    }

    private void disableMode() {
        stopService();
        isDisabled = true;
    }

    private void configure(JSONObject settings, boolean update) {
        if (update) {
            updateNotification(settings);
        } else {
            setDefaultSettings(settings);
        }
    }

    private void setDefaultSettings(JSONObject settings) {
        defaultSettings = settings;
    }

    static JSONObject getSettings() {
        return defaultSettings;
    }

    private void updateNotification(JSONObject settings) {
        if (isBind) {
            service.updateNotification(settings);
        }
    }

    private void startService() {
        Activity context = cordova.getActivity();

        if (isDisabled || isBind)
            return;

        Intent intent = new Intent(context, ForegroundService.class);

        try {
            context.bindService(intent, connection, BIND_AUTO_CREATE);
            fireEvent(Event.ACTIVATE, null);
            context.startService(intent);
        } catch (Exception e) {
            fireEvent(Event.FAILURE, String.format("'%s'", e.getMessage()));
        }

        isBind = true;
    }

    private void stopService() {
        Activity context = cordova.getActivity();
        Intent intent = new Intent(context, ForegroundService.class);

        if (!isBind) return;

        fireEvent(Event.DEACTIVATE, null);
        context.unbindService(connection);
        context.stopService(intent);

        isBind = false;
    }

    private void fireEvent(Event event, String params) {
        String eventName = event.name().toLowerCase();
        Boolean active = event == Event.ACTIVATE;

        String str = String.format("%s._setActive(%b)", JS_NAMESPACE, active);
        str = String.format("%s;%s.on('%s', %s)", str, JS_NAMESPACE, eventName, params);
        str = String.format("%s;%s.fireEvent('%s',%s);", str, JS_NAMESPACE, eventName, params);

        final String js = str;

        cordova.getActivity().runOnUiThread(() -> webView.loadUrl("javascript:" + js));
    }

    private void updateNotificationContent(JSONArray args, CallbackContext callback) throws JSONException {
        if (!isBind || service == null) {
            callback.error("Service is not bound.");
            return;
        }

        JSONObject settings = args.optJSONObject(0);
        if (settings == null) {
            callback.error("Invalid parameters.");
            return;
        }

        String title = settings.optString("title", "Default Title");
        String text = settings.optString("text", "Default Text");

        service.updateNotificationContent(title, text);
        callback.success("Notification updated.");
    }
}