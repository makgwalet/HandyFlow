// Working out deadlines. Choose a rule and the trigger date, see the date and exactly which days were skipped, then add it to
// the diary as a key date. The list underneath is the record of how each computed date was reached, with corrections.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { toast } from "../../store/toast.store"
import { Btn, Dialog, ErrorLine, Input, Pick, Pill, card, day, errorText, muted, td, th } from "./kit"
import { BASE } from "./lookups"
import { unwrap, useRules } from "./deadlineApi"
import { correctionProblem, describeRule, previewMatches, triggerProblem, type DeadlineComputation, type DeadlinePreview, type DeadlineRule } from "./legal.logic"

const weekday = (iso: string) => new Date(iso + "T00:00:00").toLocaleDateString("en-ZA", { weekday: "long" })

/** The worked-out date and the reason for it. Shared by the calculator and the dialog on a matter. */
export function PreviewCard({ preview, rule }: { preview: DeadlinePreview; rule?: DeadlineRule }) {
  return (
    <div style={{ ...card, marginTop: 10, background: "var(--hf-surface-alt, var(--hf-surface))" }}>
      <div style={muted}>{preview.ruleName}: {preview.triggerLabel} {day(preview.triggerDate)}</div>
      <div style={{ fontSize: 22, fontWeight: 700, margin: "4px 0" }}>{preview.dueDayOfWeek}, {day(preview.dueDate)}</div>
      {rule && <div style={muted}>{describeRule(rule)}</div>}
      {preview.explanation.length === 0
        ? <div style={{ ...muted, marginTop: 6 }}>No days had to be skipped.</div>
        : <div style={{ marginTop: 6 }}><div style={{ fontSize: 12, fontWeight: 600 }}>Days not counted or skipped</div>{preview.explanation.map((l, i) => <div key={i} style={{ fontSize: 13 }}>{l}</div>)}</div>}
      <div style={{ ...muted, marginTop: 8 }}>A date worked out here is only as good as the rule and the trigger date. Check both before relying on it.</div>
    </div>
  )
}

