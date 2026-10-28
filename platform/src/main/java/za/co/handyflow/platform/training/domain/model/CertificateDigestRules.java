package za.co.handyflow.platform.training.domain.model;

import java.time.LocalDate;
import java.util.List;

/** The one message a manager gets, on a day, about certificates in their team that are about to run out or have run out. Pure. */
public final class CertificateDigestRules {

    private CertificateDigestRules() {}

    public static final int LINES_SHOWN = 10;

    public record Item(String person, String course, LocalDate expiry, long daysLeft) {}

    public static String title(boolean expired, int count) {
        String what = count == 1 ? "A certificate in your team" : count + " certificates in your team";
        return expired ? what + (count == 1 ? " has expired" : " have expired") : what + (count == 1 ? " is about to expire" : " are about to expire");
    }

    public static String message(boolean expired, List<Item> items) {
        StringBuilder b = new StringBuilder();
        items.stream().limit(LINES_SHOWN).forEach(i -> {
            if (b.length() > 0) b.append(" ");
            b.append(i.person()).append(": ").append(i.course());
            if (expired) b.append(" expired on ").append(i.expiry()).append(".");
            else b.append(i.daysLeft() <= 1 ? " expires tomorrow (" : " expires in " + i.daysLeft() + " days (").append(i.expiry()).append(").");
        });
        if (items.size() > LINES_SHOWN) b.append(" And ").append(items.size() - LINES_SHOWN).append(" more.");
        b.append(" See your team's training to arrange refresher training.");
        return b.toString();
    }
}
