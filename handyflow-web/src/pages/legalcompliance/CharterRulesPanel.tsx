// src/pages/legalcompliance/CharterRulesPanel.tsx
//
// A committee's charter rules (chunk 29 API). Endpoints under /api/v1/legalcompliance/committees/{id}/charter:
//   GET  → rules in force, plain-language summary, allowed written thresholds, full history
//   POST → record a new version (LEGALCOMPLIANCE_CHARTER_MANAGE or LEGALCOMPLIANCE_ADMIN)
// No charter recorded = the earlier house rules apply (the closer's casting-vote tick-box, 75% for special resolutions, either written threshold, quorum checked after recusal).
// Once rules are recorded the tick-box is ignored. History is append-only: a change is a new version with a reason. Votes already closed keep their outcome.
import { useState } from "react"
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { input, primaryBtn, ghostBtn, one, fmtDate, errMsg } from "./lcHelpers"
import { Err, Badge, Field } from "./LcUi"
import { WRITTEN_CHOICES, charterKey, startForm, formProblem, sameAsCurrent, changes, type CharterForm, type CharterRulesView, type WrittenMode } from "./charterLogic"

interface Version extends CharterRulesView {
  id: string; versionNo: number; charterReference: string | null; adoptedOn: string; effectiveFrom: string; reason: string; recordedBy: string | null; inForce: boolean
}
export interface CharterData {
  committeeId: string; recorded: boolean; currentVersion: number | null; castingVote: boolean; writtenMode: WrittenMode; quorumAfterRecusal: boolean; specialPercent: number
  allowedWrittenThresholds: string[]; summary: string[]; history: Version[]
}

const small = { ...ghostBtn, padding: "4px 10px", fontSize: 12 }
const today = () => new Date().toISOString().slice(0, 10)

/** Shown inside an expanded committee. canRecord = may record a new version; live = the committee is not dissolved. */
export function CharterRulesPanel({ committeeId, canRecord, live }: { committeeId: string; canRecord: boolean; live: boolean }) {
  const qc = useQueryClient()
  const base = `/api/v1/legalcompliance/committees/${committeeId}/charter`
  const { data } = useQuery<CharterData>({ queryKey: charterKey(committeeId), queryFn: async () => one(await apiClient.get(base)) })
  const [form, setForm] = useState<CharterForm | null>(null)
  const [showHistory, setShowHistory] = useState(false)
  const [err, setErr] = useState("")
  const save = useMutation({
    mutationFn: (f: CharterForm) => apiClient.post(base, {
      charterReference: f.reference.trim() || null, adoptedOn: f.adoptedOn, effectiveFrom: f.effectiveFrom, castingVote: f.castingVote, writtenMode: f.writtenMode,
      quorumAfterRecusal: f.quorumAfterRecusal, specialPercent: Number(f.percent), reason: f.reason.trim(),
    }),
    onSuccess: () => { qc.invalidateQueries({ queryKey: charterKey(committeeId) }); setForm(null); setErr("") },
    onError: (e: unknown) => setErr(errMsg(e, "The charter rules were not saved.")),
  })
  if (!data) return <p style={{ fontSize: 12, color: "#64748B" }}>Loading charter rules…</p>

  const current: CharterRulesView | null = data.recorded ? data : null
  const history = [...data.history].sort((a, b) => b.versionNo - a.versionNo)
  const waiting = history.filter(v => !v.inForce && v.versionNo > (data.currentVersion ?? 0))
  const problem = form ? formProblem(form) ?? (sameAsCurrent(form, current) && waiting.length === 0 ? "These are the rules already in force: nothing to record" : null) : null

  return (
    <div style={{ margin: "8px 0", padding: 10, background: "#F8FAFC", borderRadius: 8 }}>
      <div style={{ display: "flex", gap: 8, alignItems: "center", marginBottom: 4 }}>
        <span style={{ fontWeight: 700, fontSize: 13 }}>Charter rules</span>
        {data.recorded ? <Badge text={`Version ${data.currentVersion} in force`} color="#3730A3" bg="#E0E7FF" /> : <Badge text="Not recorded" color="#475569" bg="#E2E8F0" />}
      </div>
      {data.summary.map((s, i) => <div key={i} style={{ fontSize: 12, color: "#475569", padding: "1px 0" }}>• {s}</div>)}
      {!data.recorded && <p style={{ fontSize: 12, color: "#64748B", margin: "4px 0 0" }}>Until rules are recorded, voting uses the house rules above, and the closer ticks "charter permits" for a casting vote.</p>}
      {data.recorded && <p style={{ fontSize: 12, color: "#64748B", margin: "4px 0 0" }}>The casting-vote tick-box is ignored for this committee: the charter decides.</p>}
      {waiting.map(v => <p key={v.id} style={{ fontSize: 12, color: "#92400E", margin: "4px 0 0" }}>Version {v.versionNo} takes effect on {fmtDate(v.effectiveFrom)}: {changes(current, v).join("; ") || "no change in the rules"}.</p>)}

      <div style={{ display: "flex", gap: 6, marginTop: 6 }}>
        {canRecord && live && !form && <button style={small} onClick={() => { setErr(""); setForm(startForm(current, today())) }}>{data.recorded ? "Record a change" : "Record charter rules"}</button>}
        {history.length > 0 && <button style={small} onClick={() => setShowHistory(!showHistory)}>{showHistory ? "Hide history" : `History (${history.length})`}</button>}
      </div>

      {form && <CharterRecordForm form={form} setForm={setForm} problem={problem} pending={save.isPending} err={err} onCancel={() => setForm(null)} onSave={() => save.mutate(form)} />}

      {showHistory && history.map((v, i) => {
        const prev = history[i + 1] ?? null
        const diff = changes(prev, v)
        return (
          <div key={v.id} style={{ fontSize: 12, padding: "6px 0", borderTop: "1px solid #E2E8F0", marginTop: i === 0 ? 6 : 0 }}>
            <b>Version {v.versionNo}</b> · from {fmtDate(v.effectiveFrom)}{v.charterReference ? ` · ${v.charterReference}` : ""} · adopted {fmtDate(v.adoptedOn)}{v.inForce ? " · in force" : v.versionNo > (data.currentVersion ?? 0) ? " · not yet in force" : " · replaced"}
            <div style={{ color: "#475569" }}>{diff.length ? diff.join("; ") : prev ? "No change in the rules" : "First version: matches the house rules"}</div>
            <div style={{ color: "#64748B" }}>Reason: {v.reason}{v.recordedBy ? ` (${v.recordedBy})` : ""}</div>
          </div>
        )
      })}
    </div>
  )
}

