package za.co.handyflow.platform.accounting.dto;

import java.time.LocalDate;

public record ConfirmCloseStepRequest(LocalDate periodEnd, String stepKey, String note) {}
