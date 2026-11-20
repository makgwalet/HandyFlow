package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Something a watched feed published. Stored once per (source, guid); the feed's text is kept as received (tags stripped), and a person decides whether it matters. */
@Entity
@Table(name = "legalcompliance_watch_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchItem extends AggregateRoot<WatchItem> {

    public static final int MIN_NOTE = 5;

    @Column(name = "source_id", nullable = false) private UUID sourceId;
    @Column(name = "source_name", nullable = false, length = 255) private String sourceName;
    @Column(nullable = false, length = 500) private String guid;
    @Column(nullable = false, length = 255) private String title;
    @Column(length = 500) private String link;
    @Column(length = 2000) private String summary;
    @Column(name = "published_on") private LocalDate publishedOn;
    @Column(name = "fetched_at", nullable = false) private Instant fetchedAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private WatchItemStatus status = WatchItemStatus.NEW;
    @Column(name = "decision_note", columnDefinition = "TEXT") private String decisionNote;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decided_by_name", length = 255) private String decidedByName;
    @Column(name = "decided_at") private Instant decidedAt;

    public static WatchItem create(TenantId t, UUID sourceId, String sourceName, String guid, String title, String link, String summary, LocalDate publishedOn, Instant fetchedAt) {
        if (guid == null || guid.isBlank()) throw new IllegalArgumentException("An item identifier is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        WatchItem i = new WatchItem();
        i.initTenantId(t);
        i.sourceId = sourceId;
        i.sourceName = sourceName;
        i.guid = guid;
        i.title = title;
        i.link = link;
        i.summary = summary;
        i.publishedOn = publishedOn;
        i.fetchedAt = fetchedAt;
        return i;
    }

    /** Only a NEW item can be decided. Calling an item not relevant needs a reason, so a missed change can be traced to a decision. */
    public void decide(WatchItemStatus outcome, String note, UUID by, String byName) {
        if (status != WatchItemStatus.NEW) throw new IllegalStateException("This item has already been triaged; reopen it first");
        if (outcome == null || outcome == WatchItemStatus.NEW) throw new IllegalArgumentException("Choose relevant or not relevant");
        String n = note == null ? "" : note.trim();
        if (outcome == WatchItemStatus.NOT_RELEVANT && n.length() < MIN_NOTE) throw new IllegalArgumentException("A reason of at least " + MIN_NOTE + " characters is required to mark an item not relevant");
        status = outcome;
        decisionNote = n.isEmpty() ? null : n;
        decidedBy = by;
        decidedByName = byName;
        decidedAt = Instant.now();
    }

    /** Puts a triaged item back in the inbox. The earlier decision is kept in the audit trail by the service. */
    public void reopen(String reason) {
        if (status == WatchItemStatus.NEW) throw new IllegalStateException("This item has not been triaged");
        if (reason == null || reason.trim().length() < MIN_NOTE) throw new IllegalArgumentException("A reason of at least " + MIN_NOTE + " characters is required to reopen an item");
        status = WatchItemStatus.NEW;
        decisionNote = null; decidedBy = null; decidedByName = null; decidedAt = null;
    }
}
