package za.co.handyflow.platform.catalogue.dto;

import java.time.Instant;
import java.util.UUID;

/** A picture or document on an item. {@code url} is the download path; files are always sent as downloads. */
public record MediaResponse(UUID id, UUID itemId, String kind, String title, String altText, String fileName, String contentType,
                            long sizeBytes, boolean primary, int sortOrder, Instant uploadedAt, String url) {}
