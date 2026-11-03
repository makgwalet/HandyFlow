package za.co.handyflow.platform.secureaccess.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** "This recipient may see this item." A recipient with no rows here may see every item in the share. */
@Entity
@Table(name = "sda_recipient_items")
@IdClass(SecureRecipientItem.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SecureRecipientItem {

    @Id @Column(name = "recipient_id") private UUID recipientId;
    @Id @Column(name = "item_id")      private UUID itemId;

    public static SecureRecipientItem of(UUID recipientId, UUID itemId) {
        SecureRecipientItem x = new SecureRecipientItem();
        x.recipientId = recipientId;
        x.itemId      = itemId;
        return x;
    }

    @EqualsAndHashCode
    public static class Key implements Serializable {
        private UUID recipientId;
        private UUID itemId;
        public Key() {}
    }
}
