package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

/** Pure rules for regions: a name and an optional description. */
public final class RegionRules {

    public static final int NAME_MAX = 100;
    public static final int DESCRIPTION_MAX = 500;

    private RegionRules() {}

    /** The name with its ends trimmed and runs of spaces made one. Empty, or longer than 100 characters, is refused. */
    public static String cleanName(String raw) {
        String n = raw == null ? "" : raw.strip().replaceAll("\\s+", " ");
        if (n.isEmpty()) throw new HandyFlowException("A region needs a name.", HttpStatus.BAD_REQUEST, "REGION_NAME");
        if (n.length() > NAME_MAX) throw new HandyFlowException("A region name can be at most " + NAME_MAX + " characters.", HttpStatus.BAD_REQUEST, "REGION_NAME");
        return n;
    }

    /** The description trimmed, or null when empty. More than 500 characters is refused. */
    public static String cleanDescription(String raw) {
        if (raw == null) return null;
        String d = raw.strip();
        if (d.isEmpty()) return null;
        if (d.length() > DESCRIPTION_MAX) throw new HandyFlowException("A region description can be at most " + DESCRIPTION_MAX + " characters.", HttpStatus.BAD_REQUEST, "REGION_DESCRIPTION");
        return d;
    }
}
