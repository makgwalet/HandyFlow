package za.co.handyflow.platform.facilitiesmanagement.dto;

import java.time.Instant;
import java.util.UUID;

/** A photo a client may look at. kind is PHOTO, BEFORE_PHOTO or AFTER_PHOTO — nothing else is ever listed. */
public record FmPortalPhotoResponse(UUID id, String fileName, String kind, Instant createdAt) {}
