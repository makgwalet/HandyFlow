@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "shared",
                "identity",
                "billing",
                "approvals"
        }
)
package za.co.handyflow.platform.supplychain;
