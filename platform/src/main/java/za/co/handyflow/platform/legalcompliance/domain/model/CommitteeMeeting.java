package za.co.handyflow.platform.legalcompliance.domain.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import za.co.handyflow.platform.shared.AggregateRoot;
import za.co.handyflow.platform.shared.TenantId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A committee meeting: SCHEDULED → HELD (with attendance, so quorum is known) or CANCELLED (with a reason). Minutes are written after the meeting (DRAFT)
 * and approved by someone other than the author (APPROVED, after which they cannot change). Decisions and actions can be recorded only for a meeting that
 * was held with a quorum. Quorum counts attendees who are active, non-observer members of the committee; the service passes that count in.
 */
@Entity
@Table(name = "legalcompliance_committee_meetings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CommitteeMeeting extends AggregateRoot<CommitteeMeeting> {

    public static final int MIN_TEXT = 15, MIN_MINUTES = 30;

    @Column(name = "committee_id", nullable = false) private UUID committeeId;
    @Column(name = "meeting_date", nullable = false) private LocalDate meetingDate;
    @Column(nullable = false, length = 255) private String title;
    @Column(columnDefinition = "TEXT") private String agenda;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private MeetingStatus status = MeetingStatus.SCHEDULED;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb") private List<String> attendance;
    @Column(name = "present_count") private Integer presentCount;
    @Column(name = "quorum_met") private Boolean quorumMet;
    @Column(name = "cancel_reason", columnDefinition = "TEXT") private String cancelReason;
    @Column(columnDefinition = "TEXT") private String minutes;
    @Enumerated(EnumType.STRING) @Column(name = "minutes_status", nullable = false, length = 10) private MinutesStatus minutesStatus = MinutesStatus.NONE;
    @Column(name = "minutes_by") private UUID minutesBy;
    @Column(name = "minutes_by_name", length = 255) private String minutesByName;
    @Column(name = "minutes_approved_by_name", length = 255) private String minutesApprovedByName;
    @Column(name = "minutes_approved_on") private LocalDate minutesApprovedOn;

    public static CommitteeMeeting schedule(TenantId tenantId, UUID committeeId, LocalDate date, String title, String agenda) {
        CommitteeMeeting m = new CommitteeMeeting();
        m.initTenantId(tenantId);
        m.committeeId = committeeId;
        m.apply(date, title, agenda);
        return m;
    }

    public void update(LocalDate date, String title, String agenda) {
        if (status != MeetingStatus.SCHEDULED) throw new IllegalStateException("Only a scheduled meeting can be edited — current: " + status);
        apply(date, title, agenda);
    }

    private void apply(LocalDate date, String title, String agenda) {
        if (date == null) throw new IllegalArgumentException("The meeting date is required");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A title is required");
        this.meetingDate = date;
        this.title = title.trim();
        this.agenda = agenda == null || agenda.isBlank() ? null : agenda.trim();
    }

    public void cancel(String reason) {
        if (status != MeetingStatus.SCHEDULED) throw new IllegalStateException("Only a scheduled meeting can be cancelled — current: " + status);
        if (reason == null || reason.trim().length() < MIN_TEXT) throw new IllegalArgumentException("Say why the meeting was cancelled (at least " + MIN_TEXT + " characters)");
        this.cancelReason = reason.trim();
        this.status = MeetingStatus.CANCELLED;
    }

    /** attendeeIds: the member ids marked present; qualifying: how many of them count toward quorum; quorumMin: the committee's minimum. */
    public void hold(List<String> attendeeIds, int qualifying, int quorumMin, LocalDate today) {
        if (status != MeetingStatus.SCHEDULED) throw new IllegalStateException("Only a scheduled meeting can be recorded as held — current: " + status);
        if (meetingDate.isAfter(today)) throw new IllegalStateException("This meeting is dated " + meetingDate + ", which has not happened yet");
        if (attendeeIds == null || attendeeIds.isEmpty()) throw new IllegalArgumentException("Record who attended");
        this.attendance = new ArrayList<>(attendeeIds);
        this.presentCount = qualifying;
        this.quorumMet = qualifying >= quorumMin && quorumMin >= 1;
        this.status = MeetingStatus.HELD;
    }

    public void recordMinutes(String text, UUID by, String byName) {
        if (status != MeetingStatus.HELD) throw new IllegalStateException("Minutes can be recorded only for a meeting that was held");
        if (minutesStatus == MinutesStatus.APPROVED) throw new IllegalStateException("Approved minutes cannot be changed");
        if (text == null || text.trim().length() < MIN_MINUTES) throw new IllegalArgumentException("The minutes need at least " + MIN_MINUTES + " characters");
        this.minutes = text.trim();
        this.minutesBy = by;
        this.minutesByName = byName;
        this.minutesStatus = MinutesStatus.DRAFT;
    }

    public void approveMinutes(UUID approverId, String approverName, LocalDate today) {
        if (minutesStatus != MinutesStatus.DRAFT) throw new IllegalStateException("There are no draft minutes to approve");
        if (approverId != null && approverId.equals(minutesBy)) throw new IllegalStateException("Segregation of duties: the person who wrote the minutes cannot approve them");
        this.minutesApprovedByName = approverName;
        this.minutesApprovedOn = today;
        this.minutesStatus = MinutesStatus.APPROVED;
    }

    public boolean canRecordResolutions() { return status == MeetingStatus.HELD && Boolean.TRUE.equals(quorumMet) && minutesStatus != MinutesStatus.APPROVED; }
}
