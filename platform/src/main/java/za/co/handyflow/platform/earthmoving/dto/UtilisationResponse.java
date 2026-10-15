package za.co.handyflow.platform.earthmoving.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Hours worked per day on hour-metered machines. {@code perDay[0]} is {@code from}; the last entry is {@code to}. */
public record UtilisationResponse(LocalDate from, LocalDate to, int machines, BigDecimal totalHours, List<BigDecimal> perDay) {}
