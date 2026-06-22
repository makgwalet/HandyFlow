package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
public record ProcedureLineRequest(
        String     tariffCode,
        String     icd10Code,
        String     description,
        BigDecimal quantity,
        BigDecimal unitPrice
) {}