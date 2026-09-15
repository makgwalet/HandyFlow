@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "shared",
                "identity",
                "billing",
                "approvals",
                "notifications",
                // FIX (Supply Chain -> AP hand-off, product owner's own
                // explicit design decision): needed for ApFacade
                // .createBillFromSupplyChainInvoice() — see ScmService's
                // own comment on approveSupplierInvoice() for the fuller
                // design.
                "ap"
        }
)
package za.co.handyflow.platform.supplychain;
