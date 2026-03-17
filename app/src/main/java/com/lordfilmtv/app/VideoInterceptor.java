package com.lordfilmtv.app;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

public class VideoInterceptor {

    // Паттерн стандартных видео-расширений
    private static final Pattern VIDEO_EXT = Pattern.compile(
            "\\.(m3u8|mp4|mkv|avi|webm|mov|flv|mpd|ts)(\\?.*)?$",
            Pattern.CASE_INSENSITIVE);

    // Паттерн HLS-плейлиста внутри URL (без расширения)
    private static final Pattern HLS_PATTERN = Pattern.compile(
            "(master|index|playlist|chunklist|manifest|hls|video).*\\.(m3u8|m3u)",
            Pattern.CASE_INSENSITIVE);

    // Паттерн CDN с зашифрованными путями (как lordfilm использует)
    private static final Pattern CDN_VIDEO_PATTERN = Pattern.compile(
            "/[a-zA-Z]-[a-zA-Z]{2}-[a-zA-Z]/[a-zA-Z0-9]{20,}",
            Pattern.CASE_INSENSITIVE);

    // Известные CDN-домены видеобалансеров
    private static final Set<String> VIDEO_CDN_DOMAINS = new HashSet<>();

    static {
        // Lordfilm CDN
        VIDEO_CDN_DOMAINS.add("interkh.com");

        // Популярные балансеры для кинотеатров
        VIDEO_CDN_DOMAINS.add("kodik.info");
        VIDEO_CDN_DOMAINS.add("kodikapi.com");
        VIDEO_CDN_DOMAINS.add("kodik.cc");
        VIDEO_CDN_DOMAINS.add("aniqit.com");
        VIDEO_CDN_DOMAINS.add("hdvb.co");
        VIDEO_CDN_DOMAINS.add("hdvbua.com");
        VIDEO_CDN_DOMAINS.add("videocdn.tv");
        VIDEO_CDN_DOMAINS.add("vid-cdn.com");
        VIDEO_CDN_DOMAINS.add("cdnmovies.net");
        VIDEO_CDN_DOMAINS.add("serpens.nl");
        VIDEO_CDN_DOMAINS.add("lumex.space");
        VIDEO_CDN_DOMAINS.add("voidboost.net");
        VIDEO_CDN_DOMAINS.add("bazon.cc");
        VIDEO_CDN_DOMAINS.add("alloha.tv");
        VIDEO_CDN_DOMAINS.add("filmix.ac");
        VIDEO_CDN_DOMAINS.add("kinoplay.site");
        VIDEO_CDN_DOMAINS.add("ustore.bz");
        VIDEO_CDN_DOMAINS.add("datalock.ru");
        VIDEO_CDN_DOMAINS.add("smartcdn.org");
    }

    /**
     * Определяет, является ли URL видео-контентом
     */
    public boolean isVideoUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();

        // Стандартные расширения
        if (lower.contains(".m3u8") || lower.contains(".mp4") ||
                lower.contains(".mkv") || lower.contains(".webm") ||
                lower.contains(".avi") || lower.contains(".mov") ||
                lower.contains(".flv") || lower.contains(".mpd")) {
            return true;
        }

        // HLS-пути
        if (lower.contains("/hls/") || lower.contains("/stream/") ||
                lower.contains("/video/") || lower.contains("playlist.m3u") ||
                lower.contains("/manifest/") || lower.contains("/master.")) {
            return true;
        }

        // Regex паттерн
        if (VIDEO_EXT.matcher(url).find() || HLS_PATTERN.matcher(url).find()) {
            return true;
        }

        return false;
    }

    /**
     * Определяет, является ли URL потоком с CDN (включая зашифрованные)
     */
    public boolean isCdnVideoStream(String url) {
        if (url == null || url.isEmpty()) return false;
        String lower = url.toLowerCase();

        // Проверяем домен
        for (String cdn : VIDEO_CDN_DOMAINS) {
            if (lower.contains(cdn)) return true;
        }

        // Паттерн /x-en-x/ и подобные зашифрованные пути CDN
        if (CDN_VIDEO_PATTERN.matcher(url).find()) {
            return true;
        }

        return false;
    }

    /**
     * Определяет, это HLS-плейлист или сегмент
     */
    public boolean isHlsPlaylist(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".m3u") ||
                lower.contains("playlist") || lower.contains("master") ||
                lower.contains("index.m3u");
    }

    /**
     * Определяет, это видео-сегмент (чанк) — не плейлист
     */
    public boolean isVideoSegment(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();

        // .ts сегменты
        if (lower.contains(".ts")) return true;

        // CDN с зашифрованными путями (как /x-en-x/...)
        if (CDN_VIDEO_PATTERN.matcher(url).find() && !isHlsPlaylist(url)) {
            return true;
        }

        return false;
    }

    /**
     * Пытается извлечь base URL плейлиста из URL сегмента
     */
    public String guessPlaylistUrl(String segmentUrl) {
        if (segmentUrl == null) return null;

        // Если это CDN URL типа interkh.com, берём базовый путь
        int lastSlash = segmentUrl.lastIndexOf('/');
        if (lastSlash > 0) {
            String basePath = segmentUrl.substring(0, lastSlash);
            // Попробуем стандартные имена плейлистов
            return basePath + "/index.m3u8";
        }
        return null;
    }

    /**
     * Определяет URL iframe-плеера
     */
    public boolean isPlayerIframe(String url) {
        if (url == null) return false;
        String lower = url.toLowerCase();

        for (String cdn : VIDEO_CDN_DOMAINS) {
            if (lower.contains(cdn)) return true;
        }

        return lower.contains("/embed/") || lower.contains("/player/") ||
                lower.contains("player.php") || lower.contains("/seria/") ||
                lower.contains("/movie/") || lower.contains("/video/");
    }

    public String detectQuality(String url) {
        if (url == null) return "?";
        String l = url.toLowerCase();
        if (l.contains("2160") || l.contains("4k") || l.contains("uhd")) return "4K UHD";
        if (l.contains("1080") || l.contains("fullhd") || l.contains("full_hd")) return "1080p";
        if (l.contains("720") || l.contains("hd")) return "720p HD";
        if (l.contains("480") || l.contains("sd")) return "480p";
        if (l.contains("360")) return "360p";
        if (l.contains("240")) return "240p";
        if (l.contains(".m3u8")) return "HLS Adaptive";
        if (l.contains(".mpd")) return "DASH";
        if (l.contains(".mp4")) return "MP4";
        if (l.contains("interkh.com") || isCdnVideoStream(url)) return "CDN Stream";
        return "Video";
    }

    public String getMimeType(String url) {
        if (url == null) return "video/*";
        String l = url.toLowerCase();
        if (l.contains(".m3u8") || l.contains(".m3u")) return "application/x-mpegURL";
        if (l.contains(".mpd")) return "application/dash+xml";
        if (l.contains(".mp4")) return "video/mp4";
        if (l.contains(".mkv")) return "video/x-matroska";
        if (l.contains(".webm")) return "video/webm";
        if (l.contains(".ts")) return "video/mp2t";
        return "video/*";
    }
}
