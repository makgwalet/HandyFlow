// What the signed-in user may do in the Security module, from their permission list. Mirrors the server (V845/V846):
// the server is what enforces it; this only keeps the UI from offering buttons that would answer 403.

export interface SecurityAccess {
  payrollRead: boolean
  payrollManage: boolean
  payrollApprove: boolean
  payrollPay: boolean
  armouryIssue: boolean
  screeningDecide: boolean
  bankingManage: boolean
  integrationsManage: boolean
  /** SECURITY_ADMIN, which may still revoke an API key. */
  securityAdmin: boolean
}

export function securityAccess(permissions: readonly string[] | null | undefined): SecurityAccess {
  const has = (p: string) => !!permissions && permissions.includes(p)
  return {
    payrollRead: has("SECURITY_PAYROLL_READ"),
    payrollManage: has("SECURITY_PAYROLL_MANAGE"),
    payrollApprove: has("SECURITY_PAYROLL_APPROVE"),
    payrollPay: has("SECURITY_PAYROLL_PAY"),
    armouryIssue: has("SECURITY_ARMOURY_ISSUE"),
    screeningDecide: has("SECURITY_SCREENING_DECIDE"),
    bankingManage: has("SECURITY_GUARD_BANKING_MANAGE"),
    integrationsManage: has("SECURITY_INTEGRATIONS_MANAGE"),
    securityAdmin: has("SECURITY_ADMIN"),
  }
}
