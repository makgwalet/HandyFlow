// Pieces both Insurance screens use: status and expiry badges, the renewal dialog, the renewal history, and form fields.
import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { errorText } from "../../api/projectDocs"
import { Btn, Dialog, ErrorLine, Field, Pill, day, inp, list, muted, th, td } from "../projects/tabs/pmKit"
import { STATUS_LABEL, expiryNote, renewalDefaults, statusTone, termProblem, termRequest, type TermForm } from "./insurance.logic"

export const money = (n: number | null | undefined) => (n == null ? "—" : `R ${n.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)

/** Pages come back either unwrapped or still in { data }; either way the rows are in content. */
export const pageOf = <T,>(r: any): { content: T[]; totalPages: number; totalElements: number } => {
  const d = r?.data?.content ? r.data : r?.data?.data
  return { content: d?.content ?? [], totalPages: d?.totalPages ?? 0, totalElements: d?.totalElements ?? 0 }
}

export function StatusPill({ status }: { status: string }) { return <Pill tone={statusTone(status)}>{STATUS_LABEL[status] ?? status}</Pill> }

export function ExpiryNote({ status, expiry }: { status: string; expiry: string | null }) {
  const n = expiryNote(status, expiry)
  return n ? <div style={{ marginTop: 3 }}><Pill tone={n.tone}>{n.text}</Pill></div> : null
}

export function Pager({ page, pages, onPage }: { page: number; pages: number; onPage: (p: number) => void }) {
  if (pages <= 1) return null
  return (
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: 10 }}>
      <Btn small disabled={page === 0} onClick={() => onPage(page - 1)}>← Previous</Btn>
      <span style={muted}>Page {page + 1} of {pages}</span>
      <Btn small disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>Next →</Btn>
    </div>
  )
}

export function Input({ label, hint, value, onChange, type = "text", ...rest }:
  { label: string; hint?: string; value: string; onChange: (v: string) => void; type?: string } & Omit<React.InputHTMLAttributes<HTMLInputElement>, "onChange" | "value" | "type">) {
  return <Field label={label} hint={hint}><input type={type} value={value} onChange={e => onChange(e.target.value)} style={inp} {...rest} /></Field>
}

export function Pick({ label, value, onChange, options, blank }: { label: string; value: string; onChange: (v: string) => void; options: readonly (readonly [string, string])[]; blank?: string }) {
  return (
    <Field label={label}>
      <select value={value} onChange={e => onChange(e.target.value)} style={inp}>
        {blank !== undefined && <option value="">{blank}</option>}
        {options.map(([v, l]) => <option key={v} value={v}>{l}</option>)}
      </select>
    </Field>
  )
}

export function Row({ k, children }: { k: string; children: React.ReactNode }) {
  return <div style={{ display: "grid", gridTemplateColumns: "150px 1fr", gap: 8, padding: "5px 0", fontSize: 13, borderBottom: "1px solid var(--hf-border-subtle, var(--hf-border))" }}><span style={{ color: "var(--hf-text-muted)" }}>{k}</span><span style={{ minWidth: 0, overflowWrap: "anywhere" }}>{children || "—"}</span></div>
}

/** The new term of a policy: the number, premium, sum insured and dates. Everything else carries forward on the server. */
export function RenewDialog({ policy, onSubmit, onClose }:
  { policy: { policyNumber: string | null; expiryDate: string | null; sumInsured: number | null; premiumAmount: number | null }; onSubmit: (req: ReturnType<typeof termRequest>) => Promise<unknown>; onClose: () => void }) {
  const [f, setF] = useState<TermForm>(() => renewalDefaults(policy.expiryDate, policy.sumInsured, policy.premiumAmount))
  const [busy, setBusy] = useState(false), [err, setErr] = useState("")
  const problem = termProblem(f)
  const set = (k: keyof TermForm) => (v: string) => setF(s => ({ ...s, [k]: v }))
  const go = async () => {
    setBusy(true); setErr("")
    try { await onSubmit(termRequest(f)); onClose() } catch (e) { setErr(await errorText(e, "Could not renew the policy")) } finally { setBusy(false) }
  }
  return (
    <Dialog title={`Renew ${policy.policyNumber ?? "policy"}`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || busy} onClick={go}>{busy ? "Renewing…" : "Renew policy"}</Btn></>}>
      <div style={{ ...muted, marginBottom: 12, lineHeight: 1.5 }}>The insurer, cover type, asset and excess carry over. Enter what changes for the new term; the old term is marked Renewed.</div>
      <Input label="New policy number" value={f.policyNumber} onChange={set("policyNumber")} autoFocus />
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
        <Input label="Start date" type="date" value={f.startDate} onChange={set("startDate")} />
        <Input label="End date" type="date" value={f.expiryDate} onChange={set("expiryDate")} />
        <Input label="Premium (R)" type="number" min="0" step="0.01" value={f.premiumAmount} onChange={set("premiumAmount")} />
        <Input label="Sum insured (R)" type="number" min="0" step="0.01" value={f.sumInsured} onChange={set("sumInsured")} hint="Leave empty to keep the old amount" />
      </div>
      {problem && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

interface Link { id: string; policyNumber: string | null; startDate: string | null; expiryDate: string | null; status: string; premiumAmount: number | null; sumInsured: number | null }
/** Every term of this cover, oldest first, so a renewal can be traced back to where it began. */
export function RenewalHistory({ url, currentId }: { url: string; currentId: string }) {
  const { data = [], isLoading, isError } = useQuery<Link[]>({ queryKey: ["ins-chain", url], queryFn: async () => list<Link>(await apiClient.get(url)) })
  if (isLoading) return <div style={muted}>Loading history…</div>
  if (isError) return <div style={muted}>Could not load the renewal history.</div>
  if (data.length <= 1) return <div style={muted}>This is the only term so far.</div>
  const rows = [...data].sort((a, b) => (a.startDate ?? "").localeCompare(b.startDate ?? ""))
  return (
    <div style={{ overflowX: "auto" }}>
      <table style={{ width: "100%", borderCollapse: "collapse" }}>
        <thead><tr><th style={th}>Policy</th><th style={th}>Term</th><th style={{ ...th, textAlign: "right" }}>Premium</th><th style={th}>Status</th></tr></thead>
        <tbody>{rows.map(r => (
          <tr key={r.id} style={r.id === currentId ? { background: "var(--hf-surface-sunken)" } : undefined}>
            <td style={td}>{r.policyNumber ?? "—"}{r.id === currentId && <span style={muted}> (this one)</span>}</td>
            <td style={td}>{day(r.startDate)} – {day(r.expiryDate)}</td>
            <td style={{ ...td, textAlign: "right" }}>{money(r.premiumAmount)}</td>
            <td style={td}><StatusPill status={r.status} /></td>
          </tr>))}</tbody>
      </table>
    </div>
  )
}
