// Supplier portal: log in, accept an invite, see the RFQs you were invited to, quote, revise, withdraw or decline.
import { useState } from "react"
import { Link, useNavigate, useParams, useSearchParams } from "react-router-dom"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { supplierPortalApiClient as api } from "../../api/supplierPortal.client"
import { usePortalAuthStore } from "../../store/portalAuth.store"
import { quoteProblem, quoteBody, quoteTotals, type QuoteForm } from "../supply-chain/scmOps"
import { registerProblem, quoteStatusText } from "../supply-chain/scmLimits"
import { fmtR, fmtDate } from "../supply-chain/scm.shared"

const input: React.CSSProperties = { width: "100%", padding: "10px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, boxSizing: "border-box" }
const label: React.CSSProperties = { fontSize: 12, fontWeight: 600, color: "var(--hf-text-secondary)", marginBottom: 5, display: "block" }
const btn: React.CSSProperties = { padding: "10px 16px", borderRadius: 8, border: "none", background: "var(--hf-accent-solid-strong)", color: "var(--hf-text-on-solid)", fontSize: 13.5, fontWeight: 700, cursor: "pointer" }
const ghost: React.CSSProperties = { ...btn, background: "var(--hf-surface-sunken)", color: "var(--hf-text)", border: "1px solid var(--hf-border)" }
const card: React.CSSProperties = { background: "var(--hf-surface)", borderRadius: 14, padding: 24, boxShadow: "0 4px 24px rgba(0,0,0,0.06)" }
const msg = (e: any, d: string) => e?.response?.data?.message ?? d

function Shell({ children, wide }: { children: React.ReactNode; wide?: boolean }) {
  return <div style={{ minHeight: "100vh", background: "var(--hf-surface-sunken)", display: "flex", justifyContent: "center", padding: 24, fontFamily: "'Inter', system-ui, sans-serif" }}>
    <div style={{ width: "100%", maxWidth: wide ? 900 : 400, alignSelf: wide ? "flex-start" : "center" }}>{children}</div></div>
}

export function SupplierPortalLoginPage() {
  const nav = useNavigate(); const setAuth = usePortalAuthStore(s => s.setAuth)
  const [params] = useSearchParams(); const invite = params.get("token") ?? ""
  const [email, setEmail] = useState(""); const [password, setPassword] = useState(""); const [err, setErr] = useState<string | null>(null)
  const go = async (e: React.FormEvent) => {
    e.preventDefault(); setErr(null)
    try {
      const r = await api.post("/api/v1/supplier/portal/auth/login", { email, password, inviteToken: invite || undefined })
      setAuth(r.data.token, { portalUserId: r.data.portalUserId, email: r.data.email, fullName: r.data.fullName }); nav("/supplier-portal")
    } catch (x) { setErr(msg(x, "Invalid email or password")) }
  }
  return <Shell><form style={card} onSubmit={go}>
    <h2 style={{ marginTop: 0 }}>Supplier portal</h2>
    {invite && <p style={{ fontSize: 12 }}>Log in to accept your invitation.</p>}
    <label style={label}>Email</label><input style={{ ...input, marginBottom: 12 }} type="email" required value={email} onChange={e => setEmail(e.target.value)} />
    <label style={label}>Password</label><input style={{ ...input, marginBottom: 14 }} type="password" required value={password} onChange={e => setPassword(e.target.value)} />
    {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{err}</p>}
    <button style={{ ...btn, width: "100%" }} type="submit">Sign in</button>
    {!invite && <p style={{ fontSize: 12, textAlign: "center" }}>New here? Use the link in your invitation email.</p>}
  </form></Shell>
}

export function SupplierPortalRegisterPage() {
  const nav = useNavigate(); const setAuth = usePortalAuthStore(s => s.setAuth)
  const [params] = useSearchParams(); const token = params.get("token") ?? ""
  const [name, setName] = useState(""); const [pw, setPw] = useState(""); const [pw2, setPw2] = useState(""); const [err, setErr] = useState<string | null>(null)
  const go = async (e: React.FormEvent) => {
    e.preventDefault()
    const p = !token ? "This invitation link is incomplete" : registerProblem(pw, pw2, name); if (p) { setErr(p); return }
    setErr(null)
    try {
      const r = await api.post("/api/v1/supplier/portal/auth/register", { inviteToken: token, password: pw, fullName: name.trim() })
      setAuth(r.data.token, { portalUserId: r.data.portalUserId, email: r.data.email, fullName: r.data.fullName }); nav("/supplier-portal")
    } catch (x: any) {
      setErr(x?.response?.data?.code === "ACCOUNT_EXISTS" ? "You already have an account. Log in to accept this invitation." : msg(x, "This invitation is invalid or has expired"))
    }
  }
  return <Shell><form style={card} onSubmit={go}>
    <h2 style={{ marginTop: 0 }}>Create your supplier login</h2>
    <label style={label}>Your name</label><input style={{ ...input, marginBottom: 12 }} value={name} onChange={e => setName(e.target.value)} />
    <label style={label}>Password (10+ characters)</label><input style={{ ...input, marginBottom: 12 }} type="password" value={pw} onChange={e => setPw(e.target.value)} />
    <label style={label}>Confirm password</label><input style={{ ...input, marginBottom: 14 }} type="password" value={pw2} onChange={e => setPw2(e.target.value)} />
    {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{err}</p>}
    <button style={{ ...btn, width: "100%" }} type="submit">Create account</button>
    <p style={{ fontSize: 12, textAlign: "center" }}>Already registered? <Link to={`/supplier-portal/login?token=${encodeURIComponent(token)}`}>Log in to accept</Link></p>
  </form></Shell>
}

interface Summary { rfqId: string; buyerName: string; supplierName: string; rfqNumber: string; title: string; status: string; closesAt: string | null; openForQuotes: boolean; currency: string; requiredByDate: string | null; invitationStatus: string; quotationStatus: string | null; myLandedCost: number | null }

export function SupplierPortalHomePage() {
  const user = usePortalAuthStore(s => s.user); const logout = usePortalAuthStore(s => s.logout); const nav = useNavigate()
  const q = useQuery({ queryKey: ["supplier-portal-rfqs"], queryFn: () => api.get("/api/v1/supplier/portal/rfqs").then(r => r.data as Summary[]) })
  const rows = q.data ?? []
  return <Shell wide>
    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 16 }}>
      <div><h2 style={{ margin: 0 }}>Requests for quotation</h2><small>{user?.fullName}</small></div>
      <button style={ghost} onClick={() => { logout(); nav("/supplier-portal/login") }}>Sign out</button>
    </div>
    {q.isLoading ? <p>Loading…</p> : q.isError ? <p role="alert" style={{ color: "var(--hf-danger-text)" }}>{msg(q.error, "Could not load your RFQs")}</p>
      : rows.length === 0 ? <div style={card}>You have not been invited to any RFQs yet.</div>
      : <div style={{ display: "grid", gap: 10 }}>{rows.map(r => (
        <Link key={r.rfqId} to={`/supplier-portal/rfqs/${r.rfqId}`} style={{ ...card, padding: 16, textDecoration: "none", color: "inherit", opacity: r.openForQuotes ? 1 : 0.7 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, flexWrap: "wrap" }}>
            <strong>{r.rfqNumber} · {r.title}</strong>
            <span style={{ fontSize: 12 }}>{r.closesAt ? `Closes ${fmtDate(r.closesAt)}` : "No closing date"}</span>
          </div>
          <div style={{ fontSize: 12.5, marginTop: 4 }}>{r.buyerName}{r.supplierName ? ` · quoting as ${r.supplierName}` : ""}</div>
          <div style={{ fontSize: 12.5, marginTop: 4, fontWeight: 600 }}>{quoteStatusText(r.invitationStatus, r.quotationStatus, r.openForQuotes)}{r.myLandedCost != null && r.quotationStatus === "SUBMITTED" ? ` · ${fmtR(r.myLandedCost)}` : ""}</div>
        </Link>))}</div>}
  </Shell>
}

