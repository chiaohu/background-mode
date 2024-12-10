package de.einfachhans.BackgroundMode;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
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
    private static String NOTIFICATION_TITLE;
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
        if (intent != null) {
            String action = intent.getAction();
            if ("TEST_NOTIFICATION_CLICK".equals(action)) {
                Log.e("TestForegroundService", "Notification clicked!");
            } else {
                Log.e("TestForegroundService", "Service started with action: " + action);
            }
        } else {
            Log.e("TestForegroundService", "Service started with null intent.");
        }
        return START_STICKY;
    }

    public class TestForegroundService extends Service {

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("TestForegroundService", "onStartCommand triggered");
        if (intent != null) {
            Log.e("TestForegroundService", "Action received: " + intent.getAction());
            if ("TEST_NOTIFICATION_CLICK".equals(intent.getAction())) {
                Log.e("TestForegroundService", "Notification clicked!");
            }
        } else {
            Log.e("TestForegroundService", "Service started with null intent.");
        }
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("TestForegroundService", "Service created");
        showTestNotification();
    }

    private void showTestNotification() {
        Log.e("TestForegroundService", "Preparing notification");
        String CHANNEL_ID = "test_channel_id";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Test Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            getNotificationManager().createNotificationChannel(channel);
            Log.e("TestForegroundService", "Notification channel created");
        }

        Intent intent = new Intent(this, TestForegroundService.class);
        intent.setAction("TEST_NOTIFICATION_CLICK");

        PendingIntent pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Test Notification")
            .setContentText("Click to test notification.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();

        Log.e("TestForegroundService", "Notification created");
        startForeground(1, notification);
        Log.e("TestForegroundService", "Foreground service started");
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e("TestForegroundService", "onBind triggered");
        return null;
    }
}

    private void showTestNotification() {
        String CHANNEL_ID = "test_channel_id";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Test Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            getNotificationManager().createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, ForegroundService.class);
        intent.setAction("TEST_NOTIFICATION_CLICK");

        PendingIntent pendingIntent = PendingIntent.getService(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Test Notification")
            .setContentText("Click me to test.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();

        getNotificationManager().notify(1001, notification);
    }

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
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

        Context context = getApplicationContext();
        Intent intent = new Intent(context, ForegroundService.class);
        intent.setAction("NOTIFICATION_CLICKED"); // 设置点击动作的标志

        PendingIntent contentIntent = PendingIntent.getService(
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
                .setContentIntent(contentIntent); // 点击通知后触发服务

        if (Build.VERSION.SDK_INT >= 26) {
            notification.setChannelId(CHANNEL_ID);
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

    private NotificationManager getNotificationManager() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
}