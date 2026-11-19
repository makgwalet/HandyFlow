// src/pages/legalcompliance/RegulatoryRegisterTab.tsx
//
// Regulatory library — instruments, versions, requirements.
// Endpoints (RegulatoryLibraryController, /api/v1/legalcompliance/regulatory):
//   GET  /instruments?scope=ALL|PLATFORM|MINE&topic=&q=      (paged)
//   GET  /instruments/{id}                                    (instrument + versions)
//   POST /instruments, PUT /instruments/{id}, POST /instruments/{id}/adopt, POST /instruments/{id}/mark-reviewed
//   POST /instruments/{id}/versions, PUT /versions/{id}, POST /versions/{id}/publish, POST /versions/{id}/repeal
//   GET/POST /versions/{id}/requirements, PUT/DELETE /requirements/{id}
//   GET  /events?entityType=&entityId=
//
// Rules the UI mirrors (enforced by the backend, never only here):
//   · Catalogue ("platform") entries are read-only — they list the instrument only. Adopt a copy to add requirements.
//   · A published version is immutable; an amendment is a new version (optionally carrying requirements forward).
//   · Writes need LEGALCOMPLIANCE_REGULATION_MANAGE or LEGALCOMPLIANCE_ADMIN.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { input, primaryBtn, ghostBtn, unwrap, one, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { Plus, BookMarked, ChevronDown, ChevronUp, Edit2, Trash2, CheckCircle2, Copy, History, Upload, Ban } from "lucide-react"

interface Instrument {
  id: string; platform: boolean; identifier: string; title: string; instrumentType: string; jurisdiction: string
  jurisdictionLevel: string; authority: string | null; topic: string; sourceUrl: string | null; description: string | null
  sourceInstrumentId: string | null; reviewed: boolean; reviewedByName: string | null; reviewedAt: string | null; createdAt: string
}
interface Version {
  id: string; instrumentId: string; versionLabel: string; status: string; effectiveDate: string | null; amendedDate: string | null
  repealedDate: string | null; sourceUrl: string | null; summary: string | null; publishedAt: string | null
  publishedByName: string | null; requirementCount: number
}
interface Requirement {
  id: string; versionId: string; reference: string; title: string; requirementText: string; category: string
  recurrence: string; minEmployees: number | null; industries: string[]; activities: string[]
  evidenceExpectation: string | null; previousRequirementId: string | null; platform: boolean
}
interface EventRow { id: string; eventType: string; detail: string | null; actorName: string | null; createdAt: string }

const TOPICS = ["POPIA", "COMPANIES_ACT", "BCEA", "OHS_ACT", "TAX", "FICA", "LABOUR", "BBBEE", "PUBLIC_FINANCE", "CONSUMER", "INDUSTRY_SPECIFIC", "OTHER"]
const TOPIC_LABELS: Record<string, string> = {
  POPIA: "POPIA / PAIA", COMPANIES_ACT: "Companies Act", BCEA: "BCEA", OHS_ACT: "OHS Act", TAX: "Tax", FICA: "FICA",
  LABOUR: "Labour", BBBEE: "B-BBEE", PUBLIC_FINANCE: "Public finance", CONSUMER: "Consumer", INDUSTRY_SPECIFIC: "Industry-specific", OTHER: "Other",
}
const TYPES = ["ACT", "REGULATION", "STANDARD", "CODE", "DIRECTIVE", "GUIDELINE", "LICENCE_CONDITION", "BY_LAW"]
const LEVELS = ["NATIONAL", "PROVINCIAL", "MUNICIPAL", "INDUSTRY", "INTERNATIONAL"]
const RECURRENCES = ["ONCE", "MONTHLY", "QUARTERLY", "ANNUALLY"]
const VERSION_CFG: Record<string, { color: string; bg: string }> = {
  DRAFT: { color: "#92400E", bg: "#FEF3C7" }, CURRENT: { color: "#166534", bg: "#DCFCE7" },
  SUPERSEDED: { color: "#475569", bg: "#F1F5F9" }, REPEALED: { color: "#991B1B", bg: "#FEE2E2" },
}

