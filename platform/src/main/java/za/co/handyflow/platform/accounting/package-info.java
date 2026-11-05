@ApplicationModule(allowedDependencies = {"shared", "identity", "identity::*", "crm", "invoicing", "invoicing::*", "audittrail", "audittrail::*"})
package za.co.handyflow.platform.accounting;

import org.springframework.modulith.ApplicationModule;