export function CharterRecordForm({ form, setForm, problem, pending, err, onCancel, onSave }: {
  form: CharterForm; setForm: (f: CharterForm) => void; problem: string | null; pending: boolean; err: string; onCancel: () => void; onSave: () => void
}) {
  const set = (o: Partial<CharterForm>) => setForm({ ...form, ...o })
  return (
    <div style={{ marginTop: 8, padding: 10, background: "#fff", border: "1px solid #E2E8F0", borderRadius: 8 }}>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 8 }}>
        <Field l="Adopted on *"><input type="date" style={input} value={form.adoptedOn} onChange={e => set({ adoptedOn: e.target.value })} /></Field>
        <Field l="Rules take effect on *"><input type="date" style={input} value={form.effectiveFrom} onChange={e => set({ effectiveFrom: e.target.value })} /></Field>
      </div>
      <Field l="Charter reference"><input style={input} maxLength={120} placeholder="e.g. Board charter, 2026 edition" value={form.reference} onChange={e => set({ reference: e.target.value })} /></Field>
      <label style={{ fontSize: 13, display: "flex", gap: 6, margin: "4px 0", cursor: "pointer" }}><input type="checkbox" checked={form.castingVote} onChange={e => set({ castingVote: e.target.checked })} />The chair has a casting vote on a tied ordinary resolution</label>
      <Field l="Written resolutions">
        <select style={input} value={form.writtenMode} onChange={e => set({ writtenMode: e.target.value as WrittenMode })}>{WRITTEN_CHOICES.map(c => <option key={c.value} value={c.value}>{c.label}</option>)}</select>
      </Field>
      <label style={{ fontSize: 13, display: "flex", gap: 6, margin: "4px 0", cursor: "pointer" }}><input type="checkbox" checked={form.quorumAfterRecusal} onChange={e => set({ quorumAfterRecusal: e.target.checked })} />Voting needs a quorum even after members recuse themselves</label>
      <Field l="Special resolution needs (% of votes cast, 51 to 100) *"><input style={{ ...input, maxWidth: 120 }} inputMode="numeric" value={form.percent} onChange={e => set({ percent: e.target.value })} /></Field>
      <Field l="Reason for recording this *"><input style={input} maxLength={1000} placeholder="At least 10 characters" value={form.reason} onChange={e => set({ reason: e.target.value })} /></Field>
      {problem && <p style={{ fontSize: 12, color: "#B45309", margin: "0 0 6px" }}>{problem}</p>}
      <Err m={err} />
      <p style={{ fontSize: 11, color: "#64748B", margin: "0 0 6px" }}>This is kept as a new version and cannot be edited afterwards. Votes already closed keep their outcome.</p>
      <div style={{ display: "flex", gap: 6 }}><button style={small} onClick={onCancel}>Cancel</button><button style={{ ...primaryBtn, padding: "4px 10px", fontSize: 12 }} disabled={!!problem || pending} onClick={onSave}>Save rules</button></div>
    </div>
  )
}
