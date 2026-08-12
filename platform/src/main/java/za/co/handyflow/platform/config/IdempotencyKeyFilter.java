package za.co.handyflow.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import za.co.handyflow.platform.shared.IdempotencyKeyService;
import za.co.handyflow.platform.shared.IdempotencyKeyService.*;
import za.co.handyflow.platform.shared.TenantContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

/**
 * Applies IdempotencyKeyService to any POST/PUT/PATCH request that carries
 * an Idempotency-Key header. Opt-in per request, not mandatory: a request
 * without the header is completely unaffected — this doesn't change
 * behavior for any existing client, only adds protection for ones that
 * choose to send the header (the recommended pattern, matching Stripe et
 * al.'s convention, per Section 19.1 of the discovery doc).
 * <p>
 * WHY THIS LIVES IN `config`, NOT `shared`: same reasoning as
 * RateLimitFilter — this is composition-root wiring (deciding which
 * requests get the treatment and how the filter chain is ordered), while
 * the actual reusable logic (IdempotencyKeyService) stays in `shared`.
 * <p>
 * FILTER ORDERING — IMPORTANT: this MUST run AFTER the JWT/tenant-resolving
 * filters (JwtAuthFilter etc.), not before like RateLimitFilter. Idempotency
 * keys are scoped per-tenant (see the migration's UNIQUE constraint), so
 * TenantContext must already be populated when this filter runs. Register
 * with addFilterAfter(idempotencyKeyFilter, JwtAuthFilter.class) (or
 * whichever of the five auth filters is guaranteed to have run by then) in
 * SecurityConfig — see the companion wiring instructions.
 * <p>
 * WHAT THIS DOES NOT DO: apply itself to every mutating endpoint
 * automatically. It only activates when the client sends the header — so
 * existing clients that don't send it get zero protection and zero
 * behavior change, which is the safe default for a mechanism being
 * introduced into a codebase already in active use. Client-side adoption
 * (mobile app, frontend retry logic actually sending this header on
 * retries) is a separate, follow-up piece of work this doesn't cover.
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    private final IdempotencyKeyService idempotencyKeyService;

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH");
    private static final String HEADER = "Idempotency-Key";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String idempotencyKey = request.getHeader(HEADER);
        if (idempotencyKey == null || idempotencyKey.isBlank()
                || !MUTATING_METHODS.contains(request.getMethod())
                || TenantContext.getTenantIdAsObject() == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        ClaimResult result = idempotencyKeyService.claim(
                TenantContext.getTenantIdAsObject(), path, idempotencyKey);

        if (result instanceof AlreadyCompleted completed) {
            // Replay the original response verbatim — the caller gets
            // exactly what the first, real execution produced, not a
            // second execution's result.
            response.setStatus(completed.responseStatus());
            if (completed.contentType() != null) {
                response.setContentType(completed.contentType());
            }
            response.getWriter().write(completed.responseBody() != null ? completed.responseBody() : "");
            return;
        }

        if (result instanceof InProgress) {
            response.setStatus(409);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"message\":\"A request with this Idempotency-Key is already being processed.\"}");
            return;
        }

        // Claimed — this request owns execution. Wrap the response so the
        // body can be captured after the real controller runs, since
        // HttpServletResponse's output stream can normally only be
        // written to once, not read back afterward.
        UUID recordId = ((Claimed) result).recordId();
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(request, wrappedResponse);

            int status = wrappedResponse.getStatus();
            String body = new String(wrappedResponse.getContentAsByteArray(), StandardCharsets.UTF_8);

            if (status >= 500) {
                // Server error — release the claim so a legitimate retry
                // with the same key can actually re-execute, rather than
                // being stuck replaying a failure forever.
                idempotencyKeyService.releaseOnServerError(recordId);
            } else {
                idempotencyKeyService.complete(recordId, status, body, wrappedResponse.getContentType());
            }
        } finally {
            // REQUIRED with ContentCachingResponseWrapper: without this,
            // nothing actually gets written to the real response — the
            // wrapper only buffers internally until explicitly copied out.
            wrappedResponse.copyBodyToResponse();
        }
    }
}