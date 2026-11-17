@org.springframework.modulith.ApplicationModule(
        allowedDependencies = {
                "shared",
                "identity",
                "billing",
                "approvals",
                "notifications",
                // Supply Chain -> AP hand-off (ApFacade.createBillFromSupplyChainInvoice)
                "ap",
                // ControlExceptionFacade
                "controls",
                // NEW (chunk 5): supplier compliance documents are stored through EvidenceFacade
                "evidence"
        }
)
package za.co.handyflow.platform.supplychain;
