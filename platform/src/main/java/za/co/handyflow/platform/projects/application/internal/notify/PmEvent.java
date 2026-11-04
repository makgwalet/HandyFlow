package za.co.handyflow.platform.projects.application.internal.notify;

import za.co.handyflow.platform.notifications.domain.model.NotificationType;

/**
 * Every Projects event that notifies someone, who it goes to, and whether it emails by default.
 * The in-app bell always gets it; email can be switched off per person per event.
 */
public enum PmEvent {
    TASK_ASSIGNED("A task is assigned to you", true, NotificationType.PM_TASK_ASSIGNED),
    TASK_REVIEW_REQUESTED("A task is waiting for your review (project managers)", true, NotificationType.PM_TASK_REVIEW_REQUESTED),
    TASK_RETURNED("Your task was sent back from review", true, NotificationType.PM_TASK_RETURNED),
    TASK_OVERDUE("Your task is past its end date", true, NotificationType.PM_TASK_OVERDUE),
    MILESTONE_DUE_SOON("A milestone is due in 3 days (assignee and project managers)", false, NotificationType.PM_MILESTONE_DUE_SOON),
    CHANGE_ORDER_SUBMITTED("A change order needs approval (sponsors and project managers)", true, NotificationType.PM_CHANGE_ORDER_SUBMITTED),
    CHANGE_ORDER_DECIDED("Your change order was approved or rejected", true, NotificationType.PM_CHANGE_ORDER_DECIDED),
    RFI_ANSWERED("Your RFI was answered", true, NotificationType.PM_RFI_ANSWERED),
    TIME_REJECTED("Your time entry was rejected", true, NotificationType.PM_TIME_REJECTED),
    ISSUE_ASSIGNED("An issue is assigned to you", true, NotificationType.PM_ISSUE_ASSIGNED),
    ACTION_ASSIGNED("An action item is assigned to you", false, NotificationType.PM_ACTION_ASSIGNED),
    ACTION_OVERDUE("Your action item is overdue", true, NotificationType.PM_ACTION_OVERDUE),
    PROJECT_PROPOSED("A project is submitted for approval (sponsor)", true, NotificationType.PM_PROJECT_PROPOSED),
    PROJECT_DECIDED("A project you manage was approved or returned", true, NotificationType.PM_PROJECT_DECIDED),
    CLIENT_REQUEST("A client sent a question, change request or new-project request (project managers)", true, NotificationType.PM_CLIENT_REQUEST),
    CLIENT_CO_DECISION("A client approved or disputed a change order (project managers and its originator)", true, NotificationType.PM_CLIENT_CO_DECISION),
    CLIENT_UPLOAD("A client sent a file for review (project managers and document controllers)", false, NotificationType.PM_CLIENT_UPLOAD);

    private final String label;
    private final boolean emailByDefault;
    private final NotificationType type;

    PmEvent(String label, boolean emailByDefault, NotificationType type) {
        this.label = label; this.emailByDefault = emailByDefault; this.type = type;
    }

    public String label() { return label; }
    public boolean emailByDefault() { return emailByDefault; }
    public NotificationType type() { return type; }
}
