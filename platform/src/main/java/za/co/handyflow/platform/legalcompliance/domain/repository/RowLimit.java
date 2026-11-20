package za.co.handyflow.platform.legalcompliance.domain.repository;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * Hard ceiling on the rows one list screen can pull. Lists that grow for as long as the company exists (incidents,
 * findings, declarations, risks...) are read newest-first through {@code searchCapped}, so the first screen stays fast
 * after years of data. Summaries, dashboards, coverage and overnight sweeps keep calling the uncapped {@code search}:
 * they count everything, and a silent cap there would make the numbers wrong. The web client shows a notice when a list
 * comes back at this size (see RowLimitNotice).
 */
public final class RowLimit {

    public static final int MAX_ROWS = 500;

    private RowLimit() {}

    public static Pageable first() {
        return PageRequest.of(0, MAX_ROWS);
    }
}
