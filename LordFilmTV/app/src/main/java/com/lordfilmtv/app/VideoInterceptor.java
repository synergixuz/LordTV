package com.lordfilmtv.app;

import java.util.regex.Pattern;

public class VideoInterceptor {

    private static final Pattern VIDEO_PATTERN = Pattern.compile(
        "\\.(m3u8|mp4|mkv|avi|webm|mov|flv|ts|mpd)(\\?.*)?$",
        Pattern.CASE_INSENSITIVE
    );

    public boolean isVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();

        if (lower.contains(".m3u8") || lower.contains(".mp4") ||
            lower.contains(".mkv") || lower.contains(".webm") ||
            lower.contains(".avi") || lower.contains(".mov") ||
            lower.contains(".flv") || lower.contains(".mpd")) {
            return true;
        }

        if (lower.contains("/hls/") || lower.contains("/stream/") ||
            lower.contains("manifest") || lower.contains("playlist.m3u")) {
            return true;
        }

        return VIDEO_PATTERN.matcher(url).find();
    }

    public String detectQuality(String url) {
        if (url == null) return "Неизвестно";
        String lower = url.toLowerCase();

        if (lower.contains("2160") || lower.contains("4k")) return "4K UHD";
        if (lower.contains("1080")) return "1080p Full HD";
        if (lower.contains("720")) return "720p HD";
        if (lower.contains("480")) return "480p SD";
        if (lower.contains("360")) return "360p";
        if (lower.contains("240")) return "240p";
        if (lower.contains(".m3u8")) return "HLS Stream";
        if (lower.contains(".mpd")) return "DASH Stream";
        if (lower.contains(".mp4")) return "MP4";

        return "Видео";
    }

    public String getMimeType(String url) {
        if (url == null) return "video/*";
        String lower = url.toLowerCase();

        if (lower.contains(".m3u8")) return "application/x-mpegURL";
        if (lower.contains(".mpd")) return "application/dash+xml";
        if (lower.contains(".mp4")) return "video/mp4";
        if (lower.contains(".mkv")) return "video/x-matroska";
        if (lower.contains(".webm")) return "video/webm";

        return "video/*";
    }
}