const csv = (s: string) => s.split(",").map(x => x.trim()).filter(Boolean)

const EMPTY_INSTRUMENT = { title: "", identifier: "", instrumentType: "ACT", jurisdictionLevel: "NATIONAL", authority: "", topic: "OTHER", sourceUrl: "", description: "" }
const EMPTY_VERSION = { versionLabel: "", effectiveDate: "", amendedDate: "", sourceUrl: "", summary: "", carryForwardFromVersionId: "" }
const EMPTY_REQ = { reference: "", title: "", requirementText: "", category: "OTHER", recurrence: "ANNUALLY", minEmployees: "", industries: "", activities: "", evidenceExpectation: "" }

export default function RegulatoryRegisterTab() {
  const qc = useQueryClient()
  const hasRegulationManage = usePermission("LEGALCOMPLIANCE_REGULATION_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canEdit = hasRegulationManage || hasAdmin

  const [scope, setScope] = useState("ALL")
  const [topic, setTopic] = useState("")
  const [search, setSearch] = useState("")
  const [expanded, setExpanded] = useState<string | null>(null)
  const [openVersion, setOpenVersion] = useState<string | null>(null)
  const [showHistory, setShowHistory] = useState<string | null>(null)
  const [instrumentForm, setInstrumentForm] = useState<{ id: string | null; data: typeof EMPTY_INSTRUMENT } | null>(null)
  const [versionForm, setVersionForm] = useState<{ instrumentId: string; id: string | null; data: typeof EMPTY_VERSION } | null>(null)
  const [reqForm, setReqForm] = useState<{ versionId: string; id: string | null; data: typeof EMPTY_REQ } | null>(null)
  const [repeal, setRepeal] = useState<Version | null>(null)
  const [repealForm, setRepealForm] = useState({ repealedDate: "", reason: "" })
  const [apiError, setApiError] = useState("")

  const { data: instruments = [], isLoading } = useQuery<Instrument[]>({
    queryKey: ["lc-instruments", scope, topic, search],
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/regulatory/instruments", { params: { scope, size: 200, ...(topic ? { topic } : {}), ...(search ? { q: search } : {}) } })),
  })
  const { data: detail } = useQuery<{ instrument: Instrument; versions: Version[] }>({
    queryKey: ["lc-instrument", expanded], enabled: !!expanded,
    queryFn: async () => one(await apiClient.get(`/api/v1/legalcompliance/regulatory/instruments/${expanded}`)),
  })
  const { data: requirements = [] } = useQuery<Requirement[]>({
    queryKey: ["lc-requirements", openVersion], enabled: !!openVersion,
    queryFn: async () => unwrap(await apiClient.get(`/api/v1/legalcompliance/regulatory/versions/${openVersion}/requirements`)),
  })
  const { data: events = [] } = useQuery<EventRow[]>({
    queryKey: ["lc-events", showHistory], enabled: !!showHistory,
    queryFn: async () => unwrap(await apiClient.get("/api/v1/legalcompliance/regulatory/events", { params: { entityType: "INSTRUMENT", entityId: showHistory } })),
  })

  const refresh = () => {
    qc.invalidateQueries({ queryKey: ["lc-instruments"] }); qc.invalidateQueries({ queryKey: ["lc-instrument"] })
    qc.invalidateQueries({ queryKey: ["lc-requirements"] }); qc.invalidateQueries({ queryKey: ["lc-events"] })
    qc.invalidateQueries({ queryKey: ["lc-worklist"] })
    // publishing or repealing raises a regulatory change (and may carry decisions forward) — refresh those views too
    qc.invalidateQueries({ queryKey: ["lc-changes"] }); qc.invalidateQueries({ queryKey: ["lc-changes-summary"] }); qc.invalidateQueries({ queryKey: ["lc-impacts"] })
    qc.invalidateQueries({ queryKey: ["lc-obligations"] }); qc.invalidateQueries({ queryKey: ["lc-calendar-30"] })
  }
  const done = () => { refresh(); setApiError("") }
  const fail = (m: string) => (e: any) => setApiError(errMsg(e, m))
  const base = "/api/v1/legalcompliance/regulatory"

  const saveInstrument = useMutation({
    mutationFn: ({ id, data }: { id: string | null; data: any }) => id ? apiClient.put(`${base}/instruments/${id}`, data) : apiClient.post(`${base}/instruments`, data),
    onSuccess: () => { done(); setInstrumentForm(null) }, onError: fail("Failed to save instrument"),
  })
  const adopt = useMutation({
    mutationFn: (id: string) => apiClient.post(`${base}/instruments/${id}/adopt`), onSuccess: (r) => { done(); setScope("MINE"); setExpanded(one(r).id) }, onError: fail("Failed to adopt instrument"),
  })
  const markReviewed = useMutation({
    mutationFn: (id: string) => apiClient.post(`${base}/instruments/${id}/mark-reviewed`), onSuccess: done, onError: fail("Failed to mark reviewed"),
  })
  const saveVersion = useMutation({
    mutationFn: ({ instrumentId, id, data }: { instrumentId: string; id: string | null; data: any }) =>
      id ? apiClient.put(`${base}/versions/${id}`, data) : apiClient.post(`${base}/instruments/${instrumentId}/versions`, data),
    onSuccess: () => { done(); setVersionForm(null) }, onError: fail("Failed to save version"),
  })
  const publish = useMutation({
    mutationFn: (id: string) => apiClient.post(`${base}/versions/${id}/publish`), onSuccess: done, onError: fail("Failed to publish version"),
  })
  const repealVersion = useMutation({
    mutationFn: ({ id, body }: { id: string; body: any }) => apiClient.post(`${base}/versions/${id}/repeal`, body),
    onSuccess: () => { done(); setRepeal(null) }, onError: fail("Failed to repeal version"),
  })
  const saveReq = useMutation({
    mutationFn: ({ versionId, id, body }: { versionId: string; id: string | null; body: any }) =>
      id ? apiClient.put(`${base}/requirements/${id}`, body) : apiClient.post(`${base}/versions/${versionId}/requirements`, body),
    onSuccess: () => { done(); setReqForm(null) }, onError: fail("Failed to save requirement"),
  })
  const delReq = useMutation({
    mutationFn: (id: string) => apiClient.delete(`${base}/requirements/${id}`), onSuccess: done, onError: fail("Failed to remove requirement"),
  })

  const openInstrument = (i?: Instrument) => { setApiError(""); setInstrumentForm({ id: i?.id ?? null, data: i ? {
    title: i.title, identifier: i.identifier, instrumentType: i.instrumentType, jurisdictionLevel: i.jurisdictionLevel,
    authority: i.authority ?? "", topic: i.topic, sourceUrl: i.sourceUrl ?? "", description: i.description ?? "" } : EMPTY_INSTRUMENT }) }
  const openVersionForm = (instrumentId: string, v?: Version) => { setApiError(""); setVersionForm({ instrumentId, id: v?.id ?? null, data: v ? {
    versionLabel: v.versionLabel, effectiveDate: v.effectiveDate ?? "", amendedDate: v.amendedDate ?? "", sourceUrl: v.sourceUrl ?? "",
    summary: v.summary ?? "", carryForwardFromVersionId: "" } : EMPTY_VERSION }) }
  const openReq = (versionId: string, r?: Requirement) => { setApiError(""); setReqForm({ versionId, id: r?.id ?? null, data: r ? {
    reference: r.reference, title: r.title, requirementText: r.requirementText, category: r.category, recurrence: r.recurrence,
    minEmployees: r.minEmployees?.toString() ?? "", industries: r.industries.join(", "), activities: r.activities.join(", "),
    evidenceExpectation: r.evidenceExpectation ?? "" } : EMPTY_REQ }) }

  return (
    <div>
      <div style={{ background: "#FFFBEB", border: "1px solid #FDE68A", borderRadius: 10, padding: "10px 14px", fontSize: 12.5, color: "#92400E", marginBottom: 16 }}>
        Catalogue entries name the instrument only — no requirement text is supplied. Adopt an entry, author its requirements from the official source,
        have a qualified person review it, then publish. Nothing here is assumed to apply to your organisation until you assess it.
      </div>

      <div style={{ display: "flex", justifyContent: "space-between", gap: 10, flexWrap: "wrap", marginBottom: 16 }}>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap", alignItems: "center" }}>
          {[["ALL", "All"], ["PLATFORM", "Catalogue"], ["MINE", "My library"]].map(([v, l]) => (
            <button key={v} onClick={() => setScope(v)} style={{ padding: "5px 12px", borderRadius: 20, fontSize: 12, cursor: "pointer", border: "none", fontWeight: scope === v ? 600 : 400,
              background: scope === v ? "#4338CA" : "#F1F5F9", color: scope === v ? "#fff" : "#64748B" }}>{l}</button>
          ))}
          <select value={topic} onChange={e => setTopic(e.target.value)} style={{ ...input, width: "auto", padding: "6px 10px", fontSize: 12 }}>
            <option value="">All topics</option>
            {TOPICS.map(t => <option key={t} value={t}>{TOPIC_LABELS[t]}</option>)}
          </select>
          <input value={search} onChange={e => setSearch(e.target.value)} placeholder="Search title or identifier" style={{ ...input, width: 220, padding: "6px 10px", fontSize: 12 }} />
        </div>
        {canEdit && <button onClick={() => openInstrument()} style={{ ...primaryBtn, display: "flex", alignItems: "center", gap: 7 }}><Plus size={15} /> New instrument</button>}
      </div>

      <Err m={apiError} />

      {isLoading ? <div style={{ textAlign: "center", padding: 40, color: "#94A3B8" }}>Loading regulatory library...</div>
        : instruments.length === 0 ? (
          <div style={{ textAlign: "center", padding: "60px 20px", color: "#94A3B8" }}>
            <BookMarked size={40} style={{ marginBottom: 12, opacity: 0.4 }} />
            <div style={{ fontWeight: 600, color: "#475569" }}>No instruments found</div>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
            {instruments.map(i => {
              const open = expanded === i.id
              return (
                <div key={i.id} style={{ border: "1px solid #E2E8F0", borderRadius: 12, overflow: "hidden" }}>
                  <div style={{ padding: "14px 18px", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, background: "#fff" }}>
                    <div style={{ minWidth: 0 }}>
                      <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap", marginBottom: 3 }}>
                        <span style={{ fontWeight: 700, fontSize: 15, color: "#0F172A" }}>{i.title}</span>
                        <span style={{ fontSize: 11, color: "#64748B", background: "#F1F5F9", padding: "1px 8px", borderRadius: 20 }}>{TOPIC_LABELS[i.topic] ?? i.topic}</span>
                        {i.platform ? <Badge text="Catalogue" color="#1D4ED8" bg="#DBEAFE" /> : <Badge text="My library" color="#4338CA" bg="#E0E7FF" />}
                        {i.reviewed ? <Badge text="Reviewed" color="#166534" bg="#DCFCE7" /> : <Badge text="Unreviewed" color="#92400E" bg="#FEF3C7" />}
                      </div>
                      <div style={{ fontSize: 12, color: "#94A3B8" }}>{i.identifier} · {i.instrumentType.replace("_", " ")} · {i.jurisdictionLevel} · {i.authority ?? "—"}</div>
                    </div>
                    <div style={{ display: "flex", gap: 6, alignItems: "center", flexShrink: 0 }}>
                      {i.platform && canEdit && <button onClick={() => adopt.mutate(i.id)} disabled={adopt.isPending} style={{ ...ghostBtn, display: "flex", gap: 5, alignItems: "center", color: "#4338CA" }}><Copy size={13} /> Adopt</button>}
                      {!i.platform && canEdit && <>
                        <button title="Mark reviewed" onClick={() => markReviewed.mutate(i.id)} style={{ background: "#DCFCE7", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#166534" }}><CheckCircle2 size={13} /></button>
                        <button title="Edit" onClick={() => openInstrument(i)} style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#1D4ED8" }}><Edit2 size={13} /></button>
                      </>}
                      {!i.platform && <button title="History" onClick={() => setShowHistory(showHistory === i.id ? null : i.id)} style={{ background: "#F1F5F9", border: "none", borderRadius: 6, padding: "6px 8px", cursor: "pointer", color: "#475569" }}><History size={13} /></button>}
                      <button onClick={() => { setExpanded(open ? null : i.id); setOpenVersion(null) }} style={{ background: "none", border: "none", cursor: "pointer", color: "#94A3B8" }}>
                        {open ? <ChevronUp size={18} /> : <ChevronDown size={18} />}
                      </button>
                    </div>
                  </div>

                  {showHistory === i.id && (
                    <div style={{ borderTop: "1px solid #F1F5F9", padding: "12px 18px", background: "#F8FAFC", fontSize: 12 }}>
                      {events.length === 0 ? <span style={{ color: "#94A3B8" }}>No history yet</span> : events.map(e => (
                        <div key={e.id} style={{ padding: "3px 0", color: "#475569" }}><b>{e.eventType}</b> · {fmtDateTime(e.createdAt)} · {e.actorName ?? "System"}{e.detail ? ` — ${e.detail}` : ""}</div>
                      ))}
                    </div>
                  )}

                  {open && detail && detail.instrument.id === i.id && (
                    <div style={{ borderTop: "1px solid #F1F5F9", padding: "16px 18px", background: "#F8FAFC" }}>
                      {i.description && <p style={{ margin: "0 0 12px", fontSize: 13, color: "#475569" }}>{i.description}</p>}
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 10 }}>
                        <div style={{ fontSize: 13, fontWeight: 700, color: "#0F172A" }}>Versions</div>
                        {!i.platform && canEdit && <button onClick={() => openVersionForm(i.id)} style={{ ...ghostBtn, padding: "6px 12px" }}>+ New version</button>}
                      </div>
                      {detail.versions.length === 0 && <div style={{ fontSize: 13, color: "#94A3B8" }}>{i.platform ? "The catalogue lists this instrument only. Adopt it to add requirements." : "No versions yet."}</div>}
                      {detail.versions.map(v => {
                        const cfg = VERSION_CFG[v.status] ?? VERSION_CFG.DRAFT
                        const vOpen = openVersion === v.id
                        return (
                          <div key={v.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, marginBottom: 8 }}>
                            <div style={{ padding: "10px 14px", display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap" }}>
                              <div>
                                <span style={{ fontWeight: 600, fontSize: 14 }}>{v.versionLabel}</span>{" "}
                                <Badge text={v.status} color={cfg.color} bg={cfg.bg} />
                                <div style={{ fontSize: 12, color: "#94A3B8", marginTop: 2 }}>
                                  Effective {fmtDate(v.effectiveDate)} · {v.requirementCount} requirement(s)
                                  {v.publishedAt ? ` · published ${fmtDate(v.publishedAt)} by ${v.publishedByName ?? "—"}` : ""}
                                  {v.repealedDate ? ` · repealed ${fmtDate(v.repealedDate)}` : ""}
                                </div>
                              </div>
                              <div style={{ display: "flex", gap: 6 }}>
                                {!i.platform && canEdit && v.status === "DRAFT" && <>
                                  <button onClick={() => openVersionForm(i.id, v)} style={{ ...ghostBtn, padding: "5px 10px" }}>Edit</button>
                                  <button onClick={() => { if (confirm(`Publish "${v.versionLabel}"? Its requirements become immutable and any current version is superseded. Unchanged requirements keep their decisions; amended, removed and new ones are raised as items to review on the Regulatory Changes tab.`)) publish.mutate(v.id) }}
                                    style={{ ...primaryBtn, padding: "5px 12px", display: "flex", gap: 5, alignItems: "center" }}><Upload size={12} /> Publish</button>
                                </>}
                                {!i.platform && canEdit && (v.status === "CURRENT" || v.status === "SUPERSEDED") &&
                                  <button onClick={() => { setRepeal(v); setRepealForm({ repealedDate: "", reason: "" }); setApiError("") }} style={{ ...ghostBtn, padding: "5px 10px", color: "#991B1B", display: "flex", gap: 5, alignItems: "center" }}><Ban size={12} /> Repeal</button>}
                                <button onClick={() => setOpenVersion(vOpen ? null : v.id)} style={{ ...ghostBtn, padding: "5px 10px" }}>{vOpen ? "Hide" : "Requirements"}</button>
                              </div>
                            </div>
                            {vOpen && (
                              <div style={{ borderTop: "1px solid #F1F5F9", padding: "10px 14px" }}>
                                {!i.platform && canEdit && v.status === "DRAFT" && <button onClick={() => openReq(v.id)} style={{ ...ghostBtn, padding: "5px 10px", marginBottom: 8 }}>+ Add requirement</button>}
                                {requirements.length === 0 && <div style={{ fontSize: 13, color: "#94A3B8" }}>No requirements in this version.</div>}
                                {requirements.map(r => (
                                  <div key={r.id} style={{ padding: "8px 0", borderBottom: "1px solid #F1F5F9" }}>
                                    <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
                                      <div style={{ fontSize: 13.5 }}><b>{r.reference}</b> · {r.title}
                                        <span style={{ marginLeft: 6, fontSize: 11, color: "#64748B", background: "#F1F5F9", padding: "1px 7px", borderRadius: 20 }}>{TOPIC_LABELS[r.category] ?? r.category}</span>
                                        <span style={{ marginLeft: 6, fontSize: 11, color: "#64748B" }}>{r.recurrence}</span>
                                      </div>
                                      {!i.platform && canEdit && v.status === "DRAFT" && (
                                        <div style={{ display: "flex", gap: 5 }}>
                                          <button onClick={() => openReq(v.id, r)} style={{ background: "#EFF6FF", border: "none", borderRadius: 6, padding: "4px 7px", cursor: "pointer", color: "#1D4ED8" }}><Edit2 size={12} /></button>
                                          <button onClick={() => { if (confirm(`Remove ${r.reference} from this draft?`)) delReq.mutate(r.id) }} style={{ background: "#FEF2F2", border: "none", borderRadius: 6, padding: "4px 7px", cursor: "pointer", color: "#DC2626" }}><Trash2 size={12} /></button>
                                        </div>
                                      )}
                                    </div>
                                    <div style={{ fontSize: 12.5, color: "#475569", marginTop: 3, whiteSpace: "pre-wrap" }}>{r.requirementText}</div>
                                    <div style={{ fontSize: 11.5, color: "#94A3B8", marginTop: 3 }}>
                                      Conditions: {r.minEmployees != null ? `${r.minEmployees}+ employees` : "no size threshold"} · industries {r.industries.length ? r.industries.join(", ") : "not recorded"} · activities {r.activities.length ? r.activities.join(", ") : "not recorded"}
                                      {r.evidenceExpectation ? ` · evidence: ${r.evidenceExpectation}` : ""}
                                      {r.previousRequirementId ? " · carried forward" : ""}
                                    </div>
                                  </div>
                                ))}
                              </div>
                            )}
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}

      {instrumentForm && (
        <Modal title={instrumentForm.id ? "Edit instrument" : "New instrument"} onClose={() => setInstrumentForm(null)}>
          <Err m={apiError} />
          {(() => { const f = instrumentForm.data; const set = (k: string, v: string) => setInstrumentForm({ ...instrumentForm, data: { ...f, [k]: v } }); return <>
            <Field l="Title *"><input style={input} value={f.title} onChange={e => set("title", e.target.value)} /></Field>
            <Field l="Identifier * (e.g. Act 4 of 2013)"><input style={input} value={f.identifier} onChange={e => set("identifier", e.target.value)} /></Field>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <Field l="Type"><select style={input} value={f.instrumentType} onChange={e => set("instrumentType", e.target.value)}>{TYPES.map(t => <option key={t}>{t}</option>)}</select></Field>
              <Field l="Level"><select style={input} value={f.jurisdictionLevel} onChange={e => set("jurisdictionLevel", e.target.value)}>{LEVELS.map(t => <option key={t}>{t}</option>)}</select></Field>
            </div>
            <Field l="Topic"><select style={input} value={f.topic} onChange={e => set("topic", e.target.value)}>{TOPICS.map(t => <option key={t} value={t}>{TOPIC_LABELS[t]}</option>)}</select></Field>
            <Field l="Issuing authority"><input style={input} value={f.authority} onChange={e => set("authority", e.target.value)} /></Field>
            <Field l="Official source URL"><input style={input} value={f.sourceUrl} onChange={e => set("sourceUrl", e.target.value)} placeholder="https://" /></Field>
            <Field l="Description"><textarea style={{ ...input, minHeight: 70 }} value={f.description} onChange={e => set("description", e.target.value)} /></Field>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
              <button style={ghostBtn} onClick={() => setInstrumentForm(null)}>Cancel</button>
              <button style={primaryBtn} disabled={saveInstrument.isPending} onClick={() => saveInstrument.mutate({ id: instrumentForm.id, data: { ...f, authority: f.authority || null, sourceUrl: f.sourceUrl || null, description: f.description || null } })}>Save</button>
            </div></> })()}
        </Modal>
      )}

      {versionForm && detail && (
        <Modal title={versionForm.id ? "Edit draft version" : "New draft version"} onClose={() => setVersionForm(null)}>
          <Err m={apiError} />
          {(() => { const f = versionForm.data; const set = (k: string, v: string) => setVersionForm({ ...versionForm, data: { ...f, [k]: v } }); return <>
            <Field l="Version label * (e.g. As amended 2026)"><input style={input} value={f.versionLabel} onChange={e => set("versionLabel", e.target.value)} /></Field>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <Field l="Effective date (needed to publish)"><input type="date" style={input} value={f.effectiveDate} onChange={e => set("effectiveDate", e.target.value)} /></Field>
              <Field l="Amended date"><input type="date" style={input} value={f.amendedDate} onChange={e => set("amendedDate", e.target.value)} /></Field>
            </div>
            <Field l="Source document URL"><input style={input} value={f.sourceUrl} onChange={e => set("sourceUrl", e.target.value)} /></Field>
            <Field l="Summary of changes"><textarea style={{ ...input, minHeight: 60 }} value={f.summary} onChange={e => set("summary", e.target.value)} /></Field>
            {!versionForm.id && detail.versions.length > 0 && (
              <Field l="Start from an earlier version">
                <select style={input} value={f.carryForwardFromVersionId} onChange={e => set("carryForwardFromVersionId", e.target.value)}>
                  <option value="">Blank — add requirements myself</option>
                  {detail.versions.map(v => <option key={v.id} value={v.id}>{v.versionLabel} ({v.status}, {v.requirementCount} req.)</option>)}
                </select>
              </Field>
            )}
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
              <button style={ghostBtn} onClick={() => setVersionForm(null)}>Cancel</button>
              <button style={primaryBtn} disabled={saveVersion.isPending} onClick={() => saveVersion.mutate({ instrumentId: versionForm.instrumentId, id: versionForm.id, data: {
                versionLabel: f.versionLabel, effectiveDate: f.effectiveDate || null, amendedDate: f.amendedDate || null, sourceUrl: f.sourceUrl || null,
                summary: f.summary || null, carryForwardFromVersionId: f.carryForwardFromVersionId || null } })}>Save</button>
            </div></> })()}
        </Modal>
      )}

      {reqForm && (
        <Modal wide title={reqForm.id ? "Edit requirement" : "Add requirement"} onClose={() => setReqForm(null)}>
          <Err m={apiError} />
          {(() => { const f = reqForm.data; const set = (k: string, v: string) => setReqForm({ ...reqForm, data: { ...f, [k]: v } }); return <>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 2fr", gap: 10 }}>
              <Field l="Reference * (e.g. s18)"><input style={input} value={f.reference} onChange={e => set("reference", e.target.value)} /></Field>
              <Field l="Title *"><input style={input} value={f.title} onChange={e => set("title", e.target.value)} /></Field>
            </div>
            <Field l="Requirement text * (from the official source, 10+ characters)"><textarea style={{ ...input, minHeight: 90 }} value={f.requirementText} onChange={e => set("requirementText", e.target.value)} /></Field>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 10 }}>
              <Field l="Category"><select style={input} value={f.category} onChange={e => set("category", e.target.value)}>{TOPICS.map(t => <option key={t} value={t}>{TOPIC_LABELS[t]}</option>)}</select></Field>
              <Field l="Review frequency"><select style={input} value={f.recurrence} onChange={e => set("recurrence", e.target.value)}>{RECURRENCES.map(t => <option key={t}>{t}</option>)}</select></Field>
            </div>
            <div style={{ fontSize: 12, color: "#64748B", margin: "2px 0 8px" }}>Applicability conditions feed a suggestion only. Leave a condition empty if it is not known — empty never means "applies to everyone".</div>
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: 10 }}>
              <Field l="Min. employees"><input type="number" min={0} style={input} value={f.minEmployees} onChange={e => set("minEmployees", e.target.value)} /></Field>
              <Field l="Industries (comma-separated)"><input style={input} value={f.industries} onChange={e => set("industries", e.target.value)} /></Field>
              <Field l="Activities (comma-separated)"><input style={input} value={f.activities} onChange={e => set("activities", e.target.value)} placeholder="e.g. PROCESSES_PERSONAL_INFO" /></Field>
            </div>
            <Field l="Evidence expected"><input style={input} value={f.evidenceExpectation} onChange={e => set("evidenceExpectation", e.target.value)} /></Field>
            <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
              <button style={ghostBtn} onClick={() => setReqForm(null)}>Cancel</button>
              <button style={primaryBtn} disabled={saveReq.isPending} onClick={() => saveReq.mutate({ versionId: reqForm.versionId, id: reqForm.id, body: {
                reference: f.reference, title: f.title, requirementText: f.requirementText, category: f.category, recurrence: f.recurrence,
                minEmployees: f.minEmployees === "" ? null : Number(f.minEmployees), industries: csv(f.industries), activities: csv(f.activities),
                evidenceExpectation: f.evidenceExpectation || null } })}>Save</button>
            </div></> })()}
        </Modal>
      )}

      {repeal && (
        <Modal title={`Repeal "${repeal.versionLabel}"`} onClose={() => setRepeal(null)}>
          <Err m={apiError} />
          <Field l="Repeal date *"><input type="date" style={input} value={repealForm.repealedDate} onChange={e => setRepealForm({ ...repealForm, repealedDate: e.target.value })} /></Field>
          <Field l="Reason *"><textarea style={{ ...input, minHeight: 70 }} value={repealForm.reason} onChange={e => setRepealForm({ ...repealForm, reason: e.target.value })} /></Field>
          <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}>
            <button style={ghostBtn} onClick={() => setRepeal(null)}>Cancel</button>
            <button style={{ ...primaryBtn, background: "#B91C1C" }} disabled={!repealForm.repealedDate || !repealForm.reason.trim() || repealVersion.isPending}
              onClick={() => repealVersion.mutate({ id: repeal.id, body: repealForm })}>Repeal</button>
          </div>
        </Modal>
      )}
    </div>
  )
}
