// src/pages/complianceservices/RegulatoryPage.tsx
//
// Changes in the rules. Someone flags a change and links the obligations it touches; a reviewer (a second person) confirms the content and
// the date it takes effect; confirming raises one impact per affected client. Nothing here edits the catalogue or past assessments.
import { useState } from "react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission, usePermissions } from "../../hooks/usePermission"
import RegulatoryImpactRow from "./RegulatoryImpactRow"
import {
  CHANGE_TYPES, KINDS, STATUS_FILTERS, blankForm, changeProblem, changeTypeLabel, confirmBlock, formOf, noteProblem, ordered, progress, sourceProblem, statusLabel, MIN_NOTE,
  type Change, type ChangeForm, type Detail, type Source,
} from "./regulatoryRules"

const unwrap = <T,>(r: { data: unknown }): T => ((r.data as { data?: T })?.data ?? r.data) as T
const errText = (e: unknown): string => {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message ?? x?.message ?? "Something went wrong"
}
const B = "/api/v1/compliance-services/regulatory"
const input: React.CSSProperties = { width: "100%", padding: "8px 10px", border: "1px solid var(--hf-border)", borderRadius: 8, fontSize: 13, background: "var(--hf-surface)", color: "var(--hf-text)", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "block", fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)", marginBottom: 4 }
const solid: React.CSSProperties = { padding: "8px 14px", border: "none", borderRadius: 8, background: "var(--hf-success-solid-strong)", color: "#fff", fontSize: 13, fontWeight: 600, cursor: "pointer" }
const ghost: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 8, background: "transparent", color: "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" }
const chip = (on: boolean): React.CSSProperties => ({ padding: "5px 12px", borderRadius: 999, border: "1px solid var(--hf-border)", background: on ? "var(--hf-success-solid-strong)" : "transparent", color: on ? "#fff" : "var(--hf-text-muted)", fontSize: 12, cursor: "pointer" })
const bad: React.CSSProperties = { fontSize: 12, color: "var(--hf-danger-text, #c0392b)" }
const todayIso = () => new Date().toISOString().slice(0, 10)

export default function RegulatoryPage() {
  const [view, setView] = useState<"changes" | "sources">("changes")
  return (
    <div style={{ display: "grid", gap: 12 }}>
      <div style={{ display: "flex", gap: 6 }}>
        <button style={chip(view === "changes")} onClick={() => setView("changes")}>Changes</button>
        <button style={chip(view === "sources")} onClick={() => setView("sources")}>Sources</button>
      </div>
      {view === "changes" ? <Changes /> : <Sources />}
    </div>
  )
}

function Changes() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const [status, setStatus] = useState("")
  const [open, setOpen] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)
  const { data = [], isLoading } = useQuery<Change[]>({
    queryKey: ["cs-reg-changes", status],
    queryFn: async () => unwrap<Change[]>(await apiClient.get(`${B}/changes`, { params: status ? { status } : {} })),
  })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["cs-reg-changes"] }); qc.invalidateQueries({ queryKey: ["cs-reg-detail"] }); qc.invalidateQueries({ queryKey: ["cs-work-queue"] }); qc.invalidateQueries({ queryKey: ["cs-reg-client"] }) }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>
        Changes in the rules. A change is flagged, then a reviewer confirms what it says and when it takes effect. Confirming lists the clients it touches so each one can be dealt with. Nothing here changes the obligation catalogue or finished assessments.
      </div>
      <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
        {STATUS_FILTERS.map(f => <button key={f[0]} style={chip(status === f[0])} onClick={() => setStatus(f[0])}>{f[1]}</button>)}
        <span style={{ flex: 1 }} />
        {canManage && !creating && <button style={solid} onClick={() => setCreating(true)}>Record a change</button>}
      </div>
      {creating && <ChangeFormPanel onDone={() => { setCreating(false); refresh() }} onCancel={() => setCreating(false)} />}
      {isLoading && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>}
      {!isLoading && data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No changes here.</div>}
      {ordered(data).map(c => (
        <div key={c.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 6 }}>
          <button onClick={() => setOpen(open === c.id ? null : c.id)} aria-expanded={open === c.id}
            style={{ all: "unset", cursor: "pointer", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{c.title}</span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{c.sourceName} · {changeTypeLabel(c.changeType)}{c.effectiveDate ? ` · from ${c.effectiveDate}` : ""}</span>
            <span style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>{statusLabel(c.status)}</span>
          </button>
          {c.status === "CONFIRMED" && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{progress(c)}</div>}
          {open === c.id && <ChangeDetailPanel id={c.id} canManage={canManage} onChanged={refresh} />}
        </div>
      ))}
    </div>
  )
}

