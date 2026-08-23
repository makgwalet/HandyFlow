package za.co.handyflow.platform.approvals.dto;

/**
 * FIX: backlog 1.1 — Creative's migration surfaced a case AP's rule-based
 * model doesn't fit: AP's approver chain is genuinely tenant-wide and
 * condition-matched (an amount threshold). Creative's is not — a staff
 * member picks the exact approver list and SINGLE/SEQUENTIAL/PARALLEL
 * mode fresh for each proof they send. Forcing that through
 * ApprovalRule's tenant-wide, condition-matched shape would mean
 * creating a new one-off rule per proof, which abuses what a "rule" is
 * for. ApprovalFacade.submitAdHoc() takes a chain built from these
 * directly instead, bypassing rule-matching entirely — this is exactly
 * the case ApprovalRequest.ruleId's own Javadoc already anticipated
 * ("nullable — an ad-hoc request without a matched rule is possible").
 */
public record ChainEntryInput(
        String type,      // "USER" | "ROLE" | "EXTERNAL_CONTACT" (MANAGER_OF_SUBMITTER not supported ad-hoc — see engine's own note)
        String value,      // UUID string (USER), authority name (ROLE), or email (EXTERNAL_CONTACT)
        String name,       // display name — only meaningful for EXTERNAL_CONTACT
        boolean excludeActorOfPreviousStep
) {}