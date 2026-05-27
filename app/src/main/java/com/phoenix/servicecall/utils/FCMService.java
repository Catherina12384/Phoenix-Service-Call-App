package com.phoenix.servicecall.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.phoenix.servicecall.R;
import com.phoenix.servicecall.MainActivity;

/**
 * FCMService — handles incoming Firebase Cloud Messaging messages.
 *
 * Phase 1: Service registered, notification channels created.
 * Phase 4: Will handle task reminder payloads and open SnoozeActivity.
 */
public class FCMService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    // Notification channel IDs
    public static final String CHANNEL_REMINDERS = "task_reminders";
    public static final String CHANNEL_REPORTS   = "reports";

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        // Phase 4: Save token to Firestore for server-side targeting
        Log.d(TAG, "FCM token refreshed: " + token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        Log.d(TAG, "FCM message received from: " + remoteMessage.getFrom());

        String title = "Service Call";
        String body  = "You have a new notification";

        // Extract notification payload
        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body  = remoteMessage.getNotification().getBody();
        }

        // Extract data payload (used by task reminders in Phase 4)
        String taskId = remoteMessage.getData().get("taskId");

        showNotification(title, body, taskId);
    }

    /**
     * Display a system notification.
     * Phase 4 will open SnoozeActivity when taskId is present.
     */
    private void showNotification(String title, String body, String taskId) {
        NotificationManager manager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        createChannels(manager);

        // Tap intent — opens MainActivity (Phase 4 will open SnoozeActivity)
        Intent intent = new Intent(this, MainActivity.class);
        if (taskId != null) {
            intent.putExtra("taskId", taskId);
            intent.putExtra("openSnooze", true);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_REMINDERS)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent);

        manager.notify((int) System.currentTimeMillis(), builder.build());
    }

    /**
     * Create notification channels (required for Android 8.0+).
     * Safe to call multiple times — system ignores duplicate creates.
     */
    public static void createChannels(NotificationManager manager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Task reminder channel
            NotificationChannel remindersChannel = new NotificationChannel(
                    CHANNEL_REMINDERS,
                    "Task Reminders",
                    NotificationManager.IMPORTANCE_HIGH);
            remindersChannel.setDescription("Alerts when a task is overdue");
            remindersChannel.enableVibration(true);
            manager.createNotificationChannel(remindersChannel);

            // Reports channel
            NotificationChannel reportsChannel = new NotificationChannel(
                    CHANNEL_REPORTS,
                    "Reports",
                    NotificationManager.IMPORTANCE_DEFAULT);
            reportsChannel.setDescription("Scheduled report delivery notifications");
            manager.createNotificationChannel(reportsChannel);
        }
    }
}
