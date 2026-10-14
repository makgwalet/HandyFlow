package za.co.handyflow.platform.accountant.application.internal;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Which practice clients a staff member can see (finance patch F15). Pure, so it is tested without Spring.
 * <p>
 * Staff holding ACCOUNTANT_ALL_CLIENTS see every client (everyone who could read clients before this change was given it,
 * so nobody lost access). Anyone else sees only the clients they are assigned to.
 */
public final class ClientAccessRules {

    private ClientAccessRules() {}

    public static final List<String> ROLES = List.of("PREPARER", "REVIEWER", "PARTNER");

    public static boolean canAccess(boolean seesAll, Set<UUID> assigned, UUID clientId) {
        return seesAll || (clientId != null && assigned != null && assigned.contains(clientId));
    }

    /** Only the items whose client the person can see. */
    public static <T> List<T> visible(boolean seesAll, Set<UUID> assigned, List<T> items, Function<T, UUID> clientOf) {
        if (seesAll) return items;
        return items.stream().filter(i -> canAccess(false, assigned, clientOf.apply(i))).toList();
    }

    /** Null when the assignment may be made, otherwise why not. */
    public static String assignProblem(String role, boolean alreadyAssignedInThatRole) {
        if (role == null || !ROLES.contains(role)) return "Role must be one of " + String.join(", ", ROLES);
        if (alreadyAssignedInThatRole) return "That person already has this role on the client";
        return null;
    }

    public static String denied() {
        return "You are not assigned to this client. Ask a partner to assign you.";
    }
}
