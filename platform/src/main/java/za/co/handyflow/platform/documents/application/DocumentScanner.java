package za.co.handyflow.platform.documents.application;

/**
 * Malware scan hook, called on every file before it is stored. No scanner product is chosen yet: the default implementation reports SKIPPED.
 * A real one (an antivirus service, a cloud scanner) implements this interface and is switched on with {@code documents.scan.provider}.
 * An INFECTED file is refused and never stored. With {@code documents.scan.required=true} a file that could not be scanned (SKIPPED or ERROR) is refused too.
 */
public interface DocumentScanner {

    enum Status { CLEAN, INFECTED, SKIPPED, ERROR }

    record Result(Status status, String detail) {
        public static Result clean() { return new Result(Status.CLEAN, null); }
        public static Result skipped(String why) { return new Result(Status.SKIPPED, why); }
    }

    Result scan(String fileName, String contentType, byte[] content);
}
