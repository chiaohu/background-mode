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

public class ForegroundService extends Service {

    public static final int NOTIFICATION_ID = -574543954;

    private static String NOTIFICATION_TITLE;

    private static final String NOTIFICATION_TEXT = "報價服務持續運作中...";
    private static final String NOTIFICATION_ICON = "icon";

    private final IBinder binder = new ForegroundBinder();
    private PowerManager.WakeLock wakeLock;

    // 新增動態通知的標題與內容變數
    private String dynamicTitle = NOTIFICATION_TITLE;
    private String dynamicText = NOTIFICATION_TEXT;

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
        Context context = getApplicationContext();
        NOTIFICATION_TITLE = context.getString(context.getApplicationInfo().labelRes);
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

        wakeLock = pm.newWakeLock(
                PARTIAL_WAKE_LOCK, "backgroundmode:wakelock");

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

        String title = dynamicTitle != null ? dynamicTitle : settings.optString("title", NOTIFICATION_TITLE);
        String text = dynamicText != null ? dynamicText : settings.optString("text", NOTIFICATION_TEXT);

        Context context = getApplicationContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());

        if (intent == null) {
            intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName()); // 替換為你的主頁
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder notification = new Notification.Builder(context)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSmallIcon(getIconResId(settings))
                .setContentIntent(contentIntent);

        if (Build.VERSION.SDK_INT >= 26) {
            notification.setChannelId(CHANNEL_ID);
        }

        return notification.build();
    }

    protected void updateNotificationContent(String newTitle, String newText) {
        this.dynamicTitle = newTitle;
        this.dynamicText = newText;

        Notification notification = makeNotification();
        getNotificationManager().notify(NOTIFICATION_ID, notification);
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