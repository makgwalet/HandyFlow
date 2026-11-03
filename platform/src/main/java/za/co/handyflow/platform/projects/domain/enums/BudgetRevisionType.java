package za.co.handyflow.platform.projects.domain.enums;

public enum BudgetRevisionType {
    REVISION,       // an existing line's budget was changed
    ADDITION,       // a new line was added after the baseline was locked
    TRANSFER_OUT,   // budget moved away from this line
    TRANSFER_IN     // budget moved onto this line
}