package za.co.handyflow.platform.contracting.application.internal;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Remembers, for the current request, whether the client address {@link ClientIpResolver} chose came from the connection itself
 * ({@code DIRECT}) or from a forwarded header sent by a configured trusted proxy ({@code TRUSTED_PROXY}). Services that store an
 * address call {@link #sourceFor(String)} so the stored address always says where it came from, without every method taking a second
 * parameter. Outside a web request (a job, a test) there is no source and nothing is recorded.
 */
public final class ClientIpContext {
    static final String ATTRIBUTE = "hf.contracting.clientIpSource";

    private ClientIpContext() {}

    static void remember(HttpServletRequest request, ClientIpResolver.Source source) {
        request.setAttribute(ATTRIBUTE, source.name());
    }

    /** DIRECT or TRUSTED_PROXY for the request being handled, or null when there is none. */
    public static String currentSource() {
        RequestAttributes a = RequestContextHolder.getRequestAttributes();
        Object v = a == null ? null : a.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        return v instanceof String s ? s : null;
    }

    /** The source to store next to {@code ip}: only when an address is actually being stored. */
    public static String sourceFor(String ip) {
        return ip == null || ip.isBlank() ? null : currentSource();
    }
}
