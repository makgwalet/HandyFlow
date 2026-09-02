package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Reconcile a bank transaction by creating a brand-new 2-line journal
 * entry on the fly (the bank-linked {@code BkAccount} on one line, this
 * caller-supplied contra account on the other) and linking the
 * transaction to the resulting line — for a bank movement with no
 * existing matching journal entry yet.
 */
public record ReconcileBkTransactionWithNewJournalRequest(@NotNull UUID contraAccountId, String description) {}
