package za.co.handyflow.platform.debtcollection.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import za.co.handyflow.platform.debtcollection.domain.model.LetterDispatchMethod;
import za.co.handyflow.platform.debtcollection.domain.model.NoticeType;

import java.time.LocalDate;
import java.util.UUID;

/**
 * With communicationId (a LETTER on the same case) the address, method, tracking, dispatch date and delivery
 * outcome are pre-filled from that communication; otherwise addressUsed is required. defaultDate is the date the
 * consumer fell into default, if known (used only for the advisory calculation).
 */
public record RecordNoticeRequest(
        @NotNull NoticeType noticeType,
        @NotBlank String noticeVersion,
        String addressUsed,
        String addressSource,
        LetterDispatchMethod dispatchMethod,
        LocalDate dispatchedDate,
        String trackingReference,
        String postOfficeBranch,
        String collectionNoticeReference,
        LocalDate defaultDate,
        UUID communicationId,
        UUID evidenceId,
        String notes
) {}
