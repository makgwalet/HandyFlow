package za.co.handyflow.platform.shared.internal.storage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredFileBlobRepository extends JpaRepository<StoredFileBlob, String> {
}