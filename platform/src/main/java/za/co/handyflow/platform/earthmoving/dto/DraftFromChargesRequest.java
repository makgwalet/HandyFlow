package za.co.handyflow.platform.earthmoving.dto;

import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Put these pending charges, all for one customer, on one draft invoice. */
public record DraftFromChargesRequest(@NotEmpty List<UUID> chargeIds, String title, LocalDate dueDate) {}
