package za.co.handyflow.platform.clinic.dto.billing;

import java.math.BigDecimal;
import java.util.List;

public record CreateClaimRequest(
        String schemeName,
        String memberNumber,
        String dependentCode,
        String     consultationTariffCode,
        String     consultationIcd10Code,
        String     consultationDescription,
        BigDecimal consultationRate,
        List<ProcedureLineRequest> procedures
) {}
