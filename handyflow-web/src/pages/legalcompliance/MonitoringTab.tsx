// src/pages/legalcompliance/MonitoringTab.tsx
//
// Regulatory monitoring. Endpoints (/api/v1/legalcompliance/monitoring): GET /summary · GET/POST /sources · PUT /sources/{id} · POST /sources/{id}/fetch
// GET /items?status=&sourceId= · POST /items/{id}/decision|reopen · GET /items/{id}/suggestions · POST /assist
// Feeds are fetched daily; a person decides what matters. "Possibly affected" suggestions are keyword matches against your own obligations, controls and policies:
// they show the words that matched, are never saved, and are a starting point for an assessment, not a conclusion.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { usePermission } from "../../hooks/usePermission"
import { one, unwrap, input, primaryBtn, ghostBtn, fmtDate, fmtDateTime, errMsg } from "./lcHelpers"
import { Modal, Field, Err, Badge } from "./LcUi"
import { Plus, RefreshCw, ExternalLink } from "lucide-react"

const base = "/api/v1/legalcompliance/monitoring"

type ItemStatus = "NEW" | "RELEVANT" | "NOT_RELEVANT"
interface Summary { activeSources: number; sourcesFailing: number; newItems: number }
interface Source { id: string; name: string; authority: string | null; feedUrl: string; active: boolean; lastFetchedAt: string | null; lastStatus: "NEVER" | "OK" | "FAILED"; lastError: string | null; lastNewItems: number; consecutiveFailures: number; needsAttention: boolean }
interface Item { id: string; sourceId: string; sourceName: string; title: string; link: string | null; summary: string | null; publishedOn: string | null; fetchedAt: string; status: ItemStatus; decisionNote: string | null; decidedByName: string | null; decidedAt: string | null }
interface Suggestion { type: "OBLIGATION" | "CONTROL" | "POLICY"; id: string; reference: string | null; label: string; strength: "HIGH" | "MEDIUM" | "LOW"; score: number; referenceMentioned: boolean; matchedTerms: string[] }
interface FetchOutcome { fetched: number; added: number; failed: boolean; error: string | null }

const strengthTone = { HIGH: { c: "#991B1B", b: "#FEE2E2" }, MEDIUM: { c: "#92400E", b: "#FEF3C7" }, LOW: { c: "#475569", b: "#E2E8F0" } } as const
const typeLabel = { OBLIGATION: "Obligation", CONTROL: "Control", POLICY: "Policy" } as const
const statusLabel: Record<ItemStatus, string> = { NEW: "To review", RELEVANT: "Relevant", NOT_RELEVANT: "Not relevant" }

function Suggestions({ rows, loading, error }: { rows: Suggestion[] | undefined; loading: boolean; error: string }) {
  if (loading) return <p style={{ fontSize: 12, color: "#64748B" }}>Checking your registers…</p>
  if (error) return <Err m={error} />
  if (!rows) return null
  return (
    <div style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 8, padding: "10px 12px", marginTop: 8 }}>
      <div style={{ fontSize: 12, fontWeight: 700, color: "#0F172A" }}>Possibly affected (suggestion only)</div>
      <div style={{ fontSize: 11, color: "#64748B", margin: "2px 0 8px" }}>Matched on shared words, not meaning. It can miss synonyms and match unrelated uses of a word, so check each one before relying on it. Nothing here is saved.</div>
      {rows.length === 0 ? <div style={{ fontSize: 12, color: "#475569" }}>No obligation, control or policy shares enough wording with this text. That does not mean nothing is affected.</div> : rows.map(s => (
        <div key={`${s.type}-${s.id}`} style={{ display: "flex", gap: 8, alignItems: "baseline", padding: "6px 0", borderTop: "1px solid #E2E8F0", flexWrap: "wrap" }}>
          <Badge text={`${s.strength === "HIGH" ? "Strong" : s.strength === "MEDIUM" ? "Possible" : "Weak"} match`} color={strengthTone[s.strength].c} bg={strengthTone[s.strength].b} />
          <span style={{ fontSize: 12, color: "#64748B" }}>{typeLabel[s.type]}{s.reference ? ` · ${s.reference}` : ""}</span>
          <span style={{ fontSize: 13, color: "#0F172A", flex: 1, minWidth: 200 }}>{s.label}</span>
          <span style={{ fontSize: 11, color: "#64748B" }}>{s.referenceMentioned ? "Named in the text. " : ""}{s.matchedTerms.length > 0 ? `Matched: ${s.matchedTerms.join(", ")}` : ""}</span>
        </div>
      ))}
    </div>
  )
}

