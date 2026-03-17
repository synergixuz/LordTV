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
    private static final String PKG = "org.videolan.vlc";
    private static final String CLS = "org.videolan.vlc.gui.video.VideoPlayerActivity";

    public static void launch(Context ctx, String url, String title) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(ctx, "Empty URL", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setPackage(PKG);
            i.setComponent(new ComponentName(PKG, CLS));
            String mime = url.contains(".m3u8") ? "application/x-mpegURL" : "video/*";
            i.setDataAndTypeAndNormalize(Uri.parse(url), mime);
            i.putExtra("title", title != null ? title : "");
            i.putExtra("from_start", true);
            i.putExtra("position", 0L);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "VLC not found", e);
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse("vlc://" + url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return;
        } catch (ActivityNotFoundException e) {
            Log.w(TAG, "vlc:// failed", e);
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(url), "video/*");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (i.resolveActivity(ctx.getPackageManager()) != null) {
                ctx.startActivity(i);
                return;
            }
        } catch (Exception e) {
            Log.e(TAG, "Generic failed", e);
        }
        Toast.makeText(ctx, "VLC не установлен!", Toast.LENGTH_LONG).show();
        try {
            ctx.startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=" + PKG))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {}
    }

    public static void launchWithChooser(Context ctx, String url, String title) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(Uri.parse(url), "video/*");
            i.putExtra("title", title != null ? title : "");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(Intent.createChooser(i, "Player"));
        } catch (Exception e) {
            Toast.makeText(ctx, "No players", Toast.LENGTH_LONG).show();
        }
    }
}
