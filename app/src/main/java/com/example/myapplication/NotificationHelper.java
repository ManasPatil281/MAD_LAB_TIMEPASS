package com.example.myapplication;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * Helper utility to display in-app notifications when reports are generated.
 * Uses a dedicated channel separate from the sensor notification channel.
 */
public class NotificationHelper {

    private static final String CHANNEL_ID = "ScholarMateReportsChannel";
    private static final String CHANNEL_NAME = "Report Notifications";
    private static final String CHANNEL_DESC = "Notifications for generated plagiarism reports and paper reviews";

    private static int notificationId = 2000;

    /**
     * Show a notification when a plagiarism report is generated.
     *
     * @param context  Application context
     * @param fileName Name of the analyzed file
     * @param score    Plagiarism score percentage
     * @param severity Severity level string (e.g., "Low", "Moderate", "High")
     */
    public static void showPlagiarismReportNotification(Context context, String fileName, double score, String severity) {
        createChannel(context);

        String title = "📋 Plagiarism Report Ready";
        String body = String.format("%s — Score: %.1f%% (%s risk)", fileName, score, severity);

        // Route through HomeActivity to ensure session/context is valid
        Intent resultIntent = new Intent(context, PlagiarismDetectionActivity.class);
        resultIntent.putExtra("open_latest", true);
        
        android.app.TaskStackBuilder stackBuilder = android.app.TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(new Intent(context, HomeActivity.class));
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent pendingIntent = stackBuilder.getPendingIntent(
                notificationId, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(notificationId++, builder.build());
        }
    }

    /**
     * Show a notification when a paper review is generated.
     *
     * @param context  Application context
     * @param fileName Name of the reviewed file
     * @param score    Overall review score (out of 10)
     * @param verdict  Verdict string (e.g., "Excellent Work", "Good Work")
     */
    public static void showPaperReviewNotification(Context context, String fileName, double score, String verdict) {
        createChannel(context);

        String title = "📝 Paper Review Complete";
        String body = String.format("%s — Score: %.1f/10 (%s)", fileName, score, verdict);

        Intent resultIntent = new Intent(context, PaperReviewActivity.class);
        resultIntent.putExtra("open_latest", true);
        
        android.app.TaskStackBuilder stackBuilder = android.app.TaskStackBuilder.create(context);
        stackBuilder.addNextIntentWithParentStack(new Intent(context, HomeActivity.class));
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent pendingIntent = stackBuilder.getPendingIntent(
                notificationId + 1, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(notificationId++, builder.build());
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
