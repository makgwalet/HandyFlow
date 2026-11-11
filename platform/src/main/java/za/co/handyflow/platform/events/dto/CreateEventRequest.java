package za.co.handyflow.platform.events.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Limits match the events table so bad input is a 400 with a message, never a database error. */
public record CreateEventRequest(
        @NotBlank @Size(max = 300) String title,
        @Size(max = 20000) String description,
        @Pattern(regexp = "CONFERENCE|WEDDING|CHURCH|FESTIVAL|CORPORATE|COMMUNITY|FUNDRAISER|GENERAL",
                message = "must be one of CONFERENCE, WEDDING, CHURCH, FESTIVAL, CORPORATE, COMMUNITY, FUNDRAISER, GENERAL")
        String eventType,
        @Size(max = 300) String venueName,
        @Size(max = 2000) String venueAddress,
        @Positive Integer venueCapacity,
        @NotNull LocalDateTime startDatetime,
        @NotNull LocalDateTime endDatetime,
        boolean isFree,
        boolean isPrivate,
        LocalDateTime registrationDeadline,
        @Size(max = 20000) String notes
) {}
