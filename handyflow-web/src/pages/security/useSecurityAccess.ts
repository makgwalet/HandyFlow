import { usePermission } from "../../hooks/usePermission"
import type { SecurityAccess } from "./securityAccess"

/** The signed-in user's access to the split-out Security actions (see securityAccess.ts). */
export function useSecurityAccess(): SecurityAccess {
  return {
    payrollRead: usePermission("SECURITY_PAYROLL_READ"),
    payrollManage: usePermission("SECURITY_PAYROLL_MANAGE"),
    payrollApprove: usePermission("SECURITY_PAYROLL_APPROVE"),
    payrollPay: usePermission("SECURITY_PAYROLL_PAY"),
    armouryIssue: usePermission("SECURITY_ARMOURY_ISSUE"),
    screeningDecide: usePermission("SECURITY_SCREENING_DECIDE"),
    bankingManage: usePermission("SECURITY_GUARD_BANKING_MANAGE"),
    integrationsManage: usePermission("SECURITY_INTEGRATIONS_MANAGE"),
    securityAdmin: usePermission("SECURITY_ADMIN"),
  }
}
