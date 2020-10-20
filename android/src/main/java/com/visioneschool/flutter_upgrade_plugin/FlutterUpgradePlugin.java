package com.visioneschool.flutter_upgrade_plugin;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.Map;

import io.flutter.Log;
import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;

/**
 * FlutterUpgradePlugin
 */
public class FlutterUpgradePlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {

    private MethodChannel channel;
    private Context context;
    private Activity activity;
    private File apkFile;
    private String appId = "";
    private static final int INSTALL_REQUEST_CODE = 1001;
    private static final String SAVE_APP_NAME = "vision/va_student_phone.apk";
    private static final String APP_FILE_NAME = Environment
            .getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS)
            .getAbsolutePath()
            + File.separator + SAVE_APP_NAME;


    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "flutter_upgrade_plugin");
        channel.setMethodCallHandler(this);
        context = flutterPluginBinding.getApplicationContext();
    }


    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else if (call.method.equals("downloadAndInstallApk")) {
            System.out.println("downloadAndInstallApk");
            Map<String, String> params = call.arguments();
            // 下载
            appId = params.get("appId");
            downloadApk(params.get("downloadUrl"), params.get("description"), params.get("title"), params.get("appId"));
        } else {
            result.notImplemented();
        }
    }

    // 最小版本号大于9
    private boolean isDownloadManagerAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    private void downloadApk(String downloadUrl, String description, String title, String appId) {
        if (!isDownloadManagerAvailable()) {
            return;
        }
        if (downloadUrl == null || downloadUrl.isEmpty()) {
            return;
        }

        DownloadManager.Request request;
        try {
            request = new DownloadManager.Request(Uri.parse(downloadUrl));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        request.setTitle(title);
        request.setDescription(description);

        // 在通知栏显示下载进度
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            request.setNotificationVisibility(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        }

        File file = new File(APP_FILE_NAME);
        System.out.println(file.getName());
        if (file.exists()) {
            file.delete();
        }
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, SAVE_APP_NAME);
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long taskId = downloadManager.enqueue(request);
        // 注册广播接收者，监听下载状态
        activity.registerReceiver(new DownloadReceiver(downloadManager, taskId), new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        binding.addActivityResultListener(new PluginRegistry.ActivityResultListener() {
            @Override
            public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
                if (resultCode == Activity.RESULT_OK && requestCode == INSTALL_REQUEST_CODE) {
                    installAPK(activity, apkFile);
                    return true;
                } else {
                    return false;
                }
            }
        });
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {

    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {

    }

    @Override
    public void onDetachedFromActivity() {

    }

    public class DownloadReceiver extends BroadcastReceiver {

        private final DownloadManager downloadManager;
        private final long mTaskId;

        public DownloadReceiver(DownloadManager downloadManager, long mTaskId) {
            this.downloadManager = downloadManager;
            this.mTaskId = mTaskId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            checkDownloadStatus(downloadManager, mTaskId);
        }
    }

    // 检查下载状态
    private void checkDownloadStatus(DownloadManager downloadManager, long taskId) {
        DownloadManager.Query query = new DownloadManager.Query();
        query.setFilterById(taskId);// 筛选下载任务，传入任务ID，可变参数
        Cursor c = downloadManager.query(query);
        if (c.moveToFirst()) {
            int status = c.getInt(c
                    .getColumnIndex(DownloadManager.COLUMN_STATUS));
            switch (status) {
                case DownloadManager.STATUS_PAUSED:
                    Log.i("download", ">>>下载暂停");
                case DownloadManager.STATUS_PENDING:
                    android.util.Log.i("download", ">>>下载延迟");
                case DownloadManager.STATUS_RUNNING:
                    Log.i("download", ">>>正在下载");
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    Log.i("download", ">>>下载完成");
                    // 下载完成安装APK
                    apkFile = new File(APP_FILE_NAME);
                    installAPK(activity, apkFile);
                    break;
                case DownloadManager.STATUS_FAILED:
                    Log.e("download", ">>>下载失败");
                    break;
            }
        }
    }

    // 下载到本地后执行安装
    protected void installAPK(Activity activity, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (canRequestPackageInstalls(activity)) { // 已经获取了安装权限
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                Uri uri = FileProvider.getUriForFile(context, appId + ".fileProvider.install", file);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                activity.startActivity(intent);
            } else { // 未获取安装权限
                showSettingPackageInstall(activity);
            }
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            activity.startActivity(intent);
        }
    }

    private void showSettingPackageInstall(Activity activity) { // todo to test with android 26
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d("SettingPackageInstall", ">= Build.VERSION_CODES.O");
            Uri uri = Uri.parse("package:" + activity.getPackageName());
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri);
            activity.startActivityForResult(intent, INSTALL_REQUEST_CODE);
        } else {
            throw new RuntimeException("VERSION.SDK_INT < O");
        }
    }


    private static boolean canRequestPackageInstalls(Activity activity) {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls();
    }
}
