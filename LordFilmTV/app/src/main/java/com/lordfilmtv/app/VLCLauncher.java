package com.lordfilmtv.app;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

public class VLCLauncher {

    private static final String TAG = "VLCLauncher";
    private static final String VLC_PACKAGE = "org.videolan.vlc";
    private static final String VLC_PLAYER = "org.videolan.vlc.gui.video.VideoPlayerActivity";

    public static void launch(Context context, String videoUrl, String title) {
        if (videoUrl == null || videoUrl.isEmpty()) {
            Toast.makeText(context, "Пустая ссылка", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Launch VLC: " + videoUrl);

        // Способ 1: Прямой вызов VLC
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setPackage(VLC_PACKAGE);
            intent.setComponent(new ComponentName(VLC_PACKAGE, VLC_PLAYER));

            String mimeType = videoUrl.contains(".m3u8") ? "application/x-mpegURL" : "video/*";
            intent.setDataAndTypeAndNormalize(Uri.parse(videoUrl), mimeType);

            intent.putExtra("title", title != null ? title : "");
            intent.putExtra("from_start", true);
            intent.putExtra("position", 0L);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            context.startActivity(intent);
            Toast.makeText(context, "Открываю VLC...", Toast.LENGTH_SHORT).show();
            return;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "VLC component not found", e);
        }

        // Способ 2: vlc:// scheme
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse("vlc://" + videoUrl));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            Toast.makeText(context, "Открываю VLC...", Toast.LENGTH_SHORT).show();
            return;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "VLC scheme not available", e);
        }

        // Способ 3: Любой видеоплеер
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoUrl), "video/*");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(intent);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Generic intent failed", e);
        }

        Toast.makeText(context,
            "VLC не установлен! Установите VLC for Android",
            Toast.LENGTH_LONG).show();
        openVLCInStore(context);
    }

    public static void launchWithChooser(Context context, String videoUrl, String title) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoUrl), "video/*");
            intent.putExtra("title", title != null ? title : "");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(Intent.createChooser(intent, "Выберите плеер"));
        } catch (Exception e) {
            Log.e(TAG, "Chooser failed", e);
            Toast.makeText(context, "Нет видеоплееров", Toast.LENGTH_LONG).show();
        }
    }

    private static void openVLCInStore(Context context) {
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + VLC_PACKAGE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + VLC_PACKAGE))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ex) {
                Log.e(TAG, "Cannot open store", ex);
            }
        }
    }
}
