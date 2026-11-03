/**
 * Projects may use: shared, approvals, notifications, evidence (RFI / change-order / document files) and
 * documents (only its DocumentScanner hook, so one malware scanner covers project files too).
 */
@ApplicationModule(allowedDependencies = {"shared", "approvals", "notifications", "evidence", "documents"})
package za.co.handyflow.platform.projects;

import org.springframework.modulith.ApplicationModule;