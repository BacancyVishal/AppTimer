package com.sapianz.apptimer;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.rvalerio.fgchecker.AppChecker;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FloatService extends Service {
    private WindowManager windowManager;
    TextView hour, minute, thour, tminute;
    ProgressBar progressBar;
    WindowManager.LayoutParams layoutParams;
    AppChecker appChecker;
    String currentPackage = "";
    Long packageDuration = 0L;
    Long totalUsageTime = 0L;
    SQLiteDatabase db;
    LayoutInflater layoutInflater;
    View floaterView;
    HashMap<String, Long> packageUsageTime;
    DataUtils dataUtils;
    int counter = 0;
    PowerManager powerManager;
    int floaterXPos, floaterYPos;
    private static final String CHANNEL_DEFAULT_IMPORTANCE = "AppTimer";
    private static final String CHANNEL_ID = "11";
    private static final int ONGOING_NOTIFICATION_ID = 12;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate() {
        super.onCreate();

        dataUtils = new DataUtils(getApplicationContext());
        dataUtils.refreshDatabase();

        final HashMap<String, Integer> appTimerSettings = dataUtils.getSettings();
        final Long targetUsage = TimeUnit.HOURS.toMillis((long) appTimerSettings.get("hours"))
                + TimeUnit.MINUTES.toMillis((long) appTimerSettings.get("minutes"));

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        AppTimerDbHelper dbHelper = new AppTimerDbHelper(this);
        db = dbHelper.getReadableDatabase();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        layoutInflater = LayoutInflater.from(getApplicationContext());
        floaterView = layoutInflater.inflate(R.layout.floater, null);
        hour = floaterView.findViewById(R.id.hours);
        minute = floaterView.findViewById(R.id.minutes);
        thour = floaterView.findViewById(R.id.thours);
        tminute = floaterView.findViewById(R.id.tminutes);
        progressBar = floaterView.findViewById(R.id.progressBar);
        floaterView.setVisibility(View.GONE);
        updateUsageTime();

        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_TOAST;
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        layoutParams.x = appTimerSettings.get("floaterXPosition");
        layoutParams.y = appTimerSettings.get("floaterYPosition");
        floaterXPos = layoutParams.x;
        floaterYPos = layoutParams.y;

        floaterView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (Math.abs(layoutParams.y - initialY) < 10) {
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        }
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        layoutParams.x = initialX
                                + (int) (event.getRawX() - initialTouchX);
                        layoutParams.y = initialY
                                + (int) (event.getRawY() - initialTouchY);

                        if (Math.abs(layoutParams.x - initialX) >= 10 || Math.abs(layoutParams.y - initialY) >= 10) {
                            windowManager.updateViewLayout(floaterView, layoutParams);
                            floaterXPos = layoutParams.x;
                            floaterYPos = layoutParams.y;
                        }

                        return true;
                }
                return false;
            }
        });

        windowManager.addView(floaterView, layoutParams);

        appChecker = new AppChecker();
        appChecker
                .whenAny(new AppChecker.Listener() {
                    @Override
                    public void onForeground(String packageName) {
                        PackageManager pm = getPackageManager();
                        if (pm.getLaunchIntentForPackage(packageName) != null
                                && powerManager.isInteractive()
                                && !packageName.equals(getApplicationContext().getPackageName())) {
                            totalUsageTime += 1000;
                            counter++;
                            floaterView.setVisibility(View.VISIBLE);

                            if (!packageName.equals(currentPackage)) {
                                currentPackage = packageName;
                                counter = 0;
                                dataUtils.refreshDatabase();
                                updateUsageTime();
                                packageDuration = packageUsageTime.get(packageName);
                                if (packageDuration == null) {
                                    packageDuration = 0L;
                                }
                            } else {
                                packageDuration += 1000L;
                                packageUsageTime.put(packageName, packageDuration);
                            }

                            long hours = TimeUnit.MILLISECONDS.toHours(packageDuration);
                            long minutes = TimeUnit.MILLISECONDS.toMinutes(packageDuration - TimeUnit.HOURS.toMillis(hours));
                            long thours = TimeUnit.MILLISECONDS.toHours(totalUsageTime);
                            long tminutes = TimeUnit.MILLISECONDS.toMinutes(totalUsageTime - TimeUnit.HOURS.toMillis(thours));

                            hour.setText(String.format(Locale.getDefault(), "%s", Long.toString(hours)));
                            minute.setText(String.format(Locale.getDefault(), "%02d", minutes));
                            thour.setText(String.format(Locale.getDefault(), "%s", Long.toString(thours)));
                            tminute.setText(String.format(Locale.getDefault(), "%02d", tminutes));

                            if (totalUsageTime > targetUsage
                                    && counter % 10 == 0
                                    && counter != 0
                                    && appTimerSettings.get("nag") == 1) {
                                Intent intent = new Intent(getApplicationContext(), OveruseAlertActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            }
                        } else {
                            floaterView.setVisibility(View.GONE);
                        }
                        progressBar.setProgress((int) ((double) totalUsageTime / targetUsage * 100));
                    }
                })
                .timeout(1000)
                .start(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floaterView != null) windowManager.removeView(floaterView);
        appChecker.stop();
        dataUtils.saveFloaterPosition(floaterXPos, floaterYPos);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public void updateUsageTime() {
        HashMap<String, Long> packageDurations = new HashMap<>();
        Calendar currentTime = Calendar.getInstance();

        String date = String.format(Locale.getDefault(), "%04d", currentTime.get(Calendar.YEAR))
                + String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.MONTH) + 1)
                + String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.DAY_OF_MONTH));
        String[] projection = new String[]{
                AppTimerContract.ApplicationLogSummary.COLUMN_NAME_PACKAGE_NAME,
                AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION
        };
        String selection = AppTimerContract.ApplicationLogSummary.COLUMN_NAME_DATE + " = ?";
        String[] selectionArgs = {date};

        Cursor cursor = db.query(
                AppTimerContract.ApplicationLogSummary.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                null
        );

        totalUsageTime = 0L;

        while (cursor.moveToNext()) {
            String packageName = cursor.getString(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_PACKAGE_NAME));
            long duration = cursor.getLong(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION));

            if (packageDurations.get(packageName) == null) {
                packageDurations.put(packageName, duration);
            } else {
                packageDurations.put(packageName, duration + packageDurations.get(packageName));
            }

            totalUsageTime += duration;
        }

        cursor.close();

        packageUsageTime = packageDurations;
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            String description = "AppTimer notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_DEFAULT_IMPORTANCE, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(this, 0, notificationIntent, 0);

        createNotificationChannel();

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle("AppTimer")
                        .setContentText("AppTimer is running.")
                        .setSmallIcon(R.drawable.ic_notif)
                        .setContentIntent(pendingIntent)
                        .setTicker("AppTimer is running")
                        .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            startForeground(ONGOING_NOTIFICATION_ID, notification);
        } else
            startService(intent);

        return START_STICKY;
    }
}
