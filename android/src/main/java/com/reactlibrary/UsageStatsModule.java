package com.reactlibrary;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
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
        networkStatsManager = (NetworkStatsManager) reactContext.getSystemService(Context.NETWORK_STATS_SERVICE);
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


        constants.put("TYPE_WIFI", ConnectivityManager.TYPE_WIFI);
        constants.put("TYPE_MOBILE", ConnectivityManager.TYPE_MOBILE);
        constants.put("TYPE_MOBILE_AND_WIFI", Integer.MAX_VALUE);
        return constants;
    }

    private boolean packageExists(String packageName) {
        PackageManager packageManager = reactContext.getPackageManager();
        ApplicationInfo info = null;
        try {
            info = packageManager.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @ReactMethod
    public void showUsageAccessSettings(String packageName) {
        Intent intent = new Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS);
        if (packageExists(packageName)) {
            intent.setData(Uri.fromParts("package", packageName, null));
        }
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


    private NetworkStatsManager networkStatsManager;

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

    private double getDataUsage(int networkType, String subscriberId, int packageUid, long startTime, long endTime) {
        NetworkStats networkStatsByApp;
        double currentDataUsage = 0;
        try {
            networkStatsByApp = networkStatsManager.querySummary(networkType, subscriberId, startTime, endTime);
            do {
                NetworkStats.Bucket bucket = new NetworkStats.Bucket();
                networkStatsByApp.getNextBucket(bucket);
                if (bucket.getUid() == packageUid) {
                    currentDataUsage += (bucket.getRxBytes() + bucket.getTxBytes());
                }
            } while (networkStatsByApp.hasNextBucket());

        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return currentDataUsage;
    }

    @ReactMethod
    public void getAppDataUsage(String packageName, int networkType, double startTime, double endTime, Promise promise) {
        // get sim card
        String subId = getSubscriberId(reactContext, ConnectivityManager.TYPE_MOBILE);

        int uid = getAppUid(packageName);
        if (networkType == ConnectivityManager.TYPE_MOBILE) {
            promise.resolve(getDataUsage(ConnectivityManager.TYPE_MOBILE,  null, uid, (long) startTime, (long) endTime));
        } else if (networkType == ConnectivityManager.TYPE_WIFI) {
            promise.resolve(getDataUsage(ConnectivityManager.TYPE_WIFI, "", uid, (long) startTime, (long) endTime));
        } else {
            promise.resolve(getDataUsage(ConnectivityManager.TYPE_MOBILE,  "", uid, (long) startTime, (long) endTime)
                    + getDataUsage(ConnectivityManager.TYPE_WIFI, "", uid, (long) startTime, (long) endTime)
            );
        }
    }

    private int getAppUid (String packageName) {
        // get app uid
        PackageManager packageManager = reactContext.getPackageManager();
        ApplicationInfo info = null;
        try {
            info = packageManager.getApplicationInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        int uid = 0;
        if (info != null) {
            uid = info.uid;
        }
        return uid;
    }

    @ReactMethod
    public void getAccounts(Promise promise, Context context) {
        List<Account> accounts = new ArrayList<>();
        WriteableArray accs = new WriteableNativeArray();
        for (android.accounts.Account item : android.accounts.AccountManager.get(context).getAccounts()) {
            Account account = new Account();
            account.setName(item.name);
            account.setProvider(item.type);
            accounts.add(account);

            WritableMap acc = new WritableNativeMap();
            acc.putString("name", account.getName());
            acc.putString("provider", account.getProvider());
            accs.pushMap(acc);
        }
        promise.resolve(accs);
    }
}
