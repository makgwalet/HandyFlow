package za.co.handyflow.platform.contracting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Update a DRAFT or UNDER_REVIEW contract — most commonly to fill in
 * {{template}} variables that were left blank at creation time (see
 * ContractsTab.tsx's "Leave blank to fill in later" flow), but also
 * covers the same peripheral fields CreateContractRequest already
 * collects, since editing one naturally means editing the others too.
 *
 * Every field is optional and nullable — this is a partial update.
 * ContractingService.updateContract() only touches a field when it's
 * actually provided, same "if x != null" pattern as createContract().
 *
 * Deliberately does NOT include title, contractType, templateId, or a
 * raw body override — those either have no existing domain setter
 * (title/contractType) or represent a bigger, more consequential change
 * than "finish filling in this contract" (changing what template/type a
 * contract even is). variables is the intended way to change body
 * content; anything beyond that is out of scope for this endpoint.
 */
public record UpdateContractRequest(
        Map<String, String> variables,
        BigDecimal valueAmount,
        LocalDate startDate,
        LocalDate endDate,
        String notes,
        Boolean autoRenew,
        Integer renewalNoticeDays
) {}