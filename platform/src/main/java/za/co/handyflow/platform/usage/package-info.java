/**
 * Usage metering: what a business has used this month against what its tier allows. REPORT MODE: counts and
 * warnings only, nothing is blocked. Invoices are counted by a database trigger (V594) into an append-only table.
 */
@org.springframework.modulith.ApplicationModule(allowedDependencies = {"shared"})
package za.co.handyflow.platform.usage;
