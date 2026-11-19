package za.co.handyflow.platform.contracting.domain.model;

import java.util.Set;

/**
 * The contracting actions that can require a second person's approval. Defaults follow the approved policy: publishing wording and
 * terminating need a second person; sending for signing does not unless the tenant switches it on.
 */
public enum ApprovalAction {
    SEND_FOR_SIGNING(false, "CONTRACT", Set.of("CONTRACTS_ADMIN", "CONTRACTS_MANAGE")),
    PUBLISH_CLAUSE(true, "CLAUSE", Set.of("CONTRACTS_ADMIN", "CONTRACTS_TEMPLATES")),
    PUBLISH_TEMPLATE(true, "TEMPLATE", Set.of("CONTRACTS_ADMIN", "CONTRACTS_TEMPLATES")),
    TERMINATE(true, "CONTRACT", Set.of("CONTRACTS_ADMIN", "CONTRACTS_MANAGE"));

    private final boolean requiredByDefault;
    private final String subjectType;
    private final Set<String> approverAuthorities;

    ApprovalAction(boolean requiredByDefault, String subjectType, Set<String> approverAuthorities) {
        this.requiredByDefault = requiredByDefault;
        this.subjectType = subjectType;
        this.approverAuthorities = approverAuthorities;
    }

    public boolean requiredByDefault() { return requiredByDefault; }
    public String subjectType() { return subjectType; }
    public boolean canBeDecidedBy(java.util.Collection<String> authorities) {
        return authorities != null && authorities.stream().anyMatch(approverAuthorities::contains);
    }
}
