package za.co.handyflow.platform.projects.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** "This recipient may see this item." A recipient with no rows here may see every item on the transmittal. */
@Entity
@Table(name = "project_transmittal_recipient_items")
@IdClass(ProjectTransmittalRecipientItem.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectTransmittalRecipientItem {

    @Id @Column(name = "recipient_id") private UUID recipientId;
    @Id @Column(name = "item_id")      private UUID itemId;

    public static ProjectTransmittalRecipientItem of(UUID recipientId, UUID itemId) {
        ProjectTransmittalRecipientItem x = new ProjectTransmittalRecipientItem();
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
