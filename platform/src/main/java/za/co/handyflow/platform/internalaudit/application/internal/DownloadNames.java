package za.co.handyflow.platform.internalaudit.application.internal;

/** Makes a stored file name safe to put in a Content-Disposition header. */
public final class DownloadNames {
    private DownloadNames() {}

    static final String FALLBACK = "workpaper";

    /** Drops quotes, backslashes, slashes and control characters (which could end the header or add another), and trims. Never empty. */
    public static String safe(String name) {
        if (name == null) return FALLBACK;
        String s = name.replaceAll("[\\p{Cntrl}\"\\\\/]", "").trim();
        return s.isEmpty() ? FALLBACK : s.length() > 200 ? s.substring(0, 200) : s;
    }
}
