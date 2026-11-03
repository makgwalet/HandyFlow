package za.co.handyflow.platform.documents.application;

import org.springframework.http.HttpStatus;
import za.co.handyflow.platform.shared.HandyFlowException;

/**
 * A stored file no longer matches what was written (changed, truncated, or an encrypted file that fails its tag check). The file is
 * never released when this is thrown. HTTP 409 with code FILE_INTEGRITY, the same code Projects already uses for its own re-check.
 */
public class StoredFileIntegrityException extends HandyFlowException {
    public StoredFileIntegrityException() {
        super("This file no longer matches the record made when it was stored, so it was not released. Contact your administrator.",
                HttpStatus.CONFLICT, "FILE_INTEGRITY");
    }
}
