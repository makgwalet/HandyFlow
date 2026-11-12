# Fuel B04 / B05: supplier banking details and purchase requisitions (patch 0793)

## Implemented
**Supplier banking (B04)**
- A supplier's bank account is never edited. A change is proposed, then verified (or rejected) by a DIFFERENT person, who must say how they checked it. Only a verified account counts; verifying a new one retires the old one, which is kept as history. Only one proposal can wait at a time; only its proposer can withdraw it.
- Lists show only the last four digits. The full number is shown on request to FUEL_SUPPLIER_BANK_READ and each look is logged with who and which record (never the number). The screen keeps it in memory only, not in the query cache.
- Replacing verified details needs a reason; proposing the same details again is refused.
- Three new permissions: FUEL_SUPPLIER_BANK_EDIT, _VERIFY, _READ. Only ADMIN is given them. FUEL_MANAGE and the area permissions do not open banking.

**Requisitions (B05)**
- A request to buy fuel (REQ-YYYY-NNNNN): draft, submitted to the Approvals engine (entity type REQUISITION, with value, litres and fuel type), approved, then converted to a DRAFT purchase order, which goes through the purchase order's own approval when placed. A rejection returns it to draft with the reason. Cancel needs a reason.
- Submitting needs an estimated price so approval can judge the value. No thresholds are built in: with no approval rule a requisition is approved straight away, as purchase orders already behave.
- Converting locks the requisition first, so two clicks make one order. If the order is refused (for example the supplier is suspended) the requisition stays approved.

## Preserved
Supplier status and approval, purchase orders and their approval, supplier statements and invoices are unchanged.

## Files
- DB: V676 (permissions; fuel_supplier_bank_accounts with checks including decided_by <> proposed_by and one verified and one waiting account per supplier; fuel_requisition_sequences; fuel_purchase_requisitions).
- Java: FuelSupplierBankAccount, FuelPurchaseRequisition, two repositories, FuelSupplierBankService, FuelRequisitionService, RequisitionNumberGenerator, SupplierBankDtos, RequisitionDtos, FuelSupplierBankController, FuelRequisitionController; FuelApprovalEventHandler and FuelDivisionRules ("requisitions" with purchasing) extended.
- Web: supplierBankOps, SupplierBankDialog (opened from a "Bank details" button on each supplier), requisitionOps, RequisitionsPanel (top of Purchase orders).

## API
/api/v1/fuel/suppliers/{id}/bank-accounts: GET (FUEL_READ, masked), POST propose (EDIT), POST /{id}/verify and /reject (VERIFY), POST /{id}/withdraw (EDIT), GET /{id}/reveal (READ, no-store).
/api/v1/fuel/requisitions: GET (FUEL_READ), POST, PUT /{id}, POST /{id}/submit, /convert, /cancel (FUEL_MANAGE or FUEL_PURCHASING_MANAGE). All behind the fuel module guard.

## Tests
- Java: FuelSupplierBankServiceTest (11), FuelRequisitionServiceTest (11), one new FuelEndpointSecurityTest case. Parse-checked only; not run.
- Web: supplierBankOps, requisitionOps, SupplierBankDialog, RequisitionsPanel, SuppliersTab. Fuel and navigation suite 384 pass; tsc clean.
- V676 verified on a scratch PostgreSQL 16 (self-verification, second waiting proposal, bad digits, undecided verified rows and bad requisition rows are all refused).

## Gaps and risks
- Account numbers are stored as plain text (like employee bank details elsewhere) and masked in views; there is no column encryption.
- Accounts Payable does not read the verified account yet, and Fuel does not pay suppliers.
- No hold period after a change and no supplier documents (for example a bank letter) or scorecard.
- The requisition screen has no tank picker (the API accepts a tank id).
- Run Maven including FuelEndpointSecurityTest, FuelRepositoryTenantScopingTest, PermissionConsistencyTest and ArchitectureVerificationTest.

## Next
F01 leftovers (0794).