function ChangeDetailPanel({ id, canManage, onChanged }: { id: string; canManage: boolean; onChanged: () => void }) {
  const isAdmin = usePermission("COMPLIANCE_SERVICES_ADMIN")
  const [editing, setEditing] = useState(false)
  const [deciding, setDeciding] = useState<"confirm" | "dismiss" | null>(null)
  const [note, setNote] = useState("")
  const [reason, setReason] = useState("")
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: d } = useQuery<Detail>({ queryKey: ["cs-reg-detail", id], queryFn: async () => unwrap<Detail>(await apiClient.get(`${B}/changes/${id}`)) })
  if (!d) return <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>Loading...</div>

  const c = d.change
  const call = async (path: string, body?: unknown) => {
    setBusy(true); setError("")
    try { await apiClient.post(`${B}/changes/${id}/${path}`, body ?? {}); setDeciding(null); setNote(""); setReason(""); onChanged(); return true } catch (e) { setError(errText(e)); return false } finally { setBusy(false) }
  }
  const block = confirmBlock(c)

  if (editing) return <ChangeFormPanel existing={d} onDone={() => { setEditing(false); onChanged() }} onCancel={() => setEditing(false)} />

  return (
    <div style={{ display: "grid", gap: 8, borderTop: "1px solid var(--hf-border)", paddingTop: 8 }}>
      {d.summary && <div style={{ fontSize: 13, color: "var(--hf-text)", whiteSpace: "pre-wrap" }}>{d.summary}</div>}
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        {c.publicationDate ? `Published ${c.publicationDate}` : "Publication date not recorded"}
        {d.referenceUrl && <> · <a href={d.referenceUrl} target="_blank" rel="noopener noreferrer">Source link</a></>}
      </div>
      <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
        Obligations it touches: {d.obligations.length === 0 ? "none linked" : d.obligations.map(o => o.code).join(", ")}
      </div>
      {d.affected.length > 0 && (
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>Would affect: {d.affected.map(a => `${a.clientName} (${a.obligationCode})`).join(", ")}</div>
      )}
      {(c.status === "DETECTED" || c.status === "IN_REVIEW") && d.affected.length === 0 && d.obligations.length > 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No active client currently has these obligations.</div>}
      {d.reviewNote && (
        <div style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>
          Reviewed {d.reviewedAt ? d.reviewedAt.slice(0, 10) : ""}: {d.reviewNote}
          {d.reviewExceptionReason && <div>Reviewed by the person who raised it. Reason: {d.reviewExceptionReason}</div>}
        </div>
      )}
      {error && <div role="alert" style={bad}>{error}</div>}

      {canManage && c.actions.includes("EDIT") && !deciding && (
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          <button style={ghost} onClick={() => setEditing(true)}>Edit</button>
          {c.actions.includes("START_REVIEW") && <button style={ghost} disabled={busy} onClick={() => call("start-review")}>Take into review</button>}
          {isAdmin && c.actions.includes("CONFIRM") && <button style={solid} onClick={() => setDeciding("confirm")}>Confirm…</button>}
          {isAdmin && c.actions.includes("DISMISS") && <button style={ghost} onClick={() => setDeciding("dismiss")}>Dismiss…</button>}
        </div>
      )}
      {deciding && (
        <div style={{ display: "grid", gap: 6 }}>
          {deciding === "confirm" && block && <div style={bad}>{block}</div>}
          <label htmlFor={`review-note-${id}`} style={label}>{deciding === "confirm" ? "Confirm" : "Dismiss"}: what did you check? (at least {MIN_NOTE} characters)</label>
          <textarea id={`review-note-${id}`} rows={2} style={input} value={note} onChange={e => setNote(e.target.value)} />
          <label htmlFor={`review-reason-${id}`} style={label}>Only if you raised this change yourself: why can no one else review it?</label>
          <input id={`review-reason-${id}`} style={input} value={reason} onChange={e => setReason(e.target.value)} />
          <div style={{ display: "flex", gap: 8 }}>
            <button style={{ ...solid, opacity: busy || noteProblem(note) || (deciding === "confirm" && block) ? 0.5 : 1 }}
              disabled={busy || !!noteProblem(note) || (deciding === "confirm" && !!block)}
              onClick={() => call(deciding === "confirm" ? "confirm" : "dismiss", { note: note.trim(), exceptionReason: reason.trim() || null })}>
              {deciding === "confirm" ? "Confirm change" : "Dismiss change"}
            </button>
            <button style={ghost} onClick={() => setDeciding(null)}>Cancel</button>
          </div>
        </div>
      )}

      {c.status === "CONFIRMED" && (
        <div style={{ display: "grid", gap: 6 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
            <strong style={{ fontSize: 13 }}>Clients affected</strong>
            <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>{progress(c)}</span>
            <span style={{ flex: 1 }} />
            {canManage && <button style={ghost} disabled={busy} onClick={() => call("refresh-impacts")}>Check for newly affected clients</button>}
          </div>
          {d.impacts.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>No client had these obligations when the change was confirmed.</div>}
          {d.impacts.map(i => <RegulatoryImpactRow key={i.id} impact={i} canManage={canManage} showClient onChanged={onChanged} />)}
        </div>
      )}
    </div>
  )
}

function ChangeFormPanel({ existing, onDone, onCancel }: { existing?: Detail; onDone: () => void; onCancel: () => void }) {
  const [f, setF] = useState<ChangeForm>(existing ? formOf(existing) : blankForm())
  const [error, setError] = useState("")
  const [busy, setBusy] = useState(false)
  const { data: sources = [] } = useQuery<Source[]>({ queryKey: ["cs-reg-sources"], queryFn: async () => unwrap<Source[]>(await apiClient.get(`${B}/sources`)) })
  const { data: obligations = [] } = useQuery<{ id: string; code: string; name: string | null }[]>({ queryKey: ["cs-obligations"], queryFn: async () => unwrap(await apiClient.get("/api/v1/compliance-services/obligations")) })
  const set = (k: keyof ChangeForm, v: string) => setF(p => ({ ...p, [k]: v }))
  const problem = changeProblem(f, todayIso())
  const toggle = (id: string) => setF(p => ({ ...p, obligationIds: p.obligationIds.includes(id) ? p.obligationIds.filter(x => x !== id) : [...p.obligationIds, id] }))

  const save = async () => {
    setBusy(true); setError("")
    const body = { ...f, summary: f.summary || null, publicationDate: f.publicationDate || null, effectiveDate: f.effectiveDate || null, referenceUrl: f.referenceUrl.trim() || null }
    try {
      if (existing) await apiClient.put(`${B}/changes/${existing.change.id}`, body)
      else await apiClient.post(`${B}/changes`, body)
      onDone()
    } catch (e) { setError(errText(e)) } finally { setBusy(false) }
  }

  return (
    <div style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: 12, display: "grid", gap: 10 }}>
      <div>
        <label htmlFor="reg-source" style={label}>Source</label>
        <select id="reg-source" style={input} value={f.sourceId} onChange={e => set("sourceId", e.target.value)}>
          <option value="">Choose…</option>
          {sources.filter(s => s.active || s.id === f.sourceId).map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
        {sources.length === 0 && <div style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>Add a source on the Sources tab first.</div>}
      </div>
      <div><label htmlFor="reg-title" style={label}>Title</label><input id="reg-title" style={input} value={f.title} onChange={e => set("title", e.target.value)} /></div>
      <div><label htmlFor="reg-summary" style={label}>What changed</label><textarea id="reg-summary" rows={3} style={input} value={f.summary} onChange={e => set("summary", e.target.value)} /></div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(160px, 1fr))", gap: 10 }}>
        <div><label htmlFor="reg-type" style={label}>Kind of change</label>
          <select id="reg-type" style={input} value={f.changeType} onChange={e => set("changeType", e.target.value)}>{CHANGE_TYPES.map(t => <option key={t[0]} value={t[0]}>{t[1]}</option>)}</select></div>
        <div><label htmlFor="reg-pub" style={label}>Published</label><input id="reg-pub" type="date" style={input} value={f.publicationDate} onChange={e => set("publicationDate", e.target.value)} /></div>
        <div><label htmlFor="reg-eff" style={label}>Takes effect</label><input id="reg-eff" type="date" style={input} value={f.effectiveDate} onChange={e => set("effectiveDate", e.target.value)} /></div>
      </div>
      <div><label htmlFor="reg-url" style={label}>Link to the notice (recorded only, never opened by the system)</label><input id="reg-url" style={input} value={f.referenceUrl} onChange={e => set("referenceUrl", e.target.value)} /></div>
      <fieldset style={{ border: "1px solid var(--hf-border)", borderRadius: 8, padding: 8 }}>
        <legend style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>Obligations it touches</legend>
        <div style={{ display: "flex", gap: 10, flexWrap: "wrap", maxHeight: 160, overflowY: "auto" }}>
          {obligations.map(o => (
            <label key={o.id} style={{ fontSize: 12, display: "flex", gap: 4, alignItems: "center" }}>
              <input type="checkbox" checked={f.obligationIds.includes(o.id)} onChange={() => toggle(o.id)} />{o.code}{o.name ? ` · ${o.name}` : ""}
            </label>
          ))}
          {obligations.length === 0 && <span style={{ fontSize: 12, color: "var(--hf-text-faint)" }}>The catalogue has no obligations yet.</span>}
        </div>
      </fieldset>
      {problem && f.title && <div style={bad}>{problem}</div>}
      {error && <div role="alert" style={bad}>{error}</div>}
      <div style={{ display: "flex", gap: 8 }}>
        <button style={{ ...solid, opacity: busy || problem ? 0.5 : 1 }} disabled={busy || !!problem} onClick={save}>{existing ? "Save changes" : "Record change"}</button>
        <button style={ghost} onClick={onCancel}>Cancel</button>
      </div>
    </div>
  )
}

