package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeDeliveryStatus;

import java.time.LocalDate;

/** deliveredDate is required when status is DELIVERED. Recording delivery facts resets any earlier legal review. */
public record NoticeDeliveryRequest(@NotNull NoticeDeliveryStatus status, LocalDate deliveredDate, String detail,
                                    String postOfficeBranch, String collectionNoticeReference) {}
