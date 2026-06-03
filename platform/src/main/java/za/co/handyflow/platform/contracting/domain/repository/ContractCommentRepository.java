package za.co.handyflow.platform.contracting.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import za.co.handyflow.platform.contracting.domain.model.ContractComment;

import java.util.List;
import java.util.UUID;

/**
 * File: contracting/domain/repository/ContractCommentRepository.java
 */
@Repository
public interface ContractCommentRepository extends JpaRepository<ContractComment, UUID> {

    /**
     * All comments for a contract ordered oldest-first.
     * authorName and authorRole are @Transient — populated by ContractingService
     * after the query using the party data already loaded in the transaction.
     */
    @Query("""
        SELECT c FROM ContractComment c
        WHERE c.contractId = :contractId
        ORDER BY c.createdAt ASC
    """)
    List<ContractComment> findByContract(@Param("contractId") UUID contractId);

    /** Unresolved amendment requests — shown to the owner in the HandyFlow admin UI. */
    @Query("""
        SELECT c FROM ContractComment c
        WHERE c.contractId = :contractId
          AND c.amendmentRequest = true
          AND c.resolved = false
        ORDER BY c.createdAt ASC
    """)
    List<ContractComment> findUnresolvedAmendments(@Param("contractId") UUID contractId);
}
