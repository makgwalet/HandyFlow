package za.co.handyflow.platform.clinic.application.internal;

import za.co.handyflow.platform.clinic.domain.model.ClinicClaim;
import za.co.handyflow.platform.clinic.domain.model.ClinicClaimLine;
import za.co.handyflow.platform.clinic.dto.billing.CreateClaimRequest;
import za.co.handyflow.platform.clinic.dto.billing.ProcedureLineRequest;

import java.util.ArrayList;
import java.util.List;

/** What a corrected claim starts from (CLINIC-DEC-003/004). Pure: no Spring, no database. */
final class ClaimCorrectionRules {

    private ClaimCorrectionRules() {}

    /** The new claim as a request: the claim's own scheme details and tariff lines. Medicine lines are rebuilt from the prescriptions. */
    static CreateClaimRequest copyOf(ClinicClaim old) {
        ClinicClaimLine consultation = null;
        List<ProcedureLineRequest> procedures = new ArrayList<>();
        for (ClinicClaimLine l : old.getLines()) {
            if ("CONSULTATION".equals(l.getLineType()) && consultation == null) consultation = l;
            else if ("PROCEDURE".equals(l.getLineType()))
                procedures.add(new ProcedureLineRequest(l.getTariffCode(), l.getIcd10Code(), l.getDescription(), l.getQuantity(), l.getUnitPrice()));
        }
        return new CreateClaimRequest(old.getSchemeName(), old.getMemberNumber(), old.getDependentCode(),
                consultation == null ? null : consultation.getTariffCode(),
                consultation == null ? null : consultation.getIcd10Code(),
                consultation == null ? null : consultation.getDescription(),
                consultation == null ? null : consultation.getUnitPrice(),
                procedures);
    }

    static String correctionNote(ClinicClaim old, String reason) {
        String ref = old.getReferenceNumber() == null || old.getReferenceNumber().isBlank() ? old.getId().toString().substring(0, 8) : old.getReferenceNumber();
        String note = "Corrects claim " + ref + ": " + reason;
        return note.length() > 1000 ? note.substring(0, 1000) : note;
    }
}
