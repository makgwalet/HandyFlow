package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Manual case-open path — debtor identified explicitly (customerId
 * optional, for a walk-in debtor with no CRM record) rather than resolved
 * automatically from a customer. See OpenCaseForCustomerRequest for the
 * "just pick a customer" convenience path.
 */
public record CreateDebtCollectionCaseRequest(
        UUID customerId,
        String debtorName,
        String debtorEmail,
        String debtorPhone,
        @NotEmpty Set<UUID> invoiceIds,
        LocalDate openedDate,
        UUID assignedToUserId,
        String assignedToUserName,
        String notes
) {}
