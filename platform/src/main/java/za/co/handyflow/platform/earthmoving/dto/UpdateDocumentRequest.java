package za.co.handyflow.platform.earthmoving.dto;

import java.time.LocalDate;

public record UpdateDocumentRequest(String title, String description, LocalDate expiresOn) {}