function SourceModal({ onClose, onSaved }: { onClose: () => void; onSaved: () => void }) {
  const [f, setF] = useState({ name: "", authority: "", feedUrl: "" })
  const [err, setErr] = useState("")
  const save = useMutation({ mutationFn: () => apiClient.post(`${base}/sources`, { name: f.name, authority: f.authority || null, feedUrl: f.feedUrl }), onSuccess: onSaved, onError: (e: unknown) => setErr(errMsg(e, "Could not add the source")) })
  return (
    <Modal title="Follow a regulatory feed" onClose={onClose}>
      <Err m={err} />
      <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>Public RSS or Atom feeds only, over https. The feed is read once a day; HandyFlow sends no information about you to it.</p>
      <Field l="Name *"><input style={input} value={f.name} onChange={e => setF({ ...f, name: e.target.value })} /></Field>
      <Field l="Authority"><input style={input} placeholder="e.g. Information Regulator" value={f.authority} onChange={e => setF({ ...f, authority: e.target.value })} /></Field>
      <Field l="Feed address *"><input style={input} placeholder="https://…" value={f.feedUrl} onChange={e => setF({ ...f, feedUrl: e.target.value })} /></Field>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={onClose}>Cancel</button><button style={primaryBtn} disabled={save.isPending} onClick={() => { setErr(""); save.mutate() }}>Add source</button></div>
    </Modal>
  )
}

function DecisionModal({ item, onClose, onSaved }: { item: Item; onClose: () => void; onSaved: () => void }) {
  const [outcome, setOutcome] = useState<"RELEVANT" | "NOT_RELEVANT">("RELEVANT")
  const [note, setNote] = useState("")
  const [err, setErr] = useState("")
  const save = useMutation({ mutationFn: () => apiClient.post(`${base}/items/${item.id}/decision`, { outcome, note: note || null }), onSuccess: onSaved, onError: (e: unknown) => setErr(errMsg(e, "Could not record the decision")) })
  return (
    <Modal title="Triage this item" onClose={onClose}>
      <Err m={err} />
      <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", marginBottom: 10 }}>{item.title}</div>
      <Field l="Decision *"><select style={input} value={outcome} onChange={e => setOutcome(e.target.value as "RELEVANT" | "NOT_RELEVANT")}><option value="RELEVANT">Relevant: follow it up</option><option value="NOT_RELEVANT">Not relevant</option></select></Field>
      <Field l={outcome === "NOT_RELEVANT" ? "Reason * (kept on record)" : "Note"}><textarea style={{ ...input, minHeight: 70 }} value={note} onChange={e => setNote(e.target.value)} /></Field>
      {outcome === "RELEVANT" && <p style={{ fontSize: 12, color: "#64748B" }}>Marking an item relevant does not create a regulatory change or change any obligation. Record the change in the Regulatory register, or review the affected obligations, as a next step.</p>}
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={onClose}>Cancel</button><button style={primaryBtn} disabled={save.isPending} onClick={() => { setErr(""); save.mutate() }}>Record decision</button></div>
    </Modal>
  )
}

function ReopenModal({ item, onClose, onSaved }: { item: Item; onClose: () => void; onSaved: () => void }) {
  const [reason, setReason] = useState("")
  const [err, setErr] = useState("")
  const save = useMutation({ mutationFn: () => apiClient.post(`${base}/items/${item.id}/reopen`, { reason }), onSuccess: onSaved, onError: (e: unknown) => setErr(errMsg(e, "Could not reopen the item")) })
  return (
    <Modal title="Reopen this item" onClose={onClose}>
      <Err m={err} />
      <div style={{ fontSize: 13, fontWeight: 600, color: "#0F172A", marginBottom: 10 }}>{item.title}</div>
      <p style={{ fontSize: 12, color: "#64748B", marginTop: 0 }}>The earlier decision stays in the audit trail. The item goes back to the inbox to be triaged again.</p>
      <Field l="Reason * (at least 5 characters)"><textarea style={{ ...input, minHeight: 70 }} value={reason} onChange={e => setReason(e.target.value)} /></Field>
      <div style={{ display: "flex", justifyContent: "flex-end", gap: 8 }}><button style={ghostBtn} onClick={onClose}>Cancel</button><button style={primaryBtn} disabled={save.isPending} onClick={() => { setErr(""); save.mutate() }}>Reopen</button></div>
    </Modal>
  )
}