interface RfqLine { id: string; lineNumber: number; itemName: string; description: string | null; quantity: number }
interface MyQuote { id: string; status: string; quoteReference: string | null; validUntil: string | null; leadTimeDays: number | null; warrantyMonths: number | null; paymentTermsDays: number | null; deliveryCost: number; otherCharges: number; notes: string | null; hasFile: boolean; fileName: string | null; landedCost: number
  lines: { rfqLineId: string; noBid: boolean; qtyOffered: number | null; unitPrice: number | null; discountPct: number | null }[] }
interface Detail { summary: Summary; description: string | null; lines: RfqLine[]; myQuotation: MyQuote | null; declineReason: string | null }

function initialForm(d: Detail): QuoteForm & { notes: string } {
  const q = d.myQuotation && d.myQuotation.status === "SUBMITTED" ? d.myQuotation : null
  return {
    supplierId: "self", quoteReference: q?.quoteReference ?? "", validUntil: q?.validUntil ?? "", leadTimeDays: q?.leadTimeDays != null ? String(q.leadTimeDays) : "",
    warrantyMonths: q?.warrantyMonths != null ? String(q.warrantyMonths) : "", paymentTermsDays: q?.paymentTermsDays != null ? String(q.paymentTermsDays) : "",
    deliveryCost: q ? String(q.deliveryCost) : "", otherCharges: q ? String(q.otherCharges) : "", notes: q?.notes ?? "",
    lines: d.lines.map(l => {
      const m = q?.lines.find(x => x.rfqLineId === l.id)
      return { rfqLineId: l.id, quantity: m?.qtyOffered != null ? String(m.qtyOffered) : String(l.quantity), unitPrice: m?.unitPrice != null ? String(m.unitPrice) : "", discountPct: m?.discountPct != null ? String(m.discountPct) : "", noBid: !!m?.noBid }
    }),
  } as QuoteForm & { notes: string }
}

