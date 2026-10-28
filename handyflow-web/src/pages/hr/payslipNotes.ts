// What a payslip row should say about how the period was paid, from the fields the pay run stores.
// Plain numbers in, short sentences out; nothing is shown for an ordinary full month.

export interface PayslipPeriodFields {
  daysInPeriod?: number | null
  daysPaid?: number | string | null
  prorationAmount?: number | string | null
  unpaidLeaveDays?: number | string | null
  unpaidLeaveAmount?: number | string | null
  leavePayoutDays?: number | string | null
  leavePayoutAmount?: number | string | null
  finalPay?: boolean | null
  bonusAmount?: number | string | null
  backPayAmount?: number | string | null
  noticePayAmount?: number | string | null
  severanceAmount?: number | string | null
  severanceTax?: number | string | null
  etiAmount?: number | string | null
  etiMonth?: number | null
}

const num = (v: unknown): number => {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? n : 0
}

const money = (v: number): string => `R${v.toLocaleString('en-ZA', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export function payslipNotes(p: PayslipPeriodFields): string[] {
  const out: string[] = []
  if (p.finalPay) out.push('Final pay')
  const inPeriod = num(p.daysInPeriod)
  const paid = num(p.daysPaid)
  if (inPeriod > 0 && paid !== inPeriod) out.push(`Paid ${paid} of ${inPeriod} working days`)
  const unpaid = num(p.unpaidLeaveDays)
  if (unpaid > 0) out.push(`${unpaid} unpaid leave day${unpaid === 1 ? '' : 's'} deducted (${money(num(p.unpaidLeaveAmount))})`)
  const payout = num(p.leavePayoutAmount)
  if (payout > 0) out.push(`${num(p.leavePayoutDays)} annual leave day${num(p.leavePayoutDays) === 1 ? '' : 's'} paid out (${money(payout)})`)
  const bonus = num(p.bonusAmount)
  if (bonus > 0) out.push(`Bonus of ${money(bonus)} included`)
  const back = num(p.backPayAmount)
  if (back > 0) out.push(`Back pay of ${money(back)} included`)
  const notice = num(p.noticePayAmount)
  if (notice > 0) out.push(`Pay in place of notice of ${money(notice)} included`)
  const sev = num(p.severanceAmount)
  if (sev > 0) out.push(`Severance of ${money(sev)} included, less ${money(num(p.severanceTax))} tax as directed by SARS`)
  const eti = num(p.etiAmount)
  if (eti > 0) out.push(`Employment tax incentive of ${money(eti)} claimed (month ${p.etiMonth ?? '?'} of 24)`)
  return out
}
