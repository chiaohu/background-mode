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

    // Fixed ID for the 'foreground' notification
    public static final int NOTIFICATION_ID = -574543954;

    // Default title of the background notification
    private static String NOTIFICATION_TITLE;

    // Default text of the background notification
    private static final String NOTIFICATION_TEXT =
            "報價服務持續運作中...";

    // Default icon of the background notification
    private static final String NOTIFICATION_ICON = "icon";

    // Binder given to clients
    private final IBinder binder = new ForegroundBinder();

    // Partial wake lock to prevent the app from going to sleep when locked
    private PowerManager.WakeLock wakeLock;

    /**
     * Allow clients to call on to the service.
     */
    @Override
    public IBinder onBind (Intent intent) {
        return binder;
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    class ForegroundBinder extends Binder
    {
        ForegroundService getService()
        {
            // Return this instance of ForegroundService
            // so clients can call public methods
            return ForegroundService.this;
        }
    }

    /**
     * Put the service in a foreground state to prevent app from being killed
     * by the OS.
     */
    @Override
    public void onCreate()
    {
        super.onCreate();
        // Dynamically fetch app name as notification title
        Context context = getApplicationContext();
        NOTIFICATION_TITLE = context.getString(context.getApplicationInfo().labelRes);
        keepAwake();
    }

    /**
     * No need to run headless on destroy.
     */
    @Override
    public void onDestroy()
    {
        super.onDestroy();
        sleepWell();
    }

    /**
     * Prevent Android from stopping the background service automatically.
     */
    @Override
    public int onStartCommand (Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    /**
     * Put the service in a foreground state to prevent app from being killed
     * by the OS.
     */
    @SuppressLint("WakelockTimeout")
    private void keepAwake()
    {
        JSONObject settings = BackgroundMode.getSettings();
        boolean isSilent    = settings.optBoolean("silent", false);

        if (!isSilent) {
            startForeground(NOTIFICATION_ID, makeNotification());
        }

        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);

        wakeLock = pm.newWakeLock(
                PARTIAL_WAKE_LOCK, "backgroundmode:wakelock");

        wakeLock.acquire();
    }

    /**
     * Stop background mode.
     */
    private void sleepWell()
    {
        stopForeground(true);
        getNotificationManager().cancel(NOTIFICATION_ID);

        if (wakeLock != null) {
            wakeLock.release();
            wakeLock = null;
        }
    }

    /**
     * Create a notification as the visible part to be able to put the service
     * in a foreground state by using the default settings.
     */
    private Notification makeNotification()
    {
        return makeNotification(BackgroundMode.getSettings());
    }

    /**
     * Create a notification as the visible part to be able to put the service
     * in a foreground state.
     *
     * @param settings The config settings
     */
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
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (intent == null) {
            // 如果無法取得啟動頁 Intent，提供一個默認的 Intent
            intent = new Intent(context, MainActivity.class); // 替換 MainActivity 為你的主頁 Activity
        }

        // 確保 Intent 啟動 APP 並清除多餘的 Activity 堆疊
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
                .setContentIntent(contentIntent); // 點擊通知後執行

        if (Build.VERSION.SDK_INT >= 26) {
            notification.setChannelId(CHANNEL_ID);
        }

        return notification.build();
    }

    /**
     * Update the notification.
     *
     * @param settings The config settings
     */
    protected void updateNotification (JSONObject settings)
    {
        boolean isSilent = settings.optBoolean("silent", false);

        if (isSilent) {
            stopForeground(true);
            return;
        }

        Notification notification = makeNotification(settings);
        getNotificationManager().notify(NOTIFICATION_ID, notification);

    }

    /**
     * Retrieves the resource ID of the app icon.
     *
     * @param settings A JSON dict containing the icon name.
     */
    private int getIconResId (JSONObject settings)
    {
        String icon = settings.optString("icon", NOTIFICATION_ICON);

        int resId = getIconResId(icon, "mipmap");

        if (resId == 0) {
            resId = getIconResId(icon, "drawable");
        }

        return resId;
    }

    /**
     * Retrieve resource id of the specified icon.
     *
     * @param icon The name of the icon.
     * @param type The resource type where to look for.
     *
     * @return The resource id or 0 if not found.
     */
    private int getIconResId (String icon, String type)
    {
        Resources res  = getResources();
        String pkgName = getPackageName();

        int resId = res.getIdentifier(icon, type, pkgName);

        if (resId == 0) {
            resId = res.getIdentifier("icon", type, pkgName);
        }

        return resId;
    }

    /**
     * Set notification color if its supported by the SDK.
     *
     * @param notification A Notification.Builder instance
     * @param settings A JSON dict containing the color definition (red: FF0000)
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void setColor (Notification.Builder notification, JSONObject settings)
    {

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

    /**
     * Returns the shared notification service manager.
     */
    private NotificationManager getNotificationManager()
    {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }
}
