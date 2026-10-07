// security/package-info.java
@ApplicationModule(allowedDependencies = {"shared", "billing", "crm", "notifications", "identity", "evidence", "hr"})
package za.co.handyflow.platform.security;

import org.springframework.modulith.ApplicationModule;