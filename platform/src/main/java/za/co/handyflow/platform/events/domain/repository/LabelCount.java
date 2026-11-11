package za.co.handyflow.platform.events.domain.repository;

/** Interface projection for "SELECT x AS label, COUNT(...) AS total ... GROUP BY x" queries. */
public interface LabelCount {
    String getLabel();
    Long getTotal();
}