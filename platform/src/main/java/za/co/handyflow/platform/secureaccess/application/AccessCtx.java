package za.co.handyflow.platform.secureaccess.application;

/** Where a public request came from. Stored on every log row. */
public record AccessCtx(String ip, String userAgent) {}
