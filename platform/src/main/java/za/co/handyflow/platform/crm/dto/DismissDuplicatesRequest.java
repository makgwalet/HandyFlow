package za.co.handyflow.platform.crm.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/** Marks every pair among these customers as "not a duplicate". */
public record DismissDuplicatesRequest(@NotNull @Size(min = 2, max = 10) List<UUID> customerIds) {}