export function SupplierPortalRfqPage() {
  const { rfqId = "" } = useParams(); const qc = useQueryClient()
  const key = ["supplier-portal-rfq", rfqId]
  const q = useQuery({ queryKey: key, queryFn: () => api.get(`/api/v1/supplier/portal/rfqs/${rfqId}`).then(r => r.data as Detail) })
  const [form, setForm] = useState<(QuoteForm & { notes: string }) | null>(null)
  const [err, setErr] = useState(""); const [ok, setOk] = useState(""); const [reason, setReason] = useState("")
  const d = q.data
  if (d && !form) setForm(initialForm(d))
  const done = (m: string) => ({ onSuccess: () => { setErr(""); setOk(m); setForm(null); qc.invalidateQueries({ queryKey: key }); qc.invalidateQueries({ queryKey: ["supplier-portal-rfqs"] }) }, onError: (e: any) => { setOk(""); setErr(msg(e, "That did not work")) } })
  const submit = useMutation({ mutationFn: () => api.post(`/api/v1/supplier/portal/rfqs/${rfqId}/quotation`, { ...quoteBody(form!), supplierId: undefined, notes: form!.notes.trim() || null }), ...done("Your quotation was sent.") })
  const withdraw = useMutation({ mutationFn: () => api.post(`/api/v1/supplier/portal/rfqs/${rfqId}/quotation/withdraw`, { reason }), ...done("Your quotation was withdrawn.") })
  const decline = useMutation({ mutationFn: () => api.post(`/api/v1/supplier/portal/rfqs/${rfqId}/decline`, { reason }), ...done("You declined to quote.") })
  const attach = useMutation({ mutationFn: (f: File) => { const b = new FormData(); b.append("file", f); return api.post(`/api/v1/supplier/portal/rfqs/${rfqId}/quotation/file`, b, { headers: { "Content-Type": "multipart/form-data" } }) }, ...done("Document attached.") })

  if (q.isLoading) return <Shell wide><p>Loading…</p></Shell>
  if (q.isError || !d || !form) return <Shell wide><p role="alert">{msg(q.error, "RFQ not found")}</p><Link to="/supplier-portal">Back</Link></Shell>
  const s = d.summary; const open = s.openForQuotes; const mine = d.myQuotation; const live = mine?.status === "SUBMITTED"
  const totals = quoteTotals(form)
  const send = () => { const p = quoteProblem(form); if (p) { setErr(p); return } setErr(""); submit.mutate() }
  const set = (k: keyof QuoteForm, v: string) => setForm({ ...form, [k]: v } as typeof form)
  const setLine = (i: number, patch: Partial<QuoteForm["lines"][number]>) => setForm({ ...form, lines: form.lines.map((l, j) => j === i ? { ...l, ...patch } : l) })

  return <Shell wide>
    <Link to="/supplier-portal">← All RFQs</Link>
    <div style={{ ...card, marginTop: 10 }}>
      <h2 style={{ margin: 0 }}>{s.rfqNumber} · {s.title}</h2>
      <p style={{ fontSize: 13 }}>{s.buyerName} · {open ? (s.closesAt ? `Closes ${fmtDate(s.closesAt)}` : "Open") : "No longer open for quotations"}{s.requiredByDate ? ` · needed by ${fmtDate(s.requiredByDate)}` : ""} · {s.currency}</p>
      {d.description && <p style={{ fontSize: 13, whiteSpace: "pre-wrap" }}>{d.description}</p>}
      <p style={{ fontWeight: 600, fontSize: 13 }}>{quoteStatusText(s.invitationStatus, s.quotationStatus, open)}{d.declineReason ? `: ${d.declineReason}` : ""}</p>
      {err && <p role="alert" style={{ color: "var(--hf-danger-text)", fontSize: 12.5 }}>{err}</p>}
      {ok && <p role="status" style={{ color: "var(--hf-success-text)", fontSize: 12.5 }}>{ok}</p>}

      <table style={{ width: "100%", borderCollapse: "collapse", fontSize: 13 }}>
        <thead><tr>{["Item", "Wanted", "No bid", "Your qty", "Unit price (ex VAT)", "Discount %"].map(h => <th key={h} style={{ textAlign: "left", padding: 6 }}>{h}</th>)}</tr></thead>
        <tbody>{d.lines.map((l, i) => { const f = form.lines[i]; return (
          <tr key={l.id} style={{ borderTop: "1px solid var(--hf-border)" }}>
            <td style={{ padding: 6 }}><strong>{l.itemName}</strong>{l.description && <div style={{ fontSize: 12 }}>{l.description}</div>}</td>
            <td style={{ padding: 6 }}>{l.quantity}</td>
            <td style={{ padding: 6 }}><input type="checkbox" aria-label={`No bid ${l.itemName}`} disabled={!open} checked={f.noBid} onChange={e => setLine(i, { noBid: e.target.checked })} /></td>
            <td style={{ padding: 6 }}><input style={input} aria-label={`Quantity ${l.itemName}`} disabled={!open || f.noBid} value={f.quantity} onChange={e => setLine(i, { quantity: e.target.value })} /></td>
            <td style={{ padding: 6 }}><input style={input} aria-label={`Unit price ${l.itemName}`} disabled={!open || f.noBid} value={f.unitPrice} onChange={e => setLine(i, { unitPrice: e.target.value })} /></td>
            <td style={{ padding: 6 }}><input style={input} aria-label={`Discount ${l.itemName}`} disabled={!open || f.noBid} value={f.discountPct} onChange={e => setLine(i, { discountPct: e.target.value })} /></td>
          </tr>) })}</tbody>
      </table>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 10, margin: "12px 0" }}>
        {([["quoteReference", "Your quote reference"], ["validUntil", "Valid until"], ["leadTimeDays", "Lead time (days) *"], ["warrantyMonths", "Warranty (months)"], ["paymentTermsDays", "Payment terms (days)"], ["deliveryCost", "Delivery (R, ex VAT)"], ["otherCharges", "Other charges (R)"]] as const).map(([k, l]) => (
          <div key={k}><label style={label}>{l}</label><input style={input} aria-label={l} type={k === "validUntil" ? "date" : "text"} disabled={!open} value={form[k]} onChange={e => set(k, e.target.value)} /></div>))}
      </div>
      <label style={label}>Notes to the buyer</label>
      <textarea style={{ ...input, minHeight: 60 }} disabled={!open} value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
      <p style={{ fontSize: 13 }}>Subtotal {fmtR(totals.subtotal)} · with delivery and charges {fmtR(totals.landed)} (before VAT)</p>

      {open && <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}><button style={btn} disabled={submit.isPending} onClick={send}>{live ? "Send revised quotation" : "Submit quotation"}</button></div>}

      {live && open && (
        <div style={{ marginTop: 14, fontSize: 13 }}>
          {mine?.hasFile ? <>Document: {mine.fileName} · </> : null}
          <label style={{ cursor: "pointer", textDecoration: "underline" }}>{mine?.hasFile ? "Replace document" : "Attach your quote document"}
            <input type="file" hidden onChange={e => { const f = e.target.files?.[0]; if (f) attach.mutate(f) }} /></label>
        </div>)}

      {open && (live || s.invitationStatus !== "DECLINED") && (
        <div style={{ marginTop: 16, paddingTop: 12, borderTop: "1px solid var(--hf-border)" }}>
          <label style={label}>{live ? "Withdraw your quotation" : "Decline to quote"} (reason required)</label>
          <div style={{ display: "flex", gap: 8 }}>
            <input style={input} aria-label="Reason" value={reason} onChange={e => setReason(e.target.value)} />
            <button style={ghost} disabled={!reason.trim() || withdraw.isPending || decline.isPending} onClick={() => (live ? withdraw : decline).mutate()}>{live ? "Withdraw" : "Decline"}</button>
          </div>
        </div>)}
    </div>
  </Shell>
}
