package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A public RSS/Atom feed a tenant follows. The address cannot be edited (add a new source instead), so what was fetched always traces back to one address. */
@Entity
@Table(name = "legalcompliance_watch_sources")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WatchSource extends AggregateRoot<WatchSource> {

    /** After this many failed fetches in a row the tenant is told, once, that the source needs attention. */
    public static final int FAILURES_BEFORE_ALERT = 3;

    @Column(nullable = false, length = 255) private String name;
    @Column(length = 255) private String authority;
    @Column(name = "feed_url", nullable = false, length = 500) private String feedUrl;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "last_fetched_at") private Instant lastFetchedAt;
    @Enumerated(EnumType.STRING) @Column(name = "last_status", nullable = false, length = 6) private FetchStatus lastStatus = FetchStatus.NEVER;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "last_new_items", nullable = false) private int lastNewItems;
    @Column(name = "consecutive_failures", nullable = false) private int consecutiveFailures;
    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_by_name", length = 255) private String createdByName;

    /** feedUrl must already have passed UrlRules.validate. */
    public static WatchSource create(TenantId t, String name, String authority, String feedUrl, UUID by, String byName) {
        WatchSource s = new WatchSource();
        s.initTenantId(t);
        s.feedUrl = feedUrl;
        s.rename(name, authority);
        s.createdBy = by;
        s.createdByName = byName;
        return s;
    }

    public void rename(String name, String authority) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("A name is required");
        this.name = name.trim();
        this.authority = authority == null || authority.isBlank() ? null : authority.trim();
    }

    public void setActive(boolean on) { this.active = on; }

    public void recordSuccess(int newItems, Instant at) {
        lastFetchedAt = at;
        lastStatus = FetchStatus.OK;
        lastError = null;
        lastNewItems = Math.max(0, newItems);
        consecutiveFailures = 0;
    }

    public void recordFailure(String error, Instant at) {
        lastFetchedAt = at;
        lastStatus = FetchStatus.FAILED;
        lastError = error == null ? "Unknown error" : error.length() > 500 ? error.substring(0, 500) : error;
        lastNewItems = 0;
        consecutiveFailures++;
    }

    /** True only on the failure that crosses the threshold, so the tenant is told once rather than every day. */
    public boolean justBecameAlertable() { return lastStatus == FetchStatus.FAILED && consecutiveFailures == FAILURES_BEFORE_ALERT; }
}
