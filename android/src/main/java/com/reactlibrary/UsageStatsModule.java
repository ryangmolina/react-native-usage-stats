package com.reactlibrary;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.os.Process;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableNativeMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.WritableNativeMap;
import com.facebook.react.common.MapBuilder;

import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.AppOpsManager.OPSTR_GET_USAGE_STATS;

public class UsageStatsModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;

    public UsageStatsModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
    }

    @Override
    public String getName() {
        return "UsageStats";
    }

    @Override
    public Map<String, Object> getConstants() {
        final Map<String, Object> constants = MapBuilder.newHashMap();
        constants.put("INTERVAL_WEEKLY", UsageStatsManager.INTERVAL_WEEKLY);
        constants.put("INTERVAL_MONTHLY", UsageStatsManager.INTERVAL_MONTHLY);
        constants.put("INTERVAL_YEARLY", UsageStatsManager.INTERVAL_YEARLY);
        constants.put("INTERVAL_DAILY", UsageStatsManager.INTERVAL_DAILY);
        constants.put("INTERVAL_BEST", UsageStatsManager.INTERVAL_BEST);
        return constants;
    }

    @ReactMethod
    public void showUsageAccessSettings(String packageName) {
        Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setData(Uri.fromParts("package", packageName, null));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        reactContext.startActivity(intent);
    }

    @ReactMethod
    public void checkForPermission(Promise promise) {
        AppOpsManager appOps = (AppOpsManager) reactContext.getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(OPSTR_GET_USAGE_STATS, Process.myUid(), reactContext.getPackageName());
        promise.resolve(mode == MODE_ALLOWED);
    }

    @ReactMethod
    public void queryUsageStats(int interval, double startTime, double endTime, Promise promise) {
        WritableMap result = new WritableNativeMap();
        UsageStatsManager usageStatsManager = (UsageStatsManager)reactContext.getSystemService(Context.USAGE_STATS_SERVICE);
        List<UsageStats> queryUsageStats = usageStatsManager.queryUsageStats(interval, (long) startTime, (long) endTime);
        for (UsageStats us : queryUsageStats) {
            Log.d("UsageStats", us.getPackageName() + " = " + us.getTotalTimeInForeground());
            WritableMap usageStats = new WritableNativeMap();
            usageStats.putString("packageName", us.getPackageName());
            usageStats.putDouble("totalTimeInForeground", us.getTotalTimeInForeground());
            usageStats.putDouble("firstTimeStamp", us.getFirstTimeStamp());
            usageStats.putDouble("lastTimeStamp", us.getLastTimeStamp());
            usageStats.putDouble("lastTimeUsed", us.getLastTimeUsed());
            usageStats.putInt("describeContents", us.describeContents());
            result.putMap(us.getPackageName(), usageStats);
        }
        promise.resolve(result);
    }
}
