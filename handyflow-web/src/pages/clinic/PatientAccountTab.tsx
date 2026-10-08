// Patient account: what each visit cost, what the scheme and the patient have paid, and what is still owing.
// Charges are never added here as loose items: they come from the consultation and its claim, so the account always
// agrees with the claims and billing pages.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CreditCard, Download } from "lucide-react"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { fmtDay } from "./briefing"
import ModalShell from "./ModalShell"
import { BORDER, Empty, GRAY, LIGHT, RED_TEXT, downloadPdf, fmtR, lbl, sinp } from "./patientFile.shared"
import { primaryBtn, smallBtn } from "./OverviewCard"
import { claimStatus, isOwed, methodLabel, type Account } from "./accountView"

const unwrap = (r: any) => r?.data?.data ?? r?.data
const TONE = { ok: ["var(--hf-success-soft)", "var(--hf-success-text-strong)"], warn: ["var(--hf-warning-soft)", "var(--hf-warning-text)"],
  info: ["var(--hf-info-soft)", "var(--hf-info-text)"], muted: ["var(--hf-surface-sunken)", "var(--hf-text-muted)"],
  bad: ["var(--hf-danger-soft)", "var(--hf-danger-text)"] } as const

export default function PatientAccountTab({ patientId }: { patientId: string }) {
  const qc = useQueryClient()
  const canPay = usePermission("CLINIC_PAYMENT_CREATE")
  const key = ["pf-account", patientId]
  const { data: acc, isLoading, isError } = useQuery<Account>({ queryKey: key, retry: false,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/clinic/patients/${patientId}/account`)) })
  const [paying, setPaying] = useState(false)
  const [form, setForm] = useState({ amount: "", method: "CASH", reference: "" })
  const pay = useMutation({
    mutationFn: () => apiClient.post("/api/v1/clinic/billing/payments", { patientId, method: form.method, amount: Number(form.amount), reference: form.reference || undefined }),
    onSuccess: () => { setPaying(false); setForm({ amount: "", method: "CASH", reference: "" }); qc.invalidateQueries({ queryKey: key }) },
  })
  const payError = (pay.error as any)?.response?.data?.message ?? (pay.isError ? "The payment could not be saved." : null)
  const amountOk = Number(form.amount) > 0

  if (isLoading) return <div style={{ color: GRAY, fontSize: 13 }}>Loading the account…</div>
  if (isError || !acc) return <div role="alert" style={{ color: RED_TEXT, fontSize: 13 }}>The account could not be loaded.</div>

  const stat = (label: string, value: string, strong = false) => (
    <div style={{ flex: "1 1 150px", padding: "12px 14px", border: `1px solid ${BORDER}`, borderRadius: 10, background: strong ? LIGHT : "var(--hf-surface)" }}>
      <div style={{ fontSize: 11, fontWeight: 700, color: GRAY, textTransform: "uppercase", letterSpacing: "0.04em" }}>{label}</div>
      <div style={{ fontSize: strong ? 20 : 16, fontWeight: 700, color: "var(--hf-text)", marginTop: 4 }}>{value}</div>
    </div>
  )

  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 14, gap: 12, flexWrap: "wrap" }}>
        <div>
          <div style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)" }}>Patient account</div>
          <div style={{ fontSize: 12, color: GRAY, marginTop: 2 }}>Charges come from each consultation and its claim. To change a charge, open the visit or its claim.</div>
        </div>
        <div style={{ display: "flex", gap: 8 }}>
          <button type="button" style={smallBtn} onClick={() => downloadPdf(`/api/v1/clinic/billing/patients/${patientId}/statement-pdf`, `statement-${patientId}.pdf`)}>
            <Download size={12} /> Statement
          </button>
          {canPay && <button type="button" style={primaryBtn} onClick={() => setPaying(true)}><CreditCard size={13} /> Record payment</button>}
        </div>
      </div>

      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        {stat("Charged", fmtR(acc.totalCharged))}
        {stat("Scheme share", fmtR(acc.schemeShare))}
        {stat("Patient share", fmtR(acc.patientShare))}
        {stat("Paid by patient", fmtR(acc.paidByPatient))}
        {stat("Balance owing", fmtR(acc.balanceOwing), true)}
      </div>
      {acc.schemeOutstanding > 0 && <div style={{ fontSize: 12, color: GRAY, marginBottom: 10 }}>The scheme still owes {fmtR(acc.schemeOutstanding)} on submitted claims. That is not part of the patient's balance.</div>}
      {acc.visitsNotBilled > 0 && <div style={{ fontSize: 12, color: "var(--hf-warning-text)", marginBottom: 10 }}>
        {acc.visitsNotBilled} visit{acc.visitsNotBilled !== 1 ? "s have" : " has"} not been billed yet. Create the claim from the visit or on the Claims page.</div>}

      {acc.visits.length === 0 ? <Empty icon={CreditCard} msg="Nothing charged yet"><div style={{ fontSize: 13, color: GRAY, marginTop: 4 }}>Charges appear here once a consultation is billed.</div></Empty> : (
        <div style={{ border: `1px solid ${BORDER}`, borderRadius: 12, overflow: "hidden", marginBottom: 18 }}>
          <table style={{ width: "100%", borderCollapse: "collapse" }}>
            <thead><tr style={{ background: LIGHT, borderBottom: `1px solid ${BORDER}` }}>
              {["Visit", "Claim", "Charged", "Scheme", "Patient", "Paid"].map(h => <th key={h} style={{ padding: "9px 12px", textAlign: "left", fontSize: 11, fontWeight: 700, color: GRAY }}>{h}</th>)}
            </tr></thead>
            <tbody>{acc.visits.map(v => {
              const st = claimStatus(v.claimStatus), tone = TONE[st.tone], owed = isOwed(v.claimStatus)
              const num = (n?: number | null) => n == null ? "—" : fmtR(n)
              const strike = !owed && v.claimStatus !== "NOT_BILLED" ? { textDecoration: "line-through", color: GRAY } : {}
              return (
                <tr key={v.consultationId} style={{ borderBottom: `1px solid ${BORDER}` }}>
                  <td style={{ padding: "10px 12px", fontSize: 13 }}>
                    <div style={{ fontWeight: 600, color: "var(--hf-text)" }}>{v.visitDate ? fmtDay(v.visitDate) : "—"}</div>
                    <div style={{ fontSize: 12, color: GRAY }}>{v.chiefComplaint || "No reason recorded"}</div>
                  </td>
                  <td style={{ padding: "10px 12px", fontSize: 12 }}>
                    <span style={{ padding: "2px 8px", borderRadius: 999, fontWeight: 600, background: tone[0], color: tone[1] }}>{st.label}</span>
                    {v.schemeName && <div style={{ color: GRAY, marginTop: 3 }}>{v.schemeName}</div>}
                    {v.claimId && v.claimStatus !== "NOT_BILLED" && (
                      <button type="button" onClick={() => downloadPdf(`/api/v1/clinic/billing/claims/${v.claimId}/patient-invoice-pdf`, `invoice-${v.claimId}.pdf`)}
                        style={{ ...smallBtn, marginTop: 4 }}>Invoice</button>)}
                  </td>
                  <td style={{ padding: "10px 12px", fontSize: 13, ...strike }}>{num(v.charged)}</td>
                  <td style={{ padding: "10px 12px", fontSize: 13, ...strike }}>{num(v.schemePortion)}</td>
                  <td style={{ padding: "10px 12px", fontSize: 13, ...strike }}>{num(v.patientPortion)}</td>
                  <td style={{ padding: "10px 12px", fontSize: 13 }}>{v.paidAgainstVisit ? fmtR(v.paidAgainstVisit) : "—"}</td>
                </tr>)
            })}</tbody>
          </table>
        </div>
      )}

      <div style={{ fontSize: 13, fontWeight: 700, color: "var(--hf-text)", marginBottom: 8 }}>Payments received</div>
      {acc.payments.length === 0 ? <div style={{ fontSize: 13, color: GRAY }}>No payments recorded.</div> : (
        <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
          {acc.payments.map(p => (
            <div key={p.id} style={{ display: "flex", justifyContent: "space-between", gap: 12, padding: "8px 12px", border: `1px solid ${BORDER}`, borderRadius: 8, fontSize: 13 }}>
              <span>{p.paidAt ? fmtDay(p.paidAt) : "—"} · {methodLabel(p.method)}{p.reference ? ` · ${p.reference}` : ""}</span>
              <strong>{fmtR(p.amount)}</strong>
            </div>))}
        </div>
      )}

      {paying && (
        <ModalShell title="Record payment" onClose={() => setPaying(false)} width={420}
          footer={<><button type="button" style={smallBtn} onClick={() => setPaying(false)}>Cancel</button>
            <button type="button" style={{ ...primaryBtn, opacity: amountOk && !pay.isPending ? 1 : 0.6 }} disabled={!amountOk || pay.isPending} onClick={() => pay.mutate()}>Save payment</button></>}>
          <label style={lbl}>Amount (R)</label>
          <input aria-label="Amount" style={sinp} type="number" min="0" step="0.01" value={form.amount} onChange={e => setForm(f => ({ ...f, amount: e.target.value }))} />
          <label style={{ ...lbl, marginTop: 10 }}>Method</label>
          <select aria-label="Method" style={sinp} value={form.method} onChange={e => setForm(f => ({ ...f, method: e.target.value }))}>
            <option value="CASH">Cash</option><option value="EFT">EFT</option><option value="CARD">Card</option>
          </select>
          <label style={{ ...lbl, marginTop: 10 }}>Reference (optional)</label>
          <input aria-label="Reference" style={sinp} value={form.reference} onChange={e => setForm(f => ({ ...f, reference: e.target.value }))} />
          {payError && <div role="alert" style={{ color: RED_TEXT, fontSize: 13, marginTop: 8 }}>{payError}</div>}
        </ModalShell>
      )}
    </div>
  )
}
