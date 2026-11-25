package za.co.handyflow.platform.trainingprovider.domain.rules;

import java.util.regex.Pattern;

/** Rules for links to an outside learning system. A link is a plain https address. Nothing secret is held, and nothing is synced. */
public final class LmsLinkRules {

    private LmsLinkRules() {
    }

    public static final String ACTIVE = "ACTIVE";
    public static final String ENDED = "ENDED";

    private static final Pattern HTTPS = Pattern.compile("(?i)^https://[^\\s@]+$");
    private static final Pattern SECRET_PARAM = Pattern.compile("(?i)[?&;](token|password|passwd|pwd|secret|key|apikey|api_key|access_token|auth)=");

    public static String linkProblem(String systemName, String url) {
        if (!CohortRules.longEnough(systemName, 2)) {
            return "Give the name of the learning system";
        }
        if (url == null || !HTTPS.matcher(url.trim()).matches()) {
            return "Enter a link that starts with https:// and has no username or password in it";
        }
        if (SECRET_PARAM.matcher(url.trim()).find()) {
            return "The link must not carry a password, key or token. Remove it and let people sign in on the other system";
        }
        return null;
    }

    public static String editProblem(String status) {
        return ENDED.equals(status) ? "An ended link cannot be changed. Add a new one" : null;
    }

    public static String endProblem(String status, String reason) {
        if (ENDED.equals(status)) {
            return "This link has already ended";
        }
        return CohortRules.reasonProblem(reason);
    }
}
