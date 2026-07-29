package dev.social.auth.authentication.dto;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Where a login attempt came from, for the audit trail.
 *
 * <p>{@code X-Forwarded-For} is honoured because the gateway sits in front and
 * would otherwise make every attempt look like it came from the gateway's own
 * address. Only the first hop is taken - the rest of that header is whatever
 * intermediate proxies appended and is not trustworthy.
 *
 * <p>Note the header itself is client-controlled and therefore spoofable. It is
 * good enough to group attempts in a log; it is not evidence, and nothing in
 * this service makes an authorisation decision based on it.
 */
public record ClientContext(String ipAddress, String userAgent) {

    private static final String FORWARDED_FOR = "X-Forwarded-For";
    private static final String USER_AGENT = "User-Agent";
    private static final int MAX_USER_AGENT_LENGTH = 255;

    public static ClientContext from(HttpServletRequest request) {
        return new ClientContext(resolveIpAddress(request), resolveUserAgent(request));
    }

    private static String resolveIpAddress(HttpServletRequest request) {
        String forwarded = request.getHeader(FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            String firstHop = forwarded.split(",")[0].trim();
            if (!firstHop.isEmpty()) {
                return truncate(firstHop, 45);
            }
        }
        return truncate(request.getRemoteAddr(), 45);
    }

    private static String resolveUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader(USER_AGENT);
        return userAgent == null ? "unknown" : truncate(userAgent, MAX_USER_AGENT_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "unknown";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
