package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateFmServiceAgreementRequest(BigDecimal monthlyFee, BigDecimal hourlyRate, LocalDate endDate) {}
