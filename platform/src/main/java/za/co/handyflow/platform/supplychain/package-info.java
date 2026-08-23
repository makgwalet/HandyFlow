@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "shared",
                "identity",
                "billing",
                "approvals",
                "notifications"
        }
)
package za.co.handyflow.platform.supplychain;
