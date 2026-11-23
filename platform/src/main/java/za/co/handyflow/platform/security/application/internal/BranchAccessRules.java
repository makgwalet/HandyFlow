package za.co.handyflow.platform.security.application.internal;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pure rules for giving a user access to branches. A user's branch access limits them to the sites of those branches
 * (MANAGER and VIEWER count; GUARD is for guards and never limits a user).
 */
final class BranchAccessRules {

    private BranchAccessRules() {}

    static final String MANAGER = "MANAGER";
    static final String VIEWER = "VIEWER";

    record Entry(UUID branchId, String role) {}

    /** The entries as given, with the role in capitals. A branch twice, a missing branch, a role that is not MANAGER or VIEWER, or too many is refused. */
    static List<Entry> clean(List<Entry> raw) {
        List<Entry> out = new ArrayList<>();
        Set<UUID> seen = new LinkedHashSet<>();
        if (raw != null) {
            for (Entry e : raw) {
                if (e == null) continue;
                if (e.branchId() == null) throw new HandyFlowException("Each branch entry needs a branch.", HttpStatus.BAD_REQUEST, "BRANCH_REQUIRED");
                String role = e.role() == null ? "" : e.role().trim().toUpperCase(Locale.ROOT);
                if (!role.equals(MANAGER) && !role.equals(VIEWER)) {
                    throw new HandyFlowException("A branch role must be MANAGER or VIEWER.", HttpStatus.BAD_REQUEST, "BRANCH_ROLE");
                }
                if (!seen.add(e.branchId())) throw new HandyFlowException("A branch can be listed only once.", HttpStatus.BAD_REQUEST, "BRANCH_DUPLICATE");
                out.add(new Entry(e.branchId(), role));
            }
        }
        if (out.size() > SiteScopeRules.MAX_ITEMS) {
            throw new HandyFlowException("Too many branches (at most " + SiteScopeRules.MAX_ITEMS + ").", HttpStatus.BAD_REQUEST, "BRANCH_TOO_MANY");
        }
        return out;
    }

    /** " AND branch_id NOT IN (?, ?)" for the branches to keep, or "" when none are kept (everything of this kind is switched off). */
    static String keepFilter(int kept) {
        return kept == 0 ? "" : " AND branch_id NOT IN (" + String.join(", ", java.util.Collections.nCopies(kept, "?")) + ")";
    }
}
