package com.dji.myapplication;

import android.app.Application;
import android.content.Context;

public class UAVMonitor extends Application {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        com.cySdkyc.clx.Helper.install(this);
    }
}
