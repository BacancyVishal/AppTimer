package com.sapianz.apptimer;

import android.app.Activity;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

public class OveruseAlertActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_overuse_alert);

        AssetManager assetManager = getAssets();
        ImageView imageView = findViewById(R.id.breakImage);
        try {
            InputStream ims = assetManager.open("break.jpg");
            Drawable d = Drawable.createFromStream(ims, null);
            imageView.setImageDrawable(d);
        } catch (IOException ignored) {}

        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(7000);
                    finish();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        };
        t.start();
    }
}
