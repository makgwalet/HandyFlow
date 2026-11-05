@ApplicationModule(allowedDependencies = {"shared", "billing", "crm", "catalogue", "identity", "notifications", "notifications::*", "approvals", "approvals::*", "audittrail", "audittrail::*"})
package za.co.handyflow.platform.invoicing;

import org.springframework.modulith.ApplicationModule;