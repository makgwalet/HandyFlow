package za.co.handyflow.platform.legalpractice.dto;

import java.time.LocalDate;
import java.util.UUID;

/** One non-working day. {@code source} is STATUTORY (worked out from the Public Holidays Act) or FIRM (added by the firm; has an id). */
public record CalendarDayResponse(UUID id, LocalDate date, String kind, String name, String source) {}
