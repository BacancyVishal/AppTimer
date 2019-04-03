package com.sapianz.apptimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AutoStart extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent i) {
        Intent intent = new Intent(context, FloatService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.startForegroundService(intent);
        } else
            context.startService(intent);
    }
}
