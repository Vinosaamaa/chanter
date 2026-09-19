package com.chanter.gateway.security;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

/** Per-minute defaults. Authorization and durable cost quotas stay in the owning services. */
public enum RequestBudgetPolicy {
    READ(1200, 600), WRITE(300, 120), AUTH(120, 60), REGISTRATION(12, 6), RECOVERY(20, 10), LOGOUT(120, 60),
    RECONNECT(180, 60), AI(120, 20), UPLOAD(60, 10), DOWNLOAD(600, 120), SEARCH(300, 60),
    MESSAGE(600, 120), SENSITIVE(120, 30);

    private static final Pattern TENANT = Pattern.compile("^/api/v1/study-servers/([0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12})(?:/|$)");
    public final int ipLimit;
    public final int userLimit;
    RequestBudgetPolicy(int ipLimit, int userLimit) { this.ipLimit = ipLimit; this.userLimit = userLimit; }

    public static RequestBudgetPolicy classify(HttpMethod method, String path) {
        if (path.equals("/api/v1/auth/logout")) return LOGOUT;
        if (path.equals("/api/v1/auth/register")) return REGISTRATION;
        if (path.equals("/api/v1/auth/forgot-password") || path.equals("/api/v1/auth/reset-password")
                || path.equals("/api/v1/auth/verify-email")) return RECOVERY;
        if (path.startsWith("/api/v1/auth/")) return AUTH;
        if (path.startsWith("/api/v1/realtime/")) return RECONNECT;
        boolean read = HttpMethod.GET.equals(method) || HttpMethod.HEAD.equals(method) || HttpMethod.OPTIONS.equals(method);
        if (segment(path, "platform-admin") || (!read && segment(path, "moderation"))) return SENSITIVE;
        if (HttpMethod.POST.equals(method) && (path.endsWith("/assistant-answer") || path.endsWith("/assistant-answer/stream")
                || path.endsWith("/native-request") || segment(path, "native-results") || segment(path, "native-companion"))) return AI;
        if (segment(path, "search")) return SEARCH;
        if (segment(path, "course-resources")) {
            if (read && (path.endsWith("/download") || path.endsWith("/content"))) return DOWNLOAD;
            if (HttpMethod.POST.equals(method) && path.endsWith("/course-resources")) return UPLOAD;
        }
        if (!read && (path.contains("/invitations") || path.contains("-invitations") || path.contains("/billing")
                || path.contains("/reports") || path.contains("/admin/"))) return SENSITIVE;
        if (!read && (path.contains("/messages") || path.contains("/direct-messages") || path.contains("/support-questions"))) return MESSAGE;
        return read ? READ : WRITE;
    }

    private static boolean segment(String path, String name) {
        return path.endsWith("/" + name) || path.contains("/" + name + "/");
    }

    /** A partition only. It is never used to grant membership or permissions. */
    public static String tenantHint(String path) {
        var matcher = TENANT.matcher(path);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    public boolean allowsBoundedRecovery() { return this == LOGOUT || this == RECOVERY; }
}
