package za.co.handyflow.platform.compliancetender.application.internal.submission;

/** A file could not be merged; names the file so the person knows which one to replace. */
public class PackageMergeException extends RuntimeException {

    private final String fileName;

    public PackageMergeException(String fileName, String message, Throwable cause) {
        super(fileName + ": " + message, cause);
        this.fileName = fileName;
    }

    public String fileName() { return fileName; }
}
