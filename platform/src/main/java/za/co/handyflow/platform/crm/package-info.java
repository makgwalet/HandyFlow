// crm/package-info.java
@ApplicationModule(allowedDependencies = {"shared", "billing", "notifications", "notifications::*"})
package za.co.handyflow.platform.crm;

import org.springframework.modulith.ApplicationModule;