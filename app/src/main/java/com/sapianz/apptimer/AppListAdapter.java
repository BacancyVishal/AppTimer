package com.sapianz.apptimer;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class AppListAdapter extends RecyclerView.Adapter<AppListAdapter.ListViewHolder> {
    private ArrayList<HashMap> topAppsList;
    private Context context;
    private int AD_TYPE = 0;

    AppListAdapter(ArrayList<HashMap> al) {
        this.topAppsList = al;
    }

    class ListViewHolder extends RecyclerView.ViewHolder {
        TextView appName, usageTime;
        ImageView appLogo;

        ListViewHolder(@NonNull View itemView) {
            super(itemView);
            appName = itemView.findViewById(R.id.appName);
            usageTime = itemView.findViewById(R.id.appUsageTime);
            appLogo = itemView.findViewById(R.id.appLogo);
        }
    }

    @Override
    public int getItemViewType(int position) {
        HashMap h = this.topAppsList.get(position);
        if (h.get("adview") != null) return AD_TYPE;
        return 1;
    }

    @NonNull
    @Override
    public ListViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View itemView;
        this.context = viewGroup.getContext();

        if (viewType == AD_TYPE) {
            AdView adView = new AdView(this.context);
            adView.setAdSize(AdSize.BANNER);
            adView.setAdUnitId("ca-app-pub-sample.ad.unit.id");
            float density = this.context.getResources().getDisplayMetrics().density;
            int height = Math.round(AdSize.BANNER.getHeight() * density);
            AbsListView.LayoutParams params = new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, height);
            adView.setLayoutParams(params);
            AdRequest adRequest = new AdRequest.Builder().build();
            adView.loadAd(adRequest);
            return new ListViewHolder(adView);
        } else {
            itemView = LayoutInflater.from(this.context)
                    .inflate(R.layout.top_app_list_item, viewGroup, false);
        }

        return new ListViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull ListViewHolder listViewHolder, int i) {
        HashMap<String, Long> hm = this.topAppsList.get(i);
        HashMap.Entry<String, Long> entry = hm.entrySet().iterator().next();
        long usageTime = entry.getValue();
        String packageName = entry.getKey();
        PackageManager pm = this.context.getPackageManager();
        ApplicationInfo applicationInfo;

        try {
            applicationInfo = pm.getApplicationInfo(packageName, 0);
        } catch (final PackageManager.NameNotFoundException e) {
            applicationInfo = null;
        }

        String applicationName = (String) (applicationInfo != null ? pm.getApplicationLabel(applicationInfo) : packageName);
        Drawable appIcon = applicationInfo != null ? pm.getApplicationIcon(applicationInfo) : null;
        if (appIcon != null && listViewHolder.appLogo != null) {
            listViewHolder.appLogo.setImageDrawable(appIcon);
        }

        if (listViewHolder.usageTime != null) {
            long hours = TimeUnit.MILLISECONDS.toHours(usageTime);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(usageTime - TimeUnit.HOURS.toMillis(hours));
            if (hours != 0) {
                listViewHolder.usageTime.setText(String.format(Locale.getDefault(), "%dh %02dmin", (int) hours, (int) minutes));
            } else if (minutes != 0) {
                listViewHolder.usageTime.setText(String.format(Locale.getDefault(), "%dmin", (int) minutes));
            } else {
                listViewHolder.usageTime.setText(String.format(Locale.getDefault(), "Less than %dmin", 1));
            }
        }

        if (listViewHolder.usageTime != null) {
            listViewHolder.appName.setText(applicationName);
        }
    }

    @Override
    public int getItemCount() {
        return topAppsList.size();
    }
}
