package com.sapianz.apptimer;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class AppTimerDbHelper extends SQLiteOpenHelper {
    private static final int DATABASE_VERSION = 1;
    private static final String DATABASE_NAME = "apptimer.db";

    AppTimerDbHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL(AppTimerContract.ApplicationLogRaw.get_delete_sql());
        db.execSQL(AppTimerContract.ApplicationLog.get_delete_sql());
        db.execSQL(AppTimerContract.ApplicationLogSummary.get_delete_sql());
        db.execSQL(AppTimerContract.AppTimerSettings.get_delete_sql());
        onCreate(db);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(AppTimerContract.ApplicationLogRaw.get_create_sql());
        db.execSQL(AppTimerContract.ApplicationLog.get_create_sql());
        db.execSQL(AppTimerContract.ApplicationLogSummary.get_create_sql());
        db.execSQL(AppTimerContract.AppTimerSettings.get_create_sql());
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        onUpgrade(db, oldVersion, newVersion);
    }

}
