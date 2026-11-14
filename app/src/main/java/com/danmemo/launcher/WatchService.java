package com.danmemo.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** Waits (up to 30 minutes) for the server to start accepting players, then posts a notification if the app is in the background. */
public class WatchService extends Service {
    static final String WATCH = "dm_watch", READY = "dm_ready";
    volatile boolean running = false;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(new NotificationChannel(WATCH, "Waiting for the server", NotificationManager.IMPORTANCE_LOW));
            nm.createNotificationChannel(new NotificationChannel(READY, "Server ready", NotificationManager.IMPORTANCE_HIGH));
        }
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, WATCH) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("DanMemoRe").setContentText("Waiting for the server to start...");
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(41, b.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            else startForeground(41, b.build());
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!running) {
            running = true;
            new Thread(this::loop).start();
        }
        return START_NOT_STICKY;
    }

    boolean listening() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(MainActivity.URL + "api/status").openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(800);
            if (c.getResponseCode() != 200) return false;
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
                String l;
                while ((l = r.readLine()) != null) sb.append(l);
            }
            c.disconnect();
            return sb.toString().replace(" ", "").contains("\"listening\":true");
        } catch (Exception e) {
            return false;
        }
    }

    void loop() {
        long end = System.currentTimeMillis() + 30 * 60 * 1000L;
        boolean seenDown = false;
        while (System.currentTimeMillis() < end) {
            boolean up = listening();
            if (!up) seenDown = true;
            if (up && seenDown) {
                if (!MainActivity.visible) ready();
                break;
            }
            if (up && !seenDown) break;   // already running when we started: nothing to announce
            try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
        }
        running = false;
        stopForeground(true);
        stopSelf();
    }

    void ready() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        Intent open = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, READY) : new Notification.Builder(this);
        b.setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("DanMemoRe server is running")
                .setContentText("You can open the game now.").setContentIntent(pi).setAutoCancel(true);
        nm.notify(42, b.build());
    }
}