function InboxPanel({ canManage, sources }: { canManage: boolean; sources: Source[] }) {
  const qc = useQueryClient()
  const [status, setStatus] = useState<ItemStatus | "">("NEW")
  const [sourceId, setSourceId] = useState("")
  const [deciding, setDeciding] = useState<Item | null>(null)
  const [open, setOpen] = useState<string | null>(null)
  const [reopening, setReopening] = useState<Item | null>(null)
  const { data: items = [], isLoading } = useQuery<Item[]>({ queryKey: ["lc-mon", "items", status, sourceId], queryFn: async () => unwrap(await apiClient.get(`${base}/items`, { params: { status: status || undefined, sourceId: sourceId || undefined } })) })
  const sug = useQuery<Suggestion[]>({ queryKey: ["lc-mon", "suggest", open], enabled: !!open, queryFn: async () => unwrap(await apiClient.get(`${base}/items/${open}/suggestions`)) })
  const refresh = () => { qc.invalidateQueries({ queryKey: ["lc-mon"] }) }
  return (
    <div>
      <div style={{ display: "flex", gap: 10, marginBottom: 12, flexWrap: "wrap" }}>
        <select style={{ ...input, width: 170 }} value={status} onChange={e => setStatus(e.target.value as ItemStatus | "")} aria-label="Status"><option value="NEW">To review</option><option value="RELEVANT">Relevant</option><option value="NOT_RELEVANT">Not relevant</option><option value="">All</option></select>
        <select style={{ ...input, width: 220 }} value={sourceId} onChange={e => setSourceId(e.target.value)} aria-label="Source"><option value="">All sources</option>{sources.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select>
      </div>
      {isLoading ? <p style={{ color: "#64748B" }}>Loading…</p> : items.length === 0 ? <p style={{ fontSize: 13, color: "#64748B" }}>{status === "NEW" ? "Nothing waiting to be reviewed." : "No items match."}{sources.length === 0 ? " Add a source to start receiving regulatory updates." : ""}</p> : items.map(i => (
        <div key={i.id} style={{ background: "#fff", border: "1px solid #E2E8F0", borderRadius: 10, padding: "12px 14px", marginBottom: 8 }}>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontSize: 14, fontWeight: 600, color: "#0F172A", flex: 1, minWidth: 240 }}>{i.title}</span>
            {i.status !== "NEW" && <Badge text={statusLabel[i.status]} color={i.status === "RELEVANT" ? "#3730A3" : "#475569"} bg={i.status === "RELEVANT" ? "#E0E7FF" : "#E2E8F0"} />}
          </div>
          <div style={{ fontSize: 12, color: "#64748B", margin: "2px 0 6px" }}>{i.sourceName} · {i.publishedOn ? fmtDate(i.publishedOn) : `fetched ${fmtDate(i.fetchedAt)}`}{i.link && <> · <a href={i.link} target="_blank" rel="noopener noreferrer" style={{ color: "#4338CA" }}>Open original <ExternalLink size={11} style={{ verticalAlign: -1 }} /></a></>}</div>
          {i.summary && <div style={{ fontSize: 13, color: "#334155", marginBottom: 6 }}>{i.summary}</div>}
          {i.status !== "NEW" && <div style={{ fontSize: 12, color: "#64748B" }}>{statusLabel[i.status]} by {i.decidedByName ?? "—"} on {fmtDateTime(i.decidedAt)}{i.decisionNote ? `: ${i.decisionNote}` : ""}</div>}
          <div style={{ display: "flex", gap: 8, marginTop: 6 }}>
            <button style={ghostBtn} onClick={() => setOpen(open === i.id ? null : i.id)}>{open === i.id ? "Hide suggestions" : "What might this affect?"}</button>
            {canManage && i.status === "NEW" && <button style={primaryBtn} onClick={() => setDeciding(i)}>Triage</button>}
            {canManage && i.status !== "NEW" && <button style={ghostBtn} onClick={() => setReopening(i)}>Reopen</button>}
          </div>
          {open === i.id && <Suggestions rows={sug.data} loading={sug.isLoading} error={sug.isError ? errMsg(sug.error, "Could not check the registers") : ""} />}
        </div>
      ))}
      {reopening && <ReopenModal item={reopening} onClose={() => setReopening(null)} onSaved={() => { setReopening(null); refresh() }} />}
      {deciding && <DecisionModal item={deciding} onClose={() => setDeciding(null)} onSaved={() => { setDeciding(null); refresh() }} />}
    </div>
  )
}

