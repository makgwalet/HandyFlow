import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { useAuthStore } from "../../store/auth.store"
import { btnGhost, btnPrimary, Chip, ErrorLine, inputStyle, labelStyle, Modal, ReasonModal } from "../warehousing/whseUi"
import { ACCOUNT_TYPES, STATUS_LABEL, canDecide, canWithdraw, emptyBank, pendingOf, toProposal, validateBank, verifiedOf, type BankAccount, type BankForm } from "./supplierBankOps"

const unwrap = (r: any) => r.data?.data ?? r.data
const when = (iso: string) => new Date(iso).toLocaleString("en-ZA", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })

/**
 * A supplier's bank details. Lists show only the last four digits. A change is proposed by one person and verified by another,
 * and only a verified account counts. The full number is shown on request to someone with the read permission, and is kept only
 * on this screen, never in the query cache.
 */
export default function SupplierBankDialog({ supplierId, supplierName, onClose }: { supplierId: string; supplierName: string; onClose: () => void }) {
  const qc = useQueryClient()
  const canEdit = usePermissions(["FUEL_SUPPLIER_BANK_EDIT"])
  const canVerify = usePermissions(["FUEL_SUPPLIER_BANK_VERIFY"])
  const canRead = usePermissions(["FUEL_SUPPLIER_BANK_READ"])
  const me = useAuthStore(s => s.user?.userId) as string | undefined
  const key = ["fuel-supplier-bank", supplierId]
  const base = `/api/v1/fuel/suppliers/${supplierId}/bank-accounts`
  const q = useQuery<BankAccount[]>({ queryKey: key, queryFn: async () => unwrap(await apiClient.get(base)) })
  const list = Array.isArray(q.data) ? q.data : []
  const verified = verifiedOf(list), pending = pendingOf(list)
  const [form, setForm] = useState<BankForm | null>(null)
  const [problem, setProblem] = useState("")
  const [deciding, setDeciding] = useState<{ a: BankAccount; how: "verify" | "reject" } | null>(null)
  const [shown, setShown] = useState<Record<string, string>>({})
  const [revealError, setRevealError] = useState("")
  const done = () => { qc.invalidateQueries({ queryKey: key }); setForm(null); setDeciding(null); setProblem("") }
  const propose = useMutation({ mutationFn: (f: BankForm) => apiClient.post(base, toProposal(f)), onSuccess: done })
  const decide = useMutation({ mutationFn: (a: { id: string; how: string; note: string }) => apiClient.post(`${base}/${a.id}/${a.how}`, { note: a.note }), onSuccess: done })
  const withdraw = useMutation({ mutationFn: (id: string) => apiClient.post(`${base}/${id}/withdraw`), onSuccess: done })
  const reveal = async (a: BankAccount) => {
    setRevealError("")
    if (shown[a.id]) return setShown(({ [a.id]: _gone, ...rest }) => rest)
    try { const r = unwrap(await apiClient.get(`${base}/${a.id}/reveal`)); setShown(s => ({ ...s, [a.id]: r.accountNumber })) }
    catch { setRevealError("Could not show the account number.") }
  }
  const set = (k: keyof BankForm) => (e: React.ChangeEvent<HTMLInputElement | HTMLSelectElement>) => { setProblem(""); setForm(f => f && ({ ...f, [k]: e.target.value })) }

  return (
    <Modal title={`Bank details: ${supplierName}`} onClose={onClose} width={620}>
      {q.isLoading ? <p style={{ color: "var(--hf-text-muted)" }}>Loading…</p> : q.isError ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>Could not load bank details.</p> : (
        <>
          <p style={{ fontSize: 12.5, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>
            A change is not used until a different person has checked it with the supplier and verified it. The old details are kept as history.
          </p>
          {!verified && <p role="status" style={{ fontSize: 13, color: "var(--hf-warning-text)", margin: "0 0 10px" }}>No verified bank details yet.</p>}
          {list.length === 0 && <p style={{ fontSize: 13 }}>Nothing recorded yet.</p>}
          {list.map(a => (
            <div key={a.id} style={{ padding: "8px 0", borderTop: "1px solid var(--hf-border)", opacity: a.status === "SUPERSEDED" || a.status === "REJECTED" || a.status === "WITHDRAWN" ? 0.65 : 1 }}>
              <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                <strong style={{ fontSize: 13 }}>{a.bankName}</strong>
                <Chip bg={a.status === "VERIFIED" ? "var(--hf-success-soft-strong)" : a.status === "PENDING_VERIFICATION" ? "var(--hf-warning-soft)" : "var(--hf-surface-alt)"}
                  fg={a.status === "VERIFIED" ? "var(--hf-success-text-strong)" : a.status === "PENDING_VERIFICATION" ? "var(--hf-warning-text)" : "var(--hf-text-muted)"}>{STATUS_LABEL[a.status] ?? a.status}</Chip>
                <span style={{ flex: 1 }} />
                {canRead && <button style={btnGhost} onClick={() => reveal(a)} aria-label={`${shown[a.id] ? "Hide" : "Show"} number ${a.accountNumberMasked}`}>{shown[a.id] ? "Hide number" : "Show number"}</button>}
                {canVerify && canDecide(a, me) && <>
                  <button style={btnPrimary} onClick={() => setDeciding({ a, how: "verify" })} aria-label={`Verify ${a.accountNumberMasked}`}>Verify</button>
                  <button style={btnGhost} onClick={() => setDeciding({ a, how: "reject" })} aria-label={`Reject ${a.accountNumberMasked}`}>Reject</button></>}
                {canEdit && canWithdraw(a, me) && <button style={btnGhost} disabled={withdraw.isPending} onClick={() => withdraw.mutate(a.id)} aria-label={`Withdraw ${a.accountNumberMasked}`}>Withdraw</button>}
              </div>
              <div style={{ fontSize: 12.5, color: "var(--hf-text)", marginTop: 3 }}>
                {a.accountHolder} · {ACCOUNT_TYPES.find(t => t.code === a.accountType)?.label ?? a.accountType} · <span data-testid={`acct-${a.id}`}>{shown[a.id] ?? a.accountNumberMasked}</span>{a.branchCode ? ` · branch ${a.branchCode}` : ""}
              </div>
              <div style={{ fontSize: 11.5, color: "var(--hf-text-muted)" }}>
                Proposed {when(a.proposedAt)}{a.reason ? ` · ${a.reason}` : ""}{a.decidedAt ? ` · Decided ${when(a.decidedAt)}${a.decisionNote ? `: ${a.decisionNote}` : ""}` : ""}
                {a.status === "PENDING_VERIFICATION" && me && a.proposedBy === me ? " · Waiting for someone else to verify" : ""}
              </div>
            </div>
          ))}
          {revealError && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12 }}>{revealError}</p>}
          <ErrorLine error={withdraw.error} />
          {canEdit && !pending && !form && <button style={{ ...btnPrimary, marginTop: 12 }} onClick={() => setForm(emptyBank())}>{verified ? "Propose new bank details" : "Add bank details"}</button>}
          {canEdit && pending && !form && <p style={{ fontSize: 12, color: "var(--hf-text-muted)", marginTop: 12 }}>Another change cannot be proposed until the waiting one is decided or withdrawn.</p>}
          {form && (
            <div style={{ marginTop: 12, borderTop: "1px solid var(--hf-border)", paddingTop: 12, display: "grid", gap: 8 }}>
              <div><label style={labelStyle} htmlFor="bk-holder">Account holder *</label><input id="bk-holder" style={{ ...inputStyle, width: "100%" }} value={form.accountHolder} onChange={set("accountHolder")} /></div>
              <div><label style={labelStyle} htmlFor="bk-bank">Bank *</label><input id="bk-bank" style={{ ...inputStyle, width: "100%" }} value={form.bankName} onChange={set("bankName")} /></div>
              <div style={{ display: "flex", gap: 8 }}>
                <div style={{ flex: 2 }}><label style={labelStyle} htmlFor="bk-number">Account number *</label><input id="bk-number" style={{ ...inputStyle, width: "100%" }} inputMode="numeric" autoComplete="off" value={form.accountNumber} onChange={set("accountNumber")} /></div>
                <div style={{ flex: 1 }}><label style={labelStyle} htmlFor="bk-branch">Branch code</label><input id="bk-branch" style={{ ...inputStyle, width: "100%" }} value={form.branchCode} onChange={set("branchCode")} /></div>
              </div>
              <div><label style={labelStyle} htmlFor="bk-type">Account type</label>
                <select id="bk-type" style={{ ...inputStyle, width: "100%" }} value={form.accountType} onChange={set("accountType")}>{ACCOUNT_TYPES.map(t => <option key={t.code} value={t.code}>{t.label}</option>)}</select></div>
              <div><label style={labelStyle} htmlFor="bk-reason">{verified ? "Why are the details changing? *" : "Note (optional)"}</label><input id="bk-reason" style={{ ...inputStyle, width: "100%" }} maxLength={300} value={form.reason} onChange={set("reason")} /></div>
              {problem && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12, margin: 0 }}>{problem}</p>}
              <ErrorLine error={propose.error} />
              <div style={{ display: "flex", gap: 8 }}>
                <button style={btnGhost} onClick={() => { setForm(null); setProblem("") }}>Cancel</button>
                <button style={btnPrimary} disabled={propose.isPending} onClick={() => { const p = validateBank(form, !!verified); if (p) return setProblem(p); propose.mutate(form) }}>Propose for verification</button>
              </div>
            </div>
          )}
        </>
      )}
      {deciding && (
        <ReasonModal title={deciding.how === "verify" ? `Verify ${deciding.a.accountNumberMasked}` : `Reject ${deciding.a.accountNumberMasked}`}
          intro={deciding.how === "verify" ? "Say how you confirmed these details, for example by calling the supplier on a number you already had (not one from the request)." : "Say why these details are being rejected."}
          confirmLabel={deciding.how === "verify" ? "Verify" : "Reject"} pending={decide.isPending} error={decide.error}
          onConfirm={note => decide.mutate({ id: deciding.a.id, how: deciding.how, note })} onClose={() => setDeciding(null)} />
      )}
    </Modal>
  )
}
