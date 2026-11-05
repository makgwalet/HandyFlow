// security/package-info.java
@ApplicationModule(allowedDependencies = {"shared", "billing", "crm", "notifications", "notifications::*", "identity", "evidence", "evidence::*", "hr", "hr::*"})
package za.co.handyflow.platform.security;

import org.springframework.modulith.ApplicationModule;