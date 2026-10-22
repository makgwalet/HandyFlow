package za.co.handyflow.platform.hr;

import java.math.BigDecimal;

/**
 * An employee's pay as one number and how often it is paid. For modules that cost labour. It is a separate call from
 * {@link EmployeeSummary} so that only the code that needs a salary ever receives one.
 */
public record EmployeePay(BigDecimal grossSalary, String payFrequency) {}
