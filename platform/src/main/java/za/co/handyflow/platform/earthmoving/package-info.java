
@ApplicationModule(allowedDependencies = {"shared", "billing", "crm", "projects", "invoicing", "invoicing::*", "notifications", "notifications::*", "audittrail", "audittrail::*", "contracting", "contracting::*", "documents", "documents::*"})
package za.co.handyflow.platform.earthmoving;

import org.springframework.modulith.ApplicationModule;