/** Pick a rule and a trigger date; with a matter, the date can then be added to its diary. */
export function ComputeDialog({ matterId, onClose, onAdded }: { matterId?: string; onClose: () => void; onAdded?: () => void }) {
  const qc = useQueryClient()
  const rules = useRules(false)
  const [ruleId, setRuleId] = useState(""), [trigger, setTrigger] = useState(""), [description, setDescription] = useState(""), [notes, setNotes] = useState("")
  const [preview, setPreview] = useState<DeadlinePreview | null>(null), [err, setErr] = useState("")
  const rule = (rules.data ?? []).find(r => r.id === ruleId)
  const problem = triggerProblem(ruleId, trigger)
  const seen = previewMatches(preview, ruleId, trigger)
  const check = useMutation({
    mutationFn: async () => unwrap<DeadlinePreview>(await apiClient.post(`${BASE}/deadlines/preview`, { ruleId, triggerDate: trigger })),
    onSuccess: p => { setPreview(p); setErr("") },
    onError: async e => setErr(await errorText(e, "Could not work out the date")),
  })
  const add = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/deadlines/matters/${matterId}/compute`, { ruleId, triggerDate: trigger, description: description.trim() || null, notes: notes.trim() || null }),
    onSuccess: () => { toast.success("Deadline added to the diary"); qc.invalidateQueries({ queryKey: ["lp-keydates"] }); qc.invalidateQueries({ queryKey: ["lp-deadlines"] }); onAdded?.(); onClose() },
    onError: async e => setErr(await errorText(e, "Could not add the deadline")),
  })
  return (
    <Dialog title={matterId ? "Work out a deadline" : "Deadline calculator"} onClose={onClose} width={640}
      footer={<>
        <Btn onClick={onClose}>{matterId ? "Cancel" : "Close"}</Btn>
        <Btn disabled={!!problem || check.isPending} onClick={() => { setErr(""); check.mutate() }}>{check.isPending ? "Working it out…" : "Work out the date"}</Btn>
        {matterId && <Btn kind="primary" disabled={!seen || add.isPending} onClick={() => { setErr(""); add.mutate() }}>{add.isPending ? "Adding…" : "Add to diary"}</Btn>}
      </>}>
      {rules.isError ? <ErrorLine text="Could not load the rules." /> : (rules.data ?? []).length === 0 && !rules.isLoading ? <div style={muted}>There are no deadline rules yet. Someone who can maintain the rules adds them (or installs the template rules) on the Deadlines screen.</div> : null}
      <Pick label="Rule" value={ruleId} onChange={v => { setRuleId(v); setErr("") }} options={(rules.data ?? []).map(r => [r.id, r.name] as const)} blank="Choose a rule" />
      {rule && <div style={{ ...muted, marginTop: -6, marginBottom: 10 }}>{describeRule(rule)}{rule.sourceReference ? ` · ${rule.sourceReference}` : ""}</div>}
      <Input label={rule?.triggerLabel ?? "Trigger date"} type="date" value={trigger} onChange={v => { setTrigger(v); setErr("") }} hint="The day the period starts from. It is not counted." />
      {matterId && <>
        <Input label="What is due (optional)" value={description} onChange={setDescription} hint={rule ? `Shown in the diary. Leave blank for "${rule.name}".` : undefined} />
        <Input label="Notes (optional)" value={notes} onChange={setNotes} />
      </>}
      {problem && (ruleId || trigger) && <div style={muted}>{problem}</div>}
      {preview && <>
        {!seen && <div style={{ ...muted, marginTop: 8 }}>You changed the rule or the date. Work out the date again{matterId ? " before adding it" : ""}.</div>}
        <div style={seen ? undefined : { opacity: 0.5 }}><PreviewCard preview={preview} rule={(rules.data ?? []).find(r => r.id === preview.ruleId)} /></div>
      </>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}

/** The record of computed deadlines on one matter, with the option to correct the current one. */
export function DeadlinePanel({ matterId, canCompute }: { matterId: string; canCompute: boolean }) {
  const [correcting, setCorrecting] = useState<DeadlineComputation | null>(null)
  const list = useQuery({ queryKey: ["lp-deadlines", matterId], queryFn: async () => unwrap<DeadlineComputation[]>(await apiClient.get(`${BASE}/deadlines/matters/${matterId}`)) ?? [] })
  const rows = list.data ?? []
  if (list.isError) return <div style={{ ...muted, marginTop: 12 }}>Could not load how the deadlines were worked out.</div>
  if (rows.length === 0) return null
  return (
    <div style={{ marginTop: 16 }}>
      <div style={{ fontWeight: 700, marginBottom: 6 }}>How the deadlines were worked out</div>
      <div style={{ ...card, padding: 0, overflowX: "auto" }}>
        <table style={{ width: "100%", borderCollapse: "collapse" }}>
          <thead><tr><th style={th}>Due</th><th style={th}>Rule</th><th style={th}>Worked out</th><th style={th} /></tr></thead>
          <tbody>{rows.map(c => (
            <tr key={c.id} style={c.current ? undefined : { opacity: 0.65 }}>
              <td style={td}><b>{day(c.dueDate)}</b>{!c.current && <div style={{ marginTop: 3 }}><Pill tone="neutral">Replaced</Pill></div>}
                {c.drifted && <div style={{ marginTop: 3 }}><Pill tone="warning">Diary now says {c.keyDateDueDate ? day(c.keyDateDueDate) : "something else"}</Pill></div>}</td>
              <td style={td}>{c.ruleName}<div style={muted}>{describeRule(c)}</div><div style={muted}>{c.triggerLabel}: {day(c.triggerDate)} ({weekday(c.triggerDate)})</div>
                <details><summary style={{ ...muted, cursor: "pointer" }}>{c.explanation.length === 0 ? "No days skipped" : `${c.explanation.length} ${c.explanation.length === 1 ? "line" : "lines"} of reasoning`}</summary>
                  {c.explanation.map((l, i) => <div key={i} style={{ fontSize: 12 }}>{l}</div>)}</details></td>
              <td style={td}>{c.computedByName ?? ""}<div style={muted}>{day(c.computedAt)}</div>{c.reason && <div style={muted}>Reason: {c.reason}</div>}</td>
              <td style={{ ...td, textAlign: "right" }}>{canCompute && c.current && c.keyDateStatus === "PENDING" && <Btn small onClick={() => setCorrecting(c)}>Correct</Btn>}</td>
            </tr>))}</tbody>
        </table>
      </div>
      {correcting && <CorrectDialog computation={correcting} onClose={() => setCorrecting(null)} />}
    </div>
  )
}

function CorrectDialog({ computation: c, onClose }: { computation: DeadlineComputation; onClose: () => void }) {
  const qc = useQueryClient()
  const [trigger, setTrigger] = useState(c.triggerDate), [reason, setReason] = useState(""), [current, setCurrent] = useState("no"), [err, setErr] = useState("")
  const problem = correctionProblem(trigger, reason)
  const save = useMutation({
    mutationFn: () => apiClient.post(`${BASE}/deadlines/computations/${c.id}/recompute`, { triggerDate: trigger, reason: reason.trim(), useCurrentRule: current === "yes" }),
    onSuccess: () => { toast.success("Deadline corrected"); qc.invalidateQueries({ queryKey: ["lp-keydates"] }); qc.invalidateQueries({ queryKey: ["lp-deadlines"] }); onClose() },
    onError: async e => setErr(await errorText(e, "Could not correct the deadline")),
  })
  return (
    <Dialog title={`Correct: ${c.ruleName}`} onClose={onClose}
      footer={<><Btn onClick={onClose}>Cancel</Btn><Btn kind="primary" disabled={!!problem || save.isPending} onClick={() => { setErr(""); save.mutate() }}>{save.isPending ? "Saving…" : "Correct the date"}</Btn></>}>
      <div style={muted}>Currently {day(c.dueDate)}, from {c.triggerLabel.toLowerCase()} {day(c.triggerDate)}. The diary entry moves; this computation is kept and marked as replaced.</div>
      <div style={{ marginTop: 10 }}><Input label={c.triggerLabel} type="date" value={trigger} onChange={setTrigger} /></div>
      <Pick label="Which rule" value={current} onChange={setCurrent} options={[["no", "The rule as it was when first worked out"], ["yes", "The rule as it is now"]]} />
      <Input label="Why is it being corrected" value={reason} onChange={setReason} hint="At least 10 characters. Kept permanently." />
      {problem && reason.trim() && <div style={muted}>{problem}</div>}
      {err && <ErrorLine text={err} />}
    </Dialog>
  )
}
