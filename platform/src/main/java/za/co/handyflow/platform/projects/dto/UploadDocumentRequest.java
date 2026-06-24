package za.co.handyflow.platform.projects.dto;

public record UploadDocumentRequest(
        String  documentType,  // DRAWING|RFI|SUBMITTAL|CONTRACT|REPORT|PHOTO|GENERAL
        String  title,
        String  revision,
        String  fileUrl,
        String  fileName,
        Integer fileSizeKb,
        String  description
) {}
