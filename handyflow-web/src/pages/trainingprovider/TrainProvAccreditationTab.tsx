// src/pages/trainingprovider/TrainProvAccreditationTab.tsx
//
// Who accredits the provider, where it delivers, and what each approval covers. Backed by
// /api/v1/training-provider/accreditation (authorities, sites, accreditations with scopes and history, coverage).
// An approval is only ever as good as the verified record behind it: nothing is shown as accredited because a number was typed in.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermissions } from "../../hooks/usePermission"
import { AUTHORITY_TYPES, SITE_TYPES, coverageLabel, nameProblem, reasonProblem, type Authority, type Site } from "./accreditationRules"
import { Badge, Field, Notice, SubTabs } from "./tpUi"
import { errText, ghost, input, muted, solid, card, unwrap, todayIso } from "./tpStyle"
import TrainProvAccreditationList from "./TrainProvAccreditationList"
import { ACC, useAuthorities, useSites } from "./tpApi"


function RetireBox({ onConfirm, onCancel, busy }: { onConfirm: (reason: string) => void; onCancel: () => void; busy: boolean }) {
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  return (
    <div style={{ display: "grid", gap: 6, marginTop: 8 }}>
      <input aria-label="Reason" style={input} placeholder="Why is this being retired? Nothing is deleted" value={reason} onChange={e => setReason(e.target.value)} />
      <Notice text={error} />
      <div style={{ display: "flex", gap: 8 }}>
        <button style={solid} disabled={busy} onClick={() => { const p = reasonProblem(reason); if (p) setError(p); else onConfirm(reason.trim()) }}>Retire</button>
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

function Authorities({ canManage, isAdmin }: { canManage: boolean; isAdmin: boolean }) {
  const qc = useQueryClient()
  const { data = [], isLoading } = useAuthorities()
  const [editing, setEditing] = useState<string | "new" | null>(null)
  const [form, setForm] = useState({ name: "", authorityType: "QCTO", notes: "" })
  const [retiring, setRetiring] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setEditing(null); setRetiring(null); qc.invalidateQueries({ queryKey: ["tp-authorities"] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const open = (a?: Authority) => { setEditing(a ? a.id : "new"); setForm(a ? { name: a.name, authorityType: a.authorityType, notes: a.notes ?? "" } : { name: "", authorityType: "QCTO", notes: "" }); setError("") }
  const save = () => {
    const p = nameProblem(form.name, "authority"); if (p) { setError(p); return }
    const body = { name: form.name.trim(), authorityType: form.authorityType, notes: form.notes.trim() || null }
    run(() => (editing === "new" ? apiClient.post(`${ACC}/authorities`, body) : apiClient.put(`${ACC}/authorities/${editing}`, body)))
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>The bodies that accredit or register the provider. Record only the ones that actually apply. Nothing is added for you.</div>
      {canManage && editing === null && <div><button style={solid} onClick={() => open()}>Add an authority</button></div>}
      {editing !== null && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Authority name" style={input} placeholder="Name of the authority" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} />
          <select aria-label="Authority type" style={input} value={form.authorityType} onChange={e => setForm({ ...form, authorityType: e.target.value })}>
            {Object.entries(AUTHORITY_TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <input aria-label="Notes" style={input} placeholder="Notes (optional)" value={form.notes} onChange={e => setForm({ ...form, notes: e.target.value })} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{editing === "new" ? "Add authority" : "Save changes"}</button><button style={ghost} onClick={() => setEditing(null)}>Cancel</button></div>
        </div>
      )}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No authorities recorded yet.</div> : data.map(a => (
        <div key={a.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center" }}>
            <div><strong>{a.name}</strong> <span style={muted}>{AUTHORITY_TYPES[a.authorityType] ?? a.authorityType}</span></div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <Badge text={a.status === "ACTIVE" ? "Active" : "Retired"} tone={a.status === "ACTIVE" ? "good" : "neutral"} />
              {canManage && a.status === "ACTIVE" && <button style={ghost} onClick={() => open(a)}>Edit</button>}
              {isAdmin && a.status === "ACTIVE" && <button style={ghost} onClick={() => setRetiring(a.id)}>Retire</button>}
            </div>
          </div>
          {a.notes && <div style={muted}>{a.notes}</div>}
          {a.retiredReason && <div style={muted}>Retired: {a.retiredReason}</div>}
          {retiring === a.id && <RetireBox busy={busy} onCancel={() => setRetiring(null)} onConfirm={r => run(() => apiClient.post(`${ACC}/authorities/${a.id}/retire`, { reason: r }))} />}
        </div>
      ))}
    </div>
  )
}

const emptySite = { name: "", siteType: "CAMPUS", address: "", province: "", contactPerson: "", phone: "" }
function Sites({ canManage, isAdmin }: { canManage: boolean; isAdmin: boolean }) {
  const qc = useQueryClient()
  const { data = [], isLoading } = useSites()
  const [editing, setEditing] = useState<string | "new" | null>(null)
  const [form, setForm] = useState(emptySite)
  const [retiring, setRetiring] = useState<string | null>(null)
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const run = async (fn: () => Promise<unknown>) => {
    setBusy(true); setError("")
    try { await fn(); setEditing(null); setRetiring(null); qc.invalidateQueries({ queryKey: ["tp-sites"] }) } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }
  const open = (s?: Site) => {
    setEditing(s ? s.id : "new"); setError("")
    setForm(s ? { name: s.name, siteType: s.siteType, address: s.address ?? "", province: s.province ?? "", contactPerson: s.contactPerson ?? "", phone: s.phone ?? "" } : emptySite)
  }
  const save = () => {
    const p = nameProblem(form.name, "site"); if (p) { setError(p); return }
    const b = (v: string) => v.trim() || null
    const body = { name: form.name.trim(), siteType: form.siteType, address: b(form.address), province: b(form.province), contactPerson: b(form.contactPerson), phone: b(form.phone) }
    run(() => (editing === "new" ? apiClient.post(`${ACC}/sites`, body) : apiClient.put(`${ACC}/sites/${editing}`, body)))
  }
  const set = (k: keyof typeof emptySite) => (e: React.ChangeEvent<HTMLInputElement>) => setForm({ ...form, [k]: e.target.value })
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={muted}>Each campus or delivery site is recorded on its own. Approval at one site never covers another.</div>
      {canManage && editing === null && <div><button style={solid} onClick={() => open()}>Add a site</button></div>}
      {editing !== null && (
        <div style={{ ...card, display: "grid", gap: 8, background: "var(--hf-surface-muted)" }}>
          <input aria-label="Site name" style={input} placeholder="Site name" value={form.name} onChange={set("name")} />
          <select aria-label="Site type" style={input} value={form.siteType} onChange={e => setForm({ ...form, siteType: e.target.value })}>
            {Object.entries(SITE_TYPES).map(([k, l]) => <option key={k} value={k}>{l}</option>)}
          </select>
          <input aria-label="Address" style={input} placeholder="Address" value={form.address} onChange={set("address")} />
          <input aria-label="Province" style={input} placeholder="Province" value={form.province} onChange={set("province")} />
          <input aria-label="Contact person" style={input} placeholder="Contact person" value={form.contactPerson} onChange={set("contactPerson")} />
          <input aria-label="Phone" style={input} placeholder="Phone" value={form.phone} onChange={set("phone")} />
          <Notice text={error} />
          <div style={{ display: "flex", gap: 8 }}><button style={solid} disabled={busy} onClick={save}>{editing === "new" ? "Add site" : "Save changes"}</button><button style={ghost} onClick={() => setEditing(null)}>Cancel</button></div>
        </div>
      )}
      {isLoading ? <div style={muted}>Loading…</div> : data.length === 0 ? <div style={muted}>No sites recorded yet.</div> : data.map(s => (
        <div key={s.id} style={card}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 8, alignItems: "center" }}>
            <div><strong>{s.name}</strong> <span style={muted}>{SITE_TYPES[s.siteType] ?? s.siteType}{s.province ? ` · ${s.province}` : ""}</span></div>
            <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
              <Badge text={s.status === "ACTIVE" ? "Active" : "Retired"} tone={s.status === "ACTIVE" ? "good" : "neutral"} />
              {canManage && s.status === "ACTIVE" && <button style={ghost} onClick={() => open(s)}>Edit</button>}
              {isAdmin && s.status === "ACTIVE" && <button style={ghost} onClick={() => setRetiring(s.id)}>Retire</button>}
            </div>
          </div>
          {s.address && <div style={muted}>{s.address}</div>}
          {s.retiredReason && <div style={muted}>Retired: {s.retiredReason}</div>}
          {retiring === s.id && <RetireBox busy={busy} onCancel={() => setRetiring(null)} onConfirm={r => run(() => apiClient.post(`${ACC}/sites/${s.id}/retire`, { reason: r }))} />}
        </div>
      ))}
    </div>
  )
}

type CoverageResult = { outcome: string; covered: boolean; message: string }
function CoverageCheck() {
  const { data: sites = [] } = useSites()
  const { data: courses = [] } = useQuery<{ id: string; title: string }[]>({
    queryKey: ["tp-coverage-courses"],
    queryFn: async () => { const d = unwrap<{ id: string; title: string }[] | { content?: { id: string; title: string }[] }>(await apiClient.get("/api/v1/training-provider/courses", { params: { size: 200 } })); return Array.isArray(d) ? d : d?.content ?? [] },
  })
  const [courseId, setCourseId] = useState("")
  const [siteId, setSiteId] = useState("")
  const [on, setOn] = useState(todayIso())
  const [result, setResult] = useState<CoverageResult | null>(null)
  const [error, setError] = useState("")
  const check = async () => {
    setError(""); setResult(null)
    try { setResult(unwrap<CoverageResult>(await apiClient.get(`${ACC}/coverage`, { params: { courseId, siteId, on: on || undefined } }))) } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ display: "grid", gap: 10, maxWidth: 560 }}>
      <div style={muted}>Check whether a course is covered by an approved accreditation at a site on a date. Only a verified, in-force approval with that course in its scope counts.</div>
      <Field label="Course"><select style={input} value={courseId} onChange={e => setCourseId(e.target.value)}><option value="">Choose a course</option>{courses.map(c => <option key={c.id} value={c.id}>{c.title}</option>)}</select></Field>
      <Field label="Site"><select style={input} value={siteId} onChange={e => setSiteId(e.target.value)}><option value="">Choose a site</option>{sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></Field>
      <Field label="On"><input type="date" style={input} value={on} onChange={e => setOn(e.target.value)} /></Field>
      <div><button style={solid} disabled={!courseId || !siteId} onClick={check}>Check cover</button></div>
      <Notice text={error} />
      {result && <div style={card}><Badge text={coverageLabel(result.outcome)} tone={result.covered ? "good" : "bad"} /><div style={{ marginTop: 6, fontSize: 13 }}>{result.message}</div></div>}
    </div>
  )
}

export default function TrainProvAccreditationTab() {
  const isAdmin = usePermissions(["TRAININGPROVIDER_ADMIN"])
  const canManage = usePermissions(["TRAININGPROVIDER_MANAGE", "TRAININGPROVIDER_ADMIN"])
  const [tab, setTab] = useState("accreditations")
  return (
    <div style={{ display: "grid", gap: 14 }}>
      <div><h2 style={{ margin: 0, fontSize: 18 }}>Accreditation</h2>
        <div style={muted}>Approvals, scope and history. An approval counts only once an administrator has recorded it with the authority's number and where it was verified.</div></div>
      <SubTabs value={tab} onChange={setTab} tabs={[["accreditations", "Accreditations"], ["authorities", "Authorities"], ["sites", "Sites"], ["coverage", "Check cover"]]} />
      {tab === "accreditations" && <TrainProvAccreditationList canManage={canManage} isAdmin={isAdmin} />}
      {tab === "authorities" && <Authorities canManage={canManage} isAdmin={isAdmin} />}
      {tab === "sites" && <Sites canManage={canManage} isAdmin={isAdmin} />}
      {tab === "coverage" && <CoverageCheck />}
    </div>
  )
}
