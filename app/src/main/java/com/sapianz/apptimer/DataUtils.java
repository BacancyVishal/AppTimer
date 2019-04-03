package com.sapianz.apptimer;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.provider.BaseColumns;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

class DataUtils {
    private SQLiteDatabase db;
    private AppTimerDbHelper dbHelper;
    private UsageStatsManager usageStatsManager;
    private Context context;

    DataUtils(Context context) {
        this.context = context;
        this.dbHelper = new AppTimerDbHelper(context);
        usageStatsManager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
    }

    void saveSettings(boolean showNag, int hourLimit, int minuteLimit) {
        Long usageLimit = TimeUnit.HOURS.toMillis(hourLimit)
                + TimeUnit.MINUTES.toMillis(minuteLimit);
        ContentValues contentValues = new ContentValues();
        contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_SHOW_NAG, showNag);
        contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_TARGET_USAGE_TIME, usageLimit);
        db = dbHelper.getWritableDatabase();

        db.update(
                AppTimerContract.AppTimerSettings.TABLE_NAME,
                contentValues,
                null, null
        );
    }

    void saveFloaterPosition(int x, int y) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_X, x);
        contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_Y, y);
        db = dbHelper.getWritableDatabase();

        db.update(
                AppTimerContract.AppTimerSettings.TABLE_NAME,
                contentValues,
                null, null
        );
    }

    HashMap getSettings() {
        int hours, minutes, nag, floaterXPosition, floaterYPosition;
        HashMap<String, Integer> h = new HashMap<>();

        String[] projection = new String[] {
                BaseColumns._ID,
                AppTimerContract.AppTimerSettings.COLUMN_NAME_SHOW_NAG,
                AppTimerContract.AppTimerSettings.COLUMN_NAME_TARGET_USAGE_TIME,
                AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_X,
                AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_Y
        };
        db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                AppTimerContract.AppTimerSettings.TABLE_NAME,
                projection,
                null, null, null, null, null
        );
        if (cursor.moveToFirst()) {
            long limit = cursor.getInt(cursor.getColumnIndexOrThrow(AppTimerContract.AppTimerSettings.COLUMN_NAME_TARGET_USAGE_TIME));
            hours = (int) TimeUnit.MILLISECONDS.toHours(limit);
            minutes = (int) TimeUnit.MILLISECONDS.toMinutes(limit - TimeUnit.HOURS.toMillis(hours));
            nag = cursor.getInt(cursor.getColumnIndexOrThrow(AppTimerContract.AppTimerSettings.COLUMN_NAME_SHOW_NAG));
            floaterXPosition = cursor.getInt(cursor.getColumnIndexOrThrow(AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_X));
            floaterYPosition = cursor.getInt(cursor.getColumnIndexOrThrow(AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_Y));
            h.put("hours", hours);
            h.put("minutes", minutes);
            h.put("nag", nag);
            h.put("floaterXPosition", floaterXPosition);
            h.put("floaterYPosition", floaterYPosition);
            cursor.close();
            return h;
        } else {
            cursor.close();
            return null;
        }
    }

    void refreshDatabase() {
        String[] projection = new String[] {
                BaseColumns._ID,
                AppTimerContract.AppTimerSettings.COLUMN_NAME_NEXT_EXTRACT_TIME
        };
        String[] selectionArgs;
        String selection;

        // get settings
        db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(
                AppTimerContract.AppTimerSettings.TABLE_NAME,
                projection,
                null, null, null, null, null
        );

        // get next extraction time from settings
        long next_extract_time = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1);
        if (cursor.moveToFirst()) {
            next_extract_time = Long.parseLong(cursor.getString(cursor.getColumnIndexOrThrow(AppTimerContract.AppTimerSettings.COLUMN_NAME_NEXT_EXTRACT_TIME)));
        } else {
            // we are working on a fresh database and need to setup settings and import data
            // for the last month
            ContentValues contentValues = new ContentValues();
            contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_NEXT_EXTRACT_TIME, next_extract_time);
            contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_TARGET_USAGE_TIME, 2*60*60*1000);
            contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_SHOW_NAG, true);
            contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_X, 0);
            contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_FLOATER_POSITION_Y, 300);
            db.insert(AppTimerContract.AppTimerSettings.TABLE_NAME, null, contentValues);
        }

        // delete all entries in the raw application log whose action time is later that the
        // next extraction time gotten from settings. This implies that the last database refresh
        // failed to complete successfully and "next extraction time" was not updated.
        db = dbHelper.getWritableDatabase();
        selection = AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TIME + " > ?";
        selectionArgs = new String[] {Long.toString(next_extract_time)};
        db.delete(AppTimerContract.ApplicationLogRaw.TABLE_NAME, selection, selectionArgs);

        long startTime = next_extract_time;
        long endTime = System.currentTimeMillis();

        UsageEvents usageEvents = usageStatsManager.queryEvents(
                startTime,
                endTime
        );

        // save each event to the raw application log
        long lastEventTime = startTime;
        while (usageEvents.hasNextEvent()){
            UsageEvents.Event event = new UsageEvents.Event();
            usageEvents.getNextEvent(event);
            String packageName = event.getPackageName();
            int actionType = event.getEventType();
            long eventTime = event.getTimeStamp();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(eventTime);

            PackageManager pm = this.context.getPackageManager();
            if(pm.getLaunchIntentForPackage(packageName) != null
                    & ((actionType == 1 & usageEvents.hasNextEvent()) || actionType == 2)
                    & eventTime > next_extract_time) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(AppTimerContract.ApplicationLogRaw.COLUMN_NAME_PACKAGE_NAME, packageName);
                contentValues.put(AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TIME, event.getTimeStamp());
                contentValues.put(AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TIME_HUMAN, cal.getTime().toString());
                contentValues.put(AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TYPE, actionType);
                db.insert(AppTimerContract.ApplicationLogRaw.TABLE_NAME, null, contentValues);
                lastEventTime = eventTime;
            }
        }

        // delete all entries in the application log whose start time is greater than
        // "next_extract_time"
        selection = AppTimerContract.ApplicationLog.COLUMN_NAME_START_TIME + " > ?";
        selectionArgs = new String[] {Long.toString(next_extract_time)};
        db.delete(AppTimerContract.ApplicationLog.TABLE_NAME, selection, selectionArgs);

        ContentValues contentValues = new ContentValues();

        // summarize raw application log entries into one entry for every foregrounding of
        // an app in the application log
        projection = new String[] {
                BaseColumns._ID,
                AppTimerContract.ApplicationLogRaw.COLUMN_NAME_PACKAGE_NAME,
                AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TIME,
                AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TYPE
        };

        selection = AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TIME + " > ?";
        selectionArgs = new String[] {Long.toString(next_extract_time)};
        String orderBy = AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TIME + " ASC";

        cursor = db.query(
                AppTimerContract.ApplicationLogRaw.TABLE_NAME,
                projection,
                selection,
                selectionArgs,
                null,
                null,
                orderBy
        );

        Long lastAddedId = null;
        Long lastActionTime = null;

        while(cursor.moveToNext()) {
            int actionType = cursor.getInt(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TYPE));
            long actionTime = cursor.getLong(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogRaw.COLUMN_NAME_ACTION_TIME));
            String packageName = cursor.getString(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogRaw.COLUMN_NAME_PACKAGE_NAME));
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(actionTime);

            if (actionType == 1) {
                contentValues.clear();
                contentValues.put(AppTimerContract.ApplicationLog.COLUMN_NAME_PACKAGE_NAME, packageName);
                contentValues.put(AppTimerContract.ApplicationLog.COLUMN_NAME_START_TIME, actionTime);
                lastActionTime = actionTime;

                lastAddedId = db.insert(
                        AppTimerContract.ApplicationLog.TABLE_NAME,
                        null,
                        contentValues
                );
            }
            if (actionType == 2 & lastAddedId != null) {
                contentValues.clear();

                selection = "_ID = ?";
                selectionArgs = new String[] {Long.toString(lastAddedId)};
                contentValues.put(AppTimerContract.ApplicationLog.COLUMN_NAME_END_TIME, actionTime);
                contentValues.put(AppTimerContract.ApplicationLog.COLUMN_NAME_DURATION, actionTime - lastActionTime);

                db.update(
                        AppTimerContract.ApplicationLog.TABLE_NAME,
                        contentValues,
                        selection,
                        selectionArgs
                );
            }
        }

        db.beginTransaction();

        try {
            projection = new String[] {
                    BaseColumns._ID,
                    AppTimerContract.ApplicationLog.COLUMN_NAME_PACKAGE_NAME,
                    AppTimerContract.ApplicationLog.COLUMN_NAME_START_TIME,
                    AppTimerContract.ApplicationLog.COLUMN_NAME_END_TIME,
                    AppTimerContract.ApplicationLog.COLUMN_NAME_DURATION
            };

            selection = AppTimerContract.ApplicationLog.COLUMN_NAME_START_TIME + " > ? AND "
                    + AppTimerContract.ApplicationLog.COLUMN_NAME_END_TIME + " IS NOT NULL";
            selectionArgs = new String[] {Long.toString(next_extract_time)};

            cursor = db.query(
                    AppTimerContract.ApplicationLog.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            while (cursor.moveToNext()) {
                startTime = cursor.getLong(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLog.COLUMN_NAME_START_TIME));
                endTime = cursor.getLong(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLog.COLUMN_NAME_END_TIME));
                String packageName = cursor.getString(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLog.COLUMN_NAME_PACKAGE_NAME));

                ArrayList<HashMap> al = getSlotSplit(startTime, endTime);

                for (int i = 0; i < al.size(); i++) {
                    String currentSlot = (String)al.get(i).get("slot");
                    long currentDuration = Long.parseLong((String) Objects.requireNonNull(al.get(i).get("duration")));
                    int slotNumber = Integer.parseInt((String) Objects.requireNonNull(al.get(i).get("slot-number")));
                    String currentDate = (String)al.get(i).get("date");

                    projection = new String[] {
                            AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION
                    };
                    selection = AppTimerContract.ApplicationLogSummary.COLUMN_NAME_PACKAGE_NAME + " = ? AND "
                            + AppTimerContract.ApplicationLogSummary.COLUMN_NAME_DATE + " = ? AND "
                            + AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT + " = ?";
                    selectionArgs = new String[] {
                            packageName,
                            currentDate,
                            currentSlot
                    };
                    Cursor cursor2 = db.query(
                            AppTimerContract.ApplicationLogSummary.TABLE_NAME,
                            projection,
                            selection,
                            selectionArgs,
                            null, null, null
                    );
                    double TOTAL_MILLIS = 600000;
                    if (cursor2.getCount() == 1) {
                        cursor2.moveToFirst();
                        long duration2 = cursor2.getLong(cursor2.getColumnIndexOrThrow(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION));
                        contentValues.clear();
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION, duration2 + currentDuration);
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_PERCENTAGE, (currentDuration + duration2) / TOTAL_MILLIS * 100);
                        db.update(
                                AppTimerContract.ApplicationLogSummary.TABLE_NAME,
                                contentValues,
                                selection,
                                selectionArgs
                        );
                    } else {
                        contentValues.clear();
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_PACKAGE_NAME, packageName);
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT, currentSlot);
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION, currentDuration);
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_DATE, currentDate);
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_PERCENTAGE, currentDuration / TOTAL_MILLIS * 100);
                        contentValues.put(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_NUMBER, slotNumber);
                        db.insert(
                                AppTimerContract.ApplicationLogSummary.TABLE_NAME,
                                null,
                                contentValues
                        );
                    }
                    cursor2.close();
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }

        // the refresh process is done and the next extract time can be saved to the database
        // to be used on the next refresh
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(lastEventTime);
        contentValues.clear();
        contentValues.put(AppTimerContract.AppTimerSettings.COLUMN_NAME_NEXT_EXTRACT_TIME, lastEventTime);
        db.update(
                AppTimerContract.AppTimerSettings.TABLE_NAME,
                contentValues,
                null, null
        );

        cursor.close();
    }

    private ArrayList<HashMap> getSlotSplit(long startTime, long endTime) {
        Calendar currentTime = Calendar.getInstance();
        Calendar previousTime;
        currentTime.setTimeZone(TimeZone.getDefault());
        currentTime.setTimeInMillis(startTime);
        currentTime.set(Calendar.MINUTE, 0);
        currentTime.set(Calendar.SECOND, 0);
        currentTime.set(Calendar.MILLISECOND, 0);
        Calendar beginTime;
        ArrayList<HashMap> split = new ArrayList<>();

        while (true) {
            previousTime = (Calendar)currentTime.clone();
            previousTime.setTimeZone(TimeZone.getDefault());
            currentTime.setTimeInMillis(currentTime.getTimeInMillis() + TimeUnit.MINUTES.toMillis(10));
            HashMap<String, String> hm = new HashMap<>();
            String slotStart = String.format(Locale.getDefault(), "%02d", previousTime.get(Calendar.HOUR_OF_DAY)) + String.format(Locale.getDefault(), "%02d", previousTime.get(Calendar.MINUTE));
            String slotEnd = String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.HOUR_OF_DAY)) + String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.MINUTE));
            String date = String.format(Locale.getDefault(), "%04d", previousTime.get(Calendar.YEAR))
                    + String.format(Locale.getDefault(), "%02d", previousTime.get(Calendar.MONTH)+1)
                    + String.format(Locale.getDefault(), "%02d", previousTime.get(Calendar.DAY_OF_MONTH));
            beginTime = (Calendar)previousTime.clone();
            beginTime.set(Calendar.HOUR_OF_DAY, 0);
            beginTime.set(Calendar.MINUTE, 0);
            beginTime.set(Calendar.SECOND, 0);
            beginTime.set(Calendar.MILLISECOND, 0);
            long diff = TimeUnit.MILLISECONDS.toMinutes(currentTime.getTimeInMillis() - beginTime.getTimeInMillis()) / 10;
            hm.put("slot-number", Long.toString(diff));
            hm.put("slot", slotStart + "-" + slotEnd);
            hm.put("date", date);

            if (startTime >= previousTime.getTimeInMillis() & startTime <= currentTime.getTimeInMillis()) {
                if (endTime <= currentTime.getTimeInMillis()) {
                    hm.put("duration", Long.toString(endTime - startTime));
                    split.add(hm);
                    break;
                } else {
                    hm.put("duration", Long.toString(currentTime.getTimeInMillis() - startTime));
                    split.add(hm);
                }
            }

            if (startTime < previousTime.getTimeInMillis() & endTime <= currentTime.getTimeInMillis()) {
                hm.put("duration", Long.toString(endTime - previousTime.getTimeInMillis()));
                split.add(hm);
                break;
            }

            if (startTime < previousTime.getTimeInMillis() & endTime > currentTime.getTimeInMillis()) {
                hm.put("duration", Long.toString(currentTime.getTimeInMillis() - previousTime.getTimeInMillis()));
                split.add(hm);
            }
        }

        return split;
    }

    ArrayList<HashMap> getTopApps() {
        HashMap<String, Long> hashMap = new HashMap<>();
        Calendar currentTime = Calendar.getInstance();
        ArrayList<HashMap> topAppsList = new ArrayList<>();

        String date = String.format(Locale.getDefault(), "%04d", currentTime.get(Calendar.YEAR))
                + String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.MONTH)+1)
                + String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.DAY_OF_MONTH));
        String[] projection = {
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

        while (cursor.moveToNext()) {
            String packageName = cursor.getString(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_PACKAGE_NAME));
            long incrementalDuration = cursor.getLong(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION));
            long currentDuration = hashMap.containsKey(packageName) ? hashMap.get(packageName) : 0;
            hashMap.put(packageName, currentDuration + incrementalDuration);
        }

        while(!hashMap.isEmpty()) {
            HashMap.Entry<String, Long> maxEntry = null;
            for (HashMap.Entry<String, Long> entry : hashMap.entrySet()) {
                if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) > 0) {
                    maxEntry = entry;
                }
            }
            HashMap<String, Long> maxMap = new HashMap<>();
            maxMap.put(maxEntry.getKey(), maxEntry.getValue());
            topAppsList.add(maxMap);
            if (topAppsList.size() == 5) {
                HashMap<String, Long> emptyMap = new HashMap<>();
                emptyMap.put("adview", 0L);
                topAppsList.add(emptyMap);
            }
            hashMap.remove(maxEntry.getKey());
        }

        cursor.close();

        if (topAppsList.size() < 5) {
            HashMap<String, Long> emptyMap = new HashMap<>();
            emptyMap.put("adview", 0L);
            topAppsList.add(emptyMap);
        }

        return topAppsList;
    }
}
