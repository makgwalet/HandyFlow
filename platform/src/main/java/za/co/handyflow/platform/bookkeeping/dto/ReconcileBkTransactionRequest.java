package za.co.handyflow.platform.bookkeeping.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Reconcile a bank transaction against an existing, unlinked, POSTED journal line. */
public record ReconcileBkTransactionRequest(@NotNull UUID journalLineId) {}
