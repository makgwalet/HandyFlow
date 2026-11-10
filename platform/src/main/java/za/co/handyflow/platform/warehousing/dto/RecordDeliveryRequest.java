package za.co.handyflow.platform.warehousing.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** Proof of delivery: who received the goods and when. A signed POD file is attached separately as evidence. */
public record RecordDeliveryRequest(@NotBlank @Size(max = 120) String receivedBy, @NotNull Instant deliveredAt, @Size(max = 300) String note) {}
