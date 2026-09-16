package za.co.handyflow.platform.security.domain.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * The PostOrder <-> SecurityContact join — a pure composite-key
 * association with no attributes of its own, so a full JPA entity
 * (composite @IdClass ceremony) would add complexity for no benefit
 * over direct JDBC here. Deliberately simple: replace-all-on-save
 * rather than fine-grained add/remove, since a post order's contact
 * list is edited as a whole set from the UI, not one contact at a time.
 */
@Repository
@RequiredArgsConstructor
public class PostOrderContactRepository {

    private final JdbcTemplate jdbc;

    public List<UUID> findContactIdsForOrder(UUID postOrderId) {
        return jdbc.queryForList(
                "SELECT contact_id FROM security_post_order_contacts WHERE post_order_id = ?",
                UUID.class, postOrderId);
    }

    public void replaceContactsForOrder(UUID postOrderId, List<UUID> contactIds) {
        jdbc.update("DELETE FROM security_post_order_contacts WHERE post_order_id = ?", postOrderId);
        for (UUID contactId : contactIds) {
            jdbc.update("INSERT INTO security_post_order_contacts (post_order_id, contact_id) VALUES (?, ?)",
                    postOrderId, contactId);
        }
    }
}
