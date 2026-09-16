package za.co.handyflow.platform.security.dto;

import java.util.UUID;

public record PostOrderAttachmentResponse(UUID id, String fileUrl, String fileName) {}
