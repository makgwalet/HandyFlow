// fuel/package-info.java
@ApplicationModule(allowedDependencies = {"shared", "billing", "crm", "identity", "notifications", "notifications::*", "approvals", "approvals::*", "invoicing", "invoicing::*", "hr", "hr::*", "ap", "ap::*", "bookings", "bookings::*"})
package za.co.handyflow.platform.fuel;

import org.springframework.modulith.ApplicationModule;