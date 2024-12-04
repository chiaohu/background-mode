package de.einfachhans.BackgroundMode;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import org.json.JSONObject;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

public class ForegroundService extends Service {

    public static final int NOTIFICATION_ID = -574543954;

    private String NOTIFICATION_TITLE;
    private static final String NOTIFICATION_TEXT = "報價服務持續運作中...";
    private static final String NOTIFICATION_ICON = "icon";

    private final IBinder binder = new ForegroundBinder();
    private PowerManager.WakeLock wakeLock;

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    class ForegroundBinder extends Binder {
        ForegroundService getService() {
            return ForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NOTIFICATION_TITLE = getAppName(getApplicationContext());
        keepAwake();
    }

    private String getAppName(Context context) {
        int stringId = context.getApplicationInfo().labelRes;
        return context.getString(stringId);
    }

    @SuppressLint("WakelockTimeout")
    private void keepAwake() {
        JSONObject settings = BackgroundMode.getSettings();
        boolean isSilent = settings.optBoolean("silent", false);

        if (!isSilent) {
            startForeground(NOTIFICATION_ID, makeNotification());
        }

        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PARTIAL_WAKE_LOCK, "backgroundmode:wakelock");
        wakeLock.acquire();
    }

    private Notification makeNotification(JSONObject settings) {
        String title = settings.optString("title", NOTIFICATION_TITLE);
        String text = settings.optString("text", NOTIFICATION_TEXT);

        Context context = getApplicationContext();
        String pkgName = context.getPackageName();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);

        Notification.Builder notification = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSmallIcon(getIconResId(settings));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notification.setChannelId("cordova-plugin-background-mode-id");
        }

        if (intent != null && settings.optBoolean("resume")) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT
            );
            notification.setContentIntent(contentIntent);
        }

        return notification.build();
    }

    private int getIconResId(JSONObject settings) {
        String icon = settings.optString("icon", NOTIFICATION_ICON);
        int resId = getIconResId(icon, "mipmap");

        if (resId == 0) {
            resId = getIconResId(icon, "drawable");
        }

        return resId;
    }

    private int getIconResId(String icon, String type) {
        Resources res = getResources();
        String pkgName = getPackageName();
        int resId = res.getIdentifier(icon, type, pkgName);

        if (resId == 0) {
            resId = res.getIdentifier("icon", type, pkgName);
        }

        return resId;
    }
}