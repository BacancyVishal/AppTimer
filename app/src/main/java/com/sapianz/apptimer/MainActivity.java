package com.sapianz.apptimer;

import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.constraint.ConstraintLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import com.google.android.gms.ads.MobileAds;
import com.txusballesteros.widgets.FitChart;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private RecyclerView.Adapter mAdapter;
    private AppTimerDbHelper dbHelper;
    UsageStatsManager usageStatsManager;
    Context context;
    ArrayList<HashMap> topAppsList = new ArrayList<>();
    ConstraintLayout loader;
    AppListAdapter appListAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (!hasUsageStatsPermission(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permissions Needed")
                    .setMessage("AppTimer requires extra permissions to view your app usage data. Grant these permissions on the next screen.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), 1);
                        }
                    });
            builder.show();
        } else if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("Permissions Needed")
                    .setMessage("AppTimer requires extra permissions to show an app usage HUD on your screen. Grant these permissions on the next screen.")
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            dialog.dismiss();
                            startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION), 2);
                        }
                    });
            builder.show();
        } else {
            dbHelper = new AppTimerDbHelper(this);
            usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            loader = findViewById(R.id.dataLoader);
            context = this;

            recyclerView = findViewById(R.id.topAppsRecyclerView);
            recyclerView.setHasFixedSize(true);
            recyclerView.addItemDecoration(new DividerItemDecoration(this,
                    DividerItemDecoration.VERTICAL));

            RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
            recyclerView.setLayoutManager(layoutManager);

            refreshData();

            MobileAds.initialize(this, "ca-app-pub-sample.app.id");

        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            final LayoutInflater inflater = this.getLayoutInflater();
            final View dialogView = inflater.inflate(R.layout.settings_dialog, null);
            final Spinner hourSpinner = dialogView.findViewById(R.id.hourSpinner);
            final Spinner minuteSpinner = dialogView.findViewById(R.id.minuteSpinner);
            final Switch nagSwitch = dialogView.findViewById(R.id.nagSwitch);
            final DataUtils dataUtils = new DataUtils(context);
            HashMap<String, Integer> h = dataUtils.getSettings();
            if (h != null) {
                hourSpinner.setSelection(h.get("hours"));
                minuteSpinner.setSelection(h.get("minutes") / 10);
                nagSwitch.setChecked(h.get("nag") == 1);
            }
            builder.setView(dialogView)
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            int hours = Integer.parseInt((String) hourSpinner.getSelectedItem());
                            int minutes = Integer.parseInt((String) minuteSpinner.getSelectedItem());
                            boolean nag = nagSwitch.isChecked();
                            dataUtils.saveSettings(nag, hours, minutes);
                            restart();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
            builder.show();
            return true;
        } else if (id == R.id.action_refresh) {
            restart();
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1) {
            if (!hasUsageStatsPermission(this)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Permissions Needed")
                        .setMessage("AppTimer has not been granted permissions and will now exit.")
                        .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finishAndRemoveTask();
                            }
                        });
                builder.show();
            } else {
                recreate();
            }
        }

        if (requestCode == 2) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Permissions Needed")
                        .setMessage("AppTimer has not been granted permissions and will now exit.")
                        .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                finishAndRemoveTask();
                            }
                        });
                builder.show();
            } else {
                recreate();
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    boolean hasUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow("android:get_usage_stats",
                android.os.Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void restart() {
        stopService(new Intent(getApplication(), FloatService.class));
        recreate();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopService(new Intent(getApplication(), FloatService.class));
    }

    private void refreshFitChart(long targetUsage, long currentUsage) {
        final FitChart fitChart = findViewById(R.id.fitChart);
        fitChart.setMinValue(0f);
        fitChart.setMaxValue(100f);
        float fitvalue = ((float) currentUsage / targetUsage * 100);
        fitChart.setValue(fitvalue);
        TextView usage = findViewById(R.id.todayUsage);
        int thours, tminutes, chours, cminutes;
        thours = (int) TimeUnit.MILLISECONDS.toHours(targetUsage);
        tminutes = (int) TimeUnit.MILLISECONDS.toMinutes(targetUsage - TimeUnit.HOURS.toMillis(thours));
        chours = (int) TimeUnit.MILLISECONDS.toHours(currentUsage);
        cminutes = (int) TimeUnit.MILLISECONDS.toMinutes(currentUsage - TimeUnit.HOURS.toMillis(chours));
        usage.setText(String.format(Locale.getDefault(), "%dh %02dmin", chours, cminutes));
        TextView target = findViewById(R.id.targetUsage);
        target.setText(String.format(Locale.getDefault(), "%dh %02dmin", thours, tminutes));
    }

    private void refreshData() {
        new RefreshDatabaseTask().execute();
        new RefreshFitChartTask().execute();
    }

    private class RefreshDatabaseTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            loader.setVisibility(View.VISIBLE);

            DataUtils dataUtils = new DataUtils(context);
            dataUtils.refreshDatabase();

            topAppsList = dataUtils.getTopApps();

            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            appListAdapter = new AppListAdapter(topAppsList);
            appListAdapter.notifyDataSetChanged();
            recyclerView.setAdapter(appListAdapter);
        }
    }

    private class RefreshFitChartTask extends AsyncTask<Void, Void, HashMap<String, Long>> {

        @Override
        protected HashMap<String, Long> doInBackground(Void... voids) {
            long targetUsageTime;
            String[] projection = new String[]{
                    BaseColumns._ID,
                    AppTimerContract.AppTimerSettings.COLUMN_NAME_TARGET_USAGE_TIME
            };
            SQLiteDatabase db = dbHelper.getReadableDatabase();
            Cursor cursor = db.query(
                    AppTimerContract.AppTimerSettings.TABLE_NAME,
                    projection,
                    null, null, null, null, null
            );

            if (cursor.moveToFirst()) {
                targetUsageTime = Long.parseLong(cursor.getString(cursor.getColumnIndexOrThrow(AppTimerContract.AppTimerSettings.COLUMN_NAME_TARGET_USAGE_TIME)));
            } else {
                targetUsageTime = 0;
            }

            Calendar currentTime = Calendar.getInstance();

            String date = String.format(Locale.getDefault(), "%04d", currentTime.get(Calendar.YEAR))
                    + String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.MONTH) + 1)
                    + String.format(Locale.getDefault(), "%02d", currentTime.get(Calendar.DAY_OF_MONTH));
            projection = new String[]{
                    AppTimerContract.ApplicationLogSummary.COLUMN_NAME_PACKAGE_NAME,
                    AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION
            };
            String selection = AppTimerContract.ApplicationLogSummary.COLUMN_NAME_DATE + " = ?";
            String[] selectionArgs = {date};

            cursor = db.query(
                    AppTimerContract.ApplicationLogSummary.TABLE_NAME,
                    projection,
                    selection,
                    selectionArgs,
                    null,
                    null,
                    null
            );

            long totalUsageTime = 0;

            while (cursor.moveToNext()) {
                long incrementalDuration = cursor.getLong(cursor.getColumnIndexOrThrow(AppTimerContract.ApplicationLogSummary.COLUMN_NAME_TIME_SLOT_DURATION));
                totalUsageTime += incrementalDuration;
            }
            cursor.close();

            HashMap<String, Long> hashMap = new HashMap<>();
            hashMap.put("targetUsageTime", targetUsageTime);
            hashMap.put("totalUsageTime", totalUsageTime);

            return hashMap;
        }

        @Override
        protected void onPostExecute(HashMap<String, Long> hm) {
            long targetUsageTime = hm.get("targetUsageTime");
            long totalUsageTime = hm.get("totalUsageTime");
            refreshFitChart(targetUsageTime, totalUsageTime);
            loader.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.M || Settings.canDrawOverlays(context)) {
                stopService(new Intent(getApplication(), FloatService.class));
                startService(new Intent(getApplication(), FloatService.class));
            }
        }
    }
}
