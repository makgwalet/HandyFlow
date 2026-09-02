package za.co.handyflow.platform.bookkeeping.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateBkServiceAgreementRequest(BigDecimal monthlyFee, BigDecimal hourlyRate, LocalDate endDate) {}
