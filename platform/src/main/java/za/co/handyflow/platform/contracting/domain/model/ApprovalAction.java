package za.co.handyflow.platform.contracting.domain.model;

import java.util.Set;

/**
 * The contracting actions that can require a second person's approval. Defaults follow the approved policy: publishing wording and
 * terminating need a second person; sending for signing, accepting a counterparty's proposed change and signing on behalf of a party do
 * not unless the tenant switches them on.
 */
public enum ApprovalAction {
    SEND_FOR_SIGNING(false, "CONTRACT", Set.of("CONTRACTS_ADMIN", "CONTRACTS_MANAGE")),
    PUBLISH_CLAUSE(true, "CLAUSE", Set.of("CONTRACTS_ADMIN", "CONTRACTS_TEMPLATES")),
    PUBLISH_TEMPLATE(true, "TEMPLATE", Set.of("CONTRACTS_ADMIN", "CONTRACTS_TEMPLATES")),
    TERMINATE(true, "CONTRACT", Set.of("CONTRACTS_ADMIN", "CONTRACTS_MANAGE")),
    /** Staff accepting a change a counterparty proposed. The subject is the proposal. */
    ACCEPT_COUNTERPARTY_REDLINE(false, "PROPOSAL", Set.of("CONTRACTS_ADMIN", "CONTRACTS_MANAGE")),
    /** Staff recording a signature for a party in person. The subject is the party. */
    SIGN_ON_BEHALF(false, "PARTY", Set.of("CONTRACTS_ADMIN", "CONTRACTS_MANAGE")),
    /** Cancelling the signing attempt of a contract that was sent. The subject is the contract. */
    WITHDRAW(true, "CONTRACT", Set.of("CONTRACTS_ADMIN", "CONTRACTS_MANAGE"));

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
    /** The permissions that let someone decide this kind of approval. */
    public java.util.Set<String> approverAuthorities() { return approverAuthorities; }
    public boolean canBeDecidedBy(java.util.Collection<String> authorities) {
        return authorities != null && authorities.stream().anyMatch(approverAuthorities::contains);
    }
}
