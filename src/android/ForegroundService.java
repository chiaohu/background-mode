package de.einfachhans.BackgroundMode;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
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
import android.app.NotificationChannel;

import org.json.JSONObject;

import static android.os.PowerManager.PARTIAL_WAKE_LOCK;

/**
 * Puts the service in a foreground state, where the system considers it to be
 * something the user is actively aware of and thus not a candidate for killing
 * when low on memory.
 */
public class ForegroundService extends Service {

    public static final int NOTIFICATION_ID = -574543954;
    private static final String NOTIFICATION_TITLE = "App is running in background";
    private static final String NOTIFICATION_TEXT = "Doing heavy tasks.";
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
        keepAwake();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sleepWell();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
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

    private void sleepWell() {
        stopForeground(true);
        getNotificationManager().cancel(NOTIFICATION_ID);

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    private Notification makeNotification() {
        return makeNotification(BackgroundMode.getSettings());
    }

    private Notification makeNotification(JSONObject settings) {
        String CHANNEL_ID = "cordova-plugin-background-mode-id";
        if (Build.VERSION.SDK_INT >= 26) {
            CharSequence name = "cordova-plugin-background-mode";
            String description = "cordova-plugin-background-mode notification";
            int importance = NotificationManager.IMPORTANCE_LOW;

            NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, name, importance);
            mChannel.setDescription(description);

            getNotificationManager().createNotificationChannel(mChannel);
        }

        String title = settings.optString("title", NOTIFICATION_TITLE);
        String text = settings.optString("text", NOTIFICATION_TEXT);
        Log.d("BackgroundMode", "Notification Title: " + title);
        Log.d("BackgroundMode", "Notification Text: " + text);

        Context context = getApplicationContext();
        String pkgName = context.getPackageName();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkgName);

        Notification.Builder notification = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= 26) {
            notification.setChannelId(CHANNEL_ID);
        }

        try {
            int iconResId = getIconResId(settings);
            notification.setSmallIcon(iconResId);
        } catch (Exception e) {
            Log.e("BackgroundMode", "Failed to set icon, using default", e);
            notification.setSmallIcon(android.R.drawable.sym_def_app_icon);
        }

        if (settings.optBoolean("hidden", true)) {
            notification.setPriority(Notification.PRIORITY_MIN);
        }

        if (text.contains("\n") || settings.optBoolean("bigText", false)) {
            notification.setStyle(new Notification.BigTextStyle().bigText(text));
        }

        if (intent != null && settings.optBoolean("resume")) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent contentIntent = PendingIntent.getActivity(
                    context, NOTIFICATION_ID, intent, PendingIntent.FLAG_UPDATE_CURRENT);

            notification.setContentIntent(contentIntent);
        }

        setColor(notification, settings);

        return notification.build();
    }

    protected void updateNotification(JSONObject settings) {
        boolean isSilent = settings.optBoolean("silent", false);

        if (isSilent) {
            stopForeground(true);
            return;
        }

        Notification notification = makeNotification(settings);
        getNotificationManager().notify(NOTIFICATION_ID, notification);
    }

    private int getIconResId(JSONObject settings) {
        String icon = settings.optString("icon", NOTIFICATION_ICON);

        if (icon.startsWith("mipmap://") || icon.startsWith("drawable://")) {
            icon = icon.split("://")[1];
        }

        Resources res = getResources();
        String pkgName = getPackageName();

        int resId = res.getIdentifier(icon, "mipmap", pkgName);

        if (resId == 0) {
            resId = res.getIdentifier(icon, "drawable", pkgName);
        }

        if (resId == 0) {
            Log.w("BackgroundMode", "Icon resource not found, using default");
            resId = android.R.drawable.sym_def_app_icon;
        }

        return resId;
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setColor(Notification.Builder notification, JSONObject settings) {
        String hex = settings.optString("color", null);

        if (Build.VERSION.SDK_INT < 21 || hex == null)
            return;

        try {
            int aRGB = Integer.parseInt(hex, 16) + 0xFF000000;
            notification.setColor(aRGB);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
}