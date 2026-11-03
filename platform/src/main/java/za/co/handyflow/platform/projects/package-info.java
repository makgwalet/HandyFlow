/**
 * Projects may use: shared, approvals, notifications, evidence (RFI / change-order / document files) and
 * documents (only its DocumentScanner hook, so one malware scanner covers project files too) and
 * secureaccess (the platform Secure Document Access Engine that issues and checks recipient links).
 */
@ApplicationModule(allowedDependencies = {"shared", "approvals", "notifications", "evidence", "documents", "secureaccess"})
package za.co.handyflow.platform.projects;

import org.springframework.modulith.ApplicationModule;