package com.lordfilmtv.app;

import java.util.regex.Pattern;

public class VideoInterceptor {
    private static final Pattern VIDEO_PAT = Pattern.compile(
        "\\.(m3u8|mp4|mkv|avi|webm|mov|flv|mpd)(\\?.*)?$",
        Pattern.CASE_INSENSITIVE);

    public boolean isVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String l = url.toLowerCase();
        if (l.contains(".m3u8") || l.contains(".mp4") || l.contains(".mkv") ||
            l.contains(".webm") || l.contains(".avi") || l.contains(".mov") ||
            l.contains(".flv") || l.contains(".mpd")) return true;
        if (l.contains("/hls/") || l.contains("/stream/") || l.contains("playlist.m3u"))
            return true;
        return VIDEO_PAT.matcher(url).find();
    }

    public String detectQuality(String url) {
        if (url == null) return "?";
        String l = url.toLowerCase();
        if (l.contains("2160") || l.contains("4k")) return "4K";
        if (l.contains("1080")) return "1080p";
        if (l.contains("720")) return "720p";
        if (l.contains("480")) return "480p";
        if (l.contains("360")) return "360p";
        if (l.contains(".m3u8")) return "HLS";
        if (l.contains(".mp4")) return "MP4";
        return "Video";
    }

    public String getMimeType(String url) {
        if (url == null) return "video/*";
        String l = url.toLowerCase();
        if (l.contains(".m3u8")) return "application/x-mpegURL";
        if (l.contains(".mpd")) return "application/dash+xml";
        if (l.contains(".mp4")) return "video/mp4";
        if (l.contains(".mkv")) return "video/x-matroska";
        return "video/*";
    }
}
