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

import androidx.core.content.FileProvider;

import java.io.File;

import io.flutter.Log;

class ApkManager {

    private static final String SAVE_APP_NAME = "va_student_phone.apk";
    private static final String SAVE_APP_LOCATION = "vision";
    private static final String APP_FILE_NAME = SAVE_APP_LOCATION + File.separator + SAVE_APP_NAME;


    private static ApkManager instance;

    private Activity activity;

    private ApkManager() {
    }

    public static ApkManager getInstance() {
        // 先判断实例是否存在，若不存在再对类对象进行加锁处理
        if (instance == null) {
            synchronized (ApkManager.class) {
                if (instance == null) {
                    instance = new ApkManager();
                }
            }
        }
        return instance;
    }

    public void downloadApk(Activity context, String downloadUrl, String description, String title) {
        activity = context;
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
        if (file.exists()) {
            file.delete();
        }
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, SAVE_APP_NAME);
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        long taskId = downloadManager.enqueue(request);
        // 注册广播接收者，监听下载状态
        context.registerReceiver(new DownloadReceiver(downloadManager, taskId), new IntentFilter(
                DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    // 下载到本地后执行安装
    protected void installAPK(Context context, File file) {
        //调用系统安装apk
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {   //7.0版本以上
//            if (canRequestPackageInstalls(activity)) {
                Uri uriForFile = FileProvider.getUriForFile(context, "com.visioneschool.flutter_upgrade_plugin_example", file);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.setDataAndType(uriForFile, "application/vnd.android.package-archive");
//                context.startActivity(intent);
//            }
        } else {
            Uri uri = Uri.fromFile(file);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
        }

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }

    // 最小版本号大于9
    private boolean isDownloadManagerAvailable() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD;
    }

    private boolean canRequestPackageInstalls(Context context) {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.O || context.getPackageManager().canRequestPackageInstalls();
    }

    private void installBelow24(Context context, File file) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri = Uri.fromFile(file);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    /**
     * android24及以上安装需要通过 ContentProvider 获取文件Uri，
     * 需在应用中的AndroidManifest.xml 文件添加 provider 标签，
     * 并新增文件路径配置文件 res/xml/provider_path.xml
     * 在android 6.0 以上如果没有动态申请文件读写权限，会导致文件读取失败，你将会收到一个异常。
     * 插件中不封装申请权限逻辑，是为了使模块功能单一，调用者可以引入独立的权限申请插件
     */
    private void install24(Context context, File file, String appId) {
        if (context == null) throw new NullPointerException("context is null!");
        if (file == null) throw new NullPointerException("file is null!");
        if (appId == null) throw new NullPointerException("appId is null!");
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        Uri uri = FileProvider.getUriForFile(context, "$appId.fileProvider.install", file);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        context.startActivity(intent);
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
                    String downloadPath = Environment
                            .getExternalStoragePublicDirectory(
                                    Environment.DIRECTORY_DOWNLOADS)
                            .getAbsolutePath()
                            + File.separator + APP_FILE_NAME;
                    installAPK(activity, new File(downloadPath));
                    break;
                case DownloadManager.STATUS_FAILED:
                    Log.e("download", ">>>下载失败");
                    break;
            }
        }
    }

    private boolean canRequestPackageInstalls(Activity activity) {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.O || activity.getPackageManager().canRequestPackageInstalls();
    }


    public class DownloadReceiver extends BroadcastReceiver {

        private DownloadManager downloadManager;
        private long mTaskId;

        public DownloadReceiver(DownloadManager downloadManager, long mTaskId) {
            this.downloadManager = downloadManager;
            this.mTaskId = mTaskId;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            checkDownloadStatus(downloadManager, mTaskId);
        }
    }
}
