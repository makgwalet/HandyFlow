package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import za.co.handyflow.platform.debtcollection.domain.model.CommunicationChannel;

/** contact = phone number or email; channel null means all channels. */
public record RecordOptOutRequest(@NotBlank String contact, CommunicationChannel channel, @NotBlank String reason) {}
