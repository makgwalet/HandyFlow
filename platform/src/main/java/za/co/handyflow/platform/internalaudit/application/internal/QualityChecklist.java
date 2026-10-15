package za.co.handyflow.platform.internalaudit.application.internal;

import java.util.*;

/** The methodology checklist an engagement quality review answers. Built in; each item is a plain yes/no question. */
public final class QualityChecklist {
    private QualityChecklist() {}

    public record Item(String code, String area, String question) {}

    private static final List<Item> ITEMS = List.of(
            new Item("P1", "PLANNING", "Are the objectives, scope and audit criteria documented?"),
            new Item("P2", "PLANNING", "Is the risk assessment current and linked to the plan entry?"),
            new Item("P3", "PLANNING", "Are overall, performance and trivial materiality set?"),
            new Item("P4", "PLANNING", "Is the team independent of the area audited, and is that recorded?"),
            new Item("F1", "FIELDWORK", "Is the population and selection method recorded for every sample?"),
            new Item("F2", "FIELDWORK", "Does every test have a recorded result?"),
            new Item("F3", "FIELDWORK", "Has every exception been followed up?"),
            new Item("F4", "FIELDWORK", "Have all workpapers been reviewed and signed off?"),
            new Item("F5", "FIELDWORK", "Are all workpaper review notes cleared?"),
            new Item("R1", "REPORTING", "Does every finding state criteria, condition and consequence?"),
            new Item("R2", "REPORTING", "Is every finding supported by evidence in the workpapers?"),
            new Item("R3", "REPORTING", "Has management responded to every finding?"),
            new Item("R4", "REPORTING", "Was the factual-accuracy review completed?"),
            new Item("R5", "REPORTING", "Is the conclusion supported by the work done?"));

    public static List<Item> items() { return ITEMS; }
    public static Optional<Item> find(String code) { return ITEMS.stream().filter(i -> i.code().equalsIgnoreCase(code == null ? "" : code)).findFirst(); }
}
