package za.co.handyflow.platform.shared;

import java.util.Optional;

/**
 * Where payroll gets the approved statutory rules of a tax year. One implementation (the HR module's
 * {@code StatutoryRulesService}) owns the tables; HR and Payroll Bureau both ask through this interface, so neither
 * module depends on the other and the rules are kept in one place.
 */
public interface StatutoryRulesProvider {

    /** The approved rules of the tax year, or an {@link IllegalStateException} saying why there are none. No fallback to another year. */
    StatutoryYearRules rulesFor(int taxYear);

    Optional<StatutoryYearRules> find(int taxYear);
}
