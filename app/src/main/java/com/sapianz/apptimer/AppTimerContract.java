package com.sapianz.apptimer;

import android.provider.BaseColumns;

final class AppTimerContract {
    private AppTimerContract() {}

    static class ApplicationLogRaw implements BaseColumns {
        static final String TABLE_NAME = "application_log_raw";
        static final String COLUMN_NAME_PACKAGE_NAME = "package_name";
        static final String COLUMN_NAME_ACTION_TIME = "action_time";
        static final String COLUMN_NAME_ACTION_TIME_HUMAN = "action_time_human";
        static final String COLUMN_NAME_ACTION_TYPE = "action_type";

        static String get_delete_sql() {
            return "DROP TABLE IF EXISTS " + TABLE_NAME;
        }

        static String get_create_sql() {
            return "CREATE TABLE " + TABLE_NAME + " (" + _ID + " INTEGER PRIMARY KEY , " +
                    COLUMN_NAME_PACKAGE_NAME + " TEXT," +
                    COLUMN_NAME_ACTION_TIME + " TEXT, " +
                    COLUMN_NAME_ACTION_TIME_HUMAN + " TEXT, " +
                    COLUMN_NAME_ACTION_TYPE + " TEXT)";
        }
    }

    static class ApplicationLog implements BaseColumns {
        static final String TABLE_NAME = "application_log";
        static final String COLUMN_NAME_PACKAGE_NAME = "package_name";
        static final String COLUMN_NAME_START_TIME = "start_time";
        static final String COLUMN_NAME_END_TIME = "end_time";
        static final String COLUMN_NAME_DURATION = "duration";

        static String get_delete_sql() {
            return "DROP TABLE IF EXISTS " + TABLE_NAME;
        }

        static String get_create_sql() {
            return "CREATE TABLE " + TABLE_NAME + " (" + _ID + " INTEGER PRIMARY KEY , " +
                    COLUMN_NAME_PACKAGE_NAME + " TEXT," +
                    COLUMN_NAME_START_TIME + " TEXT," +
                    COLUMN_NAME_END_TIME + " TEXT," +
                    COLUMN_NAME_DURATION + " TEXT)";
        }
    }

    static class ApplicationLogSummary implements BaseColumns {
        static final String TABLE_NAME = "application_log_summary";
        static final String COLUMN_NAME_PACKAGE_NAME = "package_name";
        static final String COLUMN_NAME_DATE = "date";
        static final String COLUMN_NAME_TIME_SLOT = "time_slot";
        static final String COLUMN_NAME_TIME_SLOT_DURATION = "time_slot_duration";
        static final String COLUMN_NAME_TIME_SLOT_PERCENTAGE = "time_slot_percentage";
        static final String COLUMN_NAME_TIME_SLOT_NUMBER = "time_slot_number";

        static String get_delete_sql() {
            return "DROP TABLE IF EXISTS " + TABLE_NAME;
        }

        static String get_create_sql() {
            return "CREATE TABLE " + TABLE_NAME + " (" + _ID + " INTEGER PRIMARY KEY , " +
                    COLUMN_NAME_PACKAGE_NAME + " TEXT," +
                    COLUMN_NAME_DATE + " TEXT, " +
                    COLUMN_NAME_TIME_SLOT + " TEXT, " +
                    COLUMN_NAME_TIME_SLOT_DURATION + " TEXT, " +
                    COLUMN_NAME_TIME_SLOT_NUMBER + " TEXT, " +
                    COLUMN_NAME_TIME_SLOT_PERCENTAGE + " TEXT)";
        }
    }

    static class AppTimerSettings implements BaseColumns {
        static final String TABLE_NAME = "app_timer_settings";
        static final String COLUMN_NAME_NEXT_EXTRACT_TIME = "last_extract_time";
        static final String COLUMN_NAME_TARGET_USAGE_TIME = "target_usage_time";
        static final String COLUMN_NAME_SHOW_NAG = "show_nag";
        static final String COLUMN_NAME_SHOW_FLOATER = "show_floater";
        static final String COLUMN_NAME_FLOATER_POSITION_X = "floater_position_x";
        static final String COLUMN_NAME_FLOATER_POSITION_Y = "floater_position_y";

        static String get_delete_sql() {
            return "DROP TABLE IF EXISTS " + TABLE_NAME;
        }

        static String get_create_sql() {
            return "CREATE TABLE " + TABLE_NAME + " (" +
                    _ID + " INTEGER PRIMARY KEY, " +
                    COLUMN_NAME_NEXT_EXTRACT_TIME + " TEXT, " +
                    COLUMN_NAME_SHOW_FLOATER + " TEXT, " +
                    COLUMN_NAME_FLOATER_POSITION_X + " TEXT, " +
                    COLUMN_NAME_FLOATER_POSITION_Y + " TEXT, " +
                    COLUMN_NAME_SHOW_NAG + " BOOLEAN, " +
                    COLUMN_NAME_TARGET_USAGE_TIME + " TEXT)";
        }
    }
}