function Sources() {
  const qc = useQueryClient()
  const canManage = usePermissions(["COMPLIANCE_SERVICES_MANAGE", "COMPLIANCE_SERVICES_ADMIN"])
  const { data = [] } = useQuery<Source[]>({ queryKey: ["cs-reg-sources"], queryFn: async () => unwrap<Source[]>(await apiClient.get(`${B}/sources`)) })
  const [name, setName] = useState("")
  const [authority, setAuthority] = useState("")
  const [url, setUrl] = useState("")
  const [kind, setKind] = useState("NOTICE")
  const [error, setError] = useState("")
  const problem = name || url ? sourceProblem(name, url) : null
  const refresh = () => qc.invalidateQueries({ queryKey: ["cs-reg-sources"] })

  const add = async () => {
    setError("")
    try { await apiClient.post(`${B}/sources`, { name: name.trim(), authority: authority.trim() || null, url: url.trim() || null, kind }); setName(""); setAuthority(""); setUrl(""); refresh() } catch (e) { setError(errText(e)) }
  }
  const toggle = async (s: Source) => {
    setError("")
    try { await apiClient.put(`${B}/sources/${s.id}/active`, { active: !s.active }); refresh() } catch (e) { setError(errText(e)) }
  }
  return (
    <div style={{ display: "grid", gap: 10 }}>
      <div style={{ fontSize: 13, color: "var(--hf-text-muted)" }}>Where changes come from: a statute, a regulator's notices, a guidance series. A source that is switched off can't be used for new changes.</div>
      {error && <div role="alert" style={bad}>{error}</div>}
      {canManage && (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))", gap: 8, alignItems: "end" }}>
          <div><label htmlFor="src-name" style={label}>Name</label><input id="src-name" style={input} value={name} onChange={e => setName(e.target.value)} /></div>
          <div><label htmlFor="src-auth" style={label}>Authority</label><input id="src-auth" style={input} value={authority} onChange={e => setAuthority(e.target.value)} /></div>
          <div><label htmlFor="src-url" style={label}>Web address</label><input id="src-url" style={input} value={url} onChange={e => setUrl(e.target.value)} /></div>
          <div><label htmlFor="src-kind" style={label}>Kind</label><select id="src-kind" style={input} value={kind} onChange={e => setKind(e.target.value)}>{KINDS.map(k => <option key={k[0]} value={k[0]}>{k[1]}</option>)}</select></div>
          <button style={{ ...solid, opacity: !name.trim() || problem ? 0.5 : 1 }} disabled={!name.trim() || !!problem} onClick={add}>Add source</button>
        </div>
      )}
      {problem && <div style={bad}>{problem}</div>}
      {data.length === 0 && <div style={{ fontSize: 13, color: "var(--hf-text-faint)" }}>No sources yet.</div>}
      {data.map(s => (
        <div key={s.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", opacity: s.active ? 1 : 0.6 }}>
          <span style={{ fontWeight: 700, fontSize: 13 }}>{s.name}</span>
          <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{[s.authority, KINDS.find(k => k[0] === s.kind)?.[1]].filter(Boolean).join(" · ")}</span>
          <span style={{ flex: 1 }} />
          {s.url && <a href={s.url} target="_blank" rel="noopener noreferrer" style={{ fontSize: 12 }}>Link</a>}
          {canManage && <button style={ghost} onClick={() => toggle(s)}>{s.active ? "Switch off" : "Switch on"}</button>}
        </div>
      ))}
    </div>
  )
}
