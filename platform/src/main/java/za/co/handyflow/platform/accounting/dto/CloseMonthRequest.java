package za.co.handyflow.platform.accounting.dto;

import java.time.LocalDate;

public record CloseMonthRequest(LocalDate periodEnd, String note) {}
