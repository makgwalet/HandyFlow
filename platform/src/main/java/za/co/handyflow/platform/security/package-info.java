// security/package-info.java
@ApplicationModule(allowedDependencies = {"shared", "billing", "crm", "notifications", "identity"})
package za.co.handyflow.platform.security;

import org.springframework.modulith.ApplicationModule;