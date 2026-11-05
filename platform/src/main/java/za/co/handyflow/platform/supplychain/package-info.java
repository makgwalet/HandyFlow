@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "shared",
                "identity",
                "billing",
                "approvals", "approvals::*",
                "notifications", "notifications::*",
                // FIX (Supply Chain -> AP hand-off, product owner's own
                // explicit design decision): needed for ApFacade
                // .createBillFromSupplyChainInvoice() — see ScmService's
                // own comment on approveSupplierInvoice() for the fuller
                // design.
                "ap", "ap::*",
                // Raises control exceptions through ControlExceptionFacade.
                "controls", "controls::*"
        }
)
package za.co.handyflow.platform.supplychain;
