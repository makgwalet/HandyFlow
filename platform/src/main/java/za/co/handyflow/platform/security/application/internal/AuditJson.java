package za.co.handyflow.platform.security.application.internal;

/** Builds small JSON documents for audit rows with correct escaping (newlines, backslashes, quotes). */
final class AuditJson {

    private AuditJson() {}

    static String statusChange(String status, String note) {
        return "{\"status\":" + quote(status) + ",\"note\":" + (note == null ? "null" : quote(note)) + "}";
    }

    static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