function SourcesPanel({ canManage, sources }: { canManage: boolean; sources: Source[] }) {
  const qc = useQueryClient()
  const [adding, setAdding] = useState(false)
  const [notice, setNotice] = useState("")
  const [apiError, setApiError] = useState("")
  const refresh = () => qc.invalidateQueries({ queryKey: ["lc-mon"] })
  const fetchNow = useMutation({
    mutationFn: async (id: string) => one(await apiClient.post(`${base}/sources/${id}/fetch`)) as FetchOutcome,
    onSuccess: (o) => { setApiError(""); setNotice(o.failed ? "" : `Fetched ${o.fetched} item(s); ${o.added} new.`); if (o.failed) setApiError(o.error ?? "The fetch failed"); refresh() },
    onError: (e: unknown) => { setNotice(""); setApiError(errMsg(e, "Could not fetch")) },
  })
  const toggle = useMutation({
    mutationFn: (s: Source) => apiClient.put(`${base}/sources/${s.id}`, { name: s.name, authority: s.authority, active: !s.active }), onSuccess: () => { setApiError(""); refresh() },
    onError: (e: unknown) => setApiError(errMsg(e, "Could not update the source")),
  })
  return (
    <div>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <div style={{ fontSize: 12, color: "#64748B" }}>Each active source is read once a day (about 06:40 South African time).</div>
        {canManage && <button style={primaryBtn} onClick={() => setAdding(true)}><Plus size={14} style={{ verticalAlign: -2 }} /> Add source</button>}
      </div>
      {notice && <div style={{ background: "#ECFDF5", border: "1px solid #A7F3D0", color: "#065F46", borderRadius: 8, padding: "8px 12px", fontSize: 13, marginBottom: 12 }}>{notice}</div>}
      <Err m={apiError} />
      {sources.length === 0 ? <p style={{ fontSize: 13, color: "#64748B" }}>No sources yet.</p> : (
        <div style={{ overflowX: "auto" }}>
          <table style={{ borderCollapse: "collapse", fontSize: 13, width: "100%" }}>
            <thead><tr>{["Source", "Status", "Last fetched", ""].map(h => <th key={h} style={{ textAlign: "left", padding: "6px 8px", borderBottom: "1px solid #E2E8F0", fontWeight: 600 }}>{h}</th>)}</tr></thead>
            <tbody>{sources.map(s => (
              <tr key={s.id}>
                <td style={{ padding: "8px", borderBottom: "1px solid #F1F5F9" }}><div style={{ fontWeight: 600 }}>{s.name}</div><div style={{ fontSize: 11, color: "#64748B" }}>{s.authority ? `${s.authority} · ` : ""}{s.feedUrl}</div></td>
                <td style={{ padding: "8px", borderBottom: "1px solid #F1F5F9" }}>
                  {!s.active ? <Badge text="Paused" color="#475569" bg="#E2E8F0" /> : s.lastStatus === "FAILED" ? <Badge text={s.needsAttention ? "Failing" : "Last fetch failed"} color="#991B1B" bg="#FEE2E2" /> : s.lastStatus === "OK" ? <Badge text="Working" color="#166534" bg="#DCFCE7" /> : <Badge text="Not fetched yet" color="#475569" bg="#E2E8F0" />}
                  {s.lastStatus === "FAILED" && <div style={{ fontSize: 11, color: "#991B1B", marginTop: 2 }}>{s.lastError} ({s.consecutiveFailures} in a row)</div>}
                  {s.lastStatus === "OK" && <div style={{ fontSize: 11, color: "#64748B", marginTop: 2 }}>{s.lastNewItems} new last time</div>}
                </td>
                <td style={{ padding: "8px", borderBottom: "1px solid #F1F5F9", color: "#64748B" }}>{s.lastFetchedAt ? fmtDateTime(s.lastFetchedAt) : "—"}</td>
                <td style={{ padding: "8px", borderBottom: "1px solid #F1F5F9", textAlign: "right", whiteSpace: "nowrap" }}>
                  {canManage && <><button style={ghostBtn} disabled={fetchNow.isPending} onClick={() => { setNotice(""); fetchNow.mutate(s.id) }}><RefreshCw size={13} style={{ verticalAlign: -2 }} /> Fetch now</button>{" "}<button style={ghostBtn} disabled={toggle.isPending} onClick={() => toggle.mutate(s)}>{s.active ? "Pause" : "Resume"}</button></>}
                </td>
              </tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {adding && <SourceModal onClose={() => setAdding(false)} onSaved={() => { setAdding(false); refresh() }} />}
    </div>
  )
}

function AssistPanel() {
  const [text, setText] = useState("")
  const run = useMutation({ mutationFn: async () => unwrap(await apiClient.post(`${base}/assist`, { text })) as Suggestion[] })
  return (
    <div>
      <p style={{ fontSize: 13, color: "#475569", marginTop: 0, maxWidth: 720 }}>Paste the text of a proposed amendment, a notice or a summary. HandyFlow lists the obligations, controls and policies in your registers that share its wording, so you know where to start an impact assessment. The pasted text is not stored.</p>
      <textarea style={{ ...input, minHeight: 140, width: "100%", boxSizing: "border-box" }} maxLength={20000} value={text} onChange={e => setText(e.target.value)} aria-label="Text to check" />
      <div style={{ margin: "8px 0" }}><button style={primaryBtn} disabled={run.isPending || text.trim().length === 0} onClick={() => run.mutate()}>Find possibly affected items</button></div>
      <Suggestions rows={run.data} loading={run.isPending} error={run.isError ? errMsg(run.error, "Could not check the registers") : ""} />
    </div>
  )
}

export default function MonitoringTab() {
  const hasMonitor = usePermission("LEGALCOMPLIANCE_MONITOR_MANAGE")
  const hasAdmin = usePermission("LEGALCOMPLIANCE_ADMIN")
  const canManage = hasMonitor || hasAdmin
  const [panel, setPanel] = useState<"inbox" | "sources" | "assist">("inbox")
  const { data: summary } = useQuery<Summary>({ queryKey: ["lc-mon", "summary"], queryFn: async () => one(await apiClient.get(`${base}/summary`)) })
  const { data: sources = [] } = useQuery<Source[]>({ queryKey: ["lc-mon", "sources"], queryFn: async () => unwrap(await apiClient.get(`${base}/sources`)) })

  const tab = (id: typeof panel, label: string) => <button key={id} onClick={() => setPanel(id)} aria-pressed={panel === id} style={{ ...(panel === id ? primaryBtn : ghostBtn) }}>{label}</button>
  return (
    <div>
      <div style={{ display: "flex", gap: 10, flexWrap: "wrap", marginBottom: 14 }}>
        {[["To review", summary?.newItems], ["Active sources", summary?.activeSources], ["Sources failing", summary?.sourcesFailing]].map(([l, v]) => (
          <div key={String(l)} style={{ background: "#F8FAFC", border: "1px solid #E2E8F0", borderRadius: 10, padding: "8px 14px" }}><div style={{ fontSize: 11, color: "#64748B" }}>{l}</div><div style={{ fontSize: 18, fontWeight: 700, color: "#0F172A" }}>{v ?? "—"}</div></div>
        ))}
      </div>
      <div style={{ display: "flex", gap: 8, marginBottom: 14 }}>{tab("inbox", "Inbox")}{tab("sources", "Sources")}{tab("assist", "Check a text")}</div>
      {panel === "inbox" && <InboxPanel canManage={canManage} sources={sources} />}
      {panel === "sources" && <SourcesPanel canManage={canManage} sources={sources} />}
      {panel === "assist" && <AssistPanel />}
    </div>
  )
}
