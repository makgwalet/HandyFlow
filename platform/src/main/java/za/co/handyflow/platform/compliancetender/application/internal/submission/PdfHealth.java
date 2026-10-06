package za.co.handyflow.platform.compliancetender.application.internal.submission;

/** What opening a PDF showed. UNKNOWN means it has not been opened (not a PDF, or not inspected yet). */
public enum PdfHealth { UNKNOWN, OK, ENCRYPTED, UNREADABLE }
