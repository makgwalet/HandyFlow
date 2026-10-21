package za.co.handyflow.platform.crm.domain.model;

/** What a workflow does. None of these starts another workflow. */
public enum WorkflowAction { CREATE_TASK, ADD_TAG, ASSIGN_OWNER, NOTIFY_OWNER }
