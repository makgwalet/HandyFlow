package za.co.handyflow.platform.debtcollection.application.internal;

/** Turns a plain-text collection message into safe HTML: escapes markup (debtor-specific text must never inject HTML) and keeps line breaks. */
final class EmailBodyHtml {

    private EmailBodyHtml() {}

    static String toHtml(String plain) {
        String s = plain == null ? "" : plain;
        StringBuilder sb = new StringBuilder("<div style=\"font-family:Arial,sans-serif;font-size:14px;white-space:pre-wrap\">");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.append("</div>").toString();
    }
}
