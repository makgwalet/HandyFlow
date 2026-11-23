// src/pages/security/WorkingTimePolicyPanel.tsx
//
// Working-time limits per employee category, each starting on a date. Policies are never edited: a change is a new policy with a later date.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { useAuthStore } from "../../store/auth.store"
import {
  BASIS_MAX, DEFAULT_CATEGORY, categoriesOf, ceilingOf, dateText, draftError, emptyDraft, errorText, exceedsGeneral, firstFullWeek, statusOf, summaryOf, toRequest, validPolicies,
  type Draft, type Policy,
} from "./workingTimeLogic"
import { todaySast } from "./weeklyHoursLogic"
import { useCompanyZone } from "./companyZone"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const input: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0, width: "100%", boxSizing: "border-box" }
const label: React.CSSProperties = { display: "flex", flexDirection: "column", gap: 3, fontSize: 12, color: "var(--hf-text-muted)" }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }

const FIELDS: { key: keyof Draft; text: string }[] = [
  { key: "warnAt", text: "Warn from (h/week)" }, { key: "ordinaryWeekly", text: "Ordinary hours per week" }, { key: "maxOvertimeWeekly", text: "Overtime per week" },
  { key: "maxDaily", text: "Hours per day" }, { key: "maxDailyOvertime", text: "Overtime per day" }, { key: "minRestHours", text: "Rest between shifts (h)" },
]

export default function WorkingTimePolicyPanel() {
  const qc = useQueryClient()
  const canManage = useAuthStore(s => s.hasPermission("SECURITY_MANAGE"))
  useCompanyZone()
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState<Draft>(() => emptyDraft())
  const [note, setNote] = useState<{ ok: boolean; text: string } | null>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ["working-time-policies"],
    queryFn: async () => (await apiClient.get("/api/v1/security/working-time-policies")).data?.data,
  })
  const list: Policy[] | null = validPolicies(data)
  const today = todaySast()

  const save = useMutation({
    mutationFn: async () => { await apiClient.post("/api/v1/security/working-time-policies", toRequest(draft)) },
    onSuccess: () => { setOpen(false); setDraft(emptyDraft()); setNote({ ok: true, text: "Policy added. It applies from the first full week on or after its start date." }); qc.invalidateQueries({ queryKey: ["working-time-policies"] }); qc.invalidateQueries({ queryKey: ["weekly-hours"] }) },
    onError: e => setNote({ ok: false, text: errorText(e, "The policy could not be saved.") }),
  })
  const problem = draftError(draft, today)
  const set = (k: keyof Draft, v: string) => setDraft(d => ({ ...d, [k]: v }))
  const ceiling = ceilingOf(draft)

  return (
    <section aria-label="Working-time policies" style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
        <div style={{ fontWeight: 700 }}>Working-time policies</div>
        {canManage && !open && <button type="button" style={btn} onClick={() => { setNote(null); setOpen(true) }}>Add policy</button>}
      </div>
      <div style={{ ...muted, marginBottom: 8 }}>Limits per employee category. Guards with no category use the Default policy; with none set, the system settings apply. A policy cannot be edited: add one with a later start date.</div>

      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data !== undefined && list === null)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>Working-time policies could not be loaded.</div>}

      {list && list.length === 0 && <div style={muted}>No policy has been added. The system settings apply to every guard.</div>}
      {list && categoriesOf(list).filter(c => list.some(p => p.category === c)).map(c => (
        <div key={c} style={{ marginBottom: 10 }}>
          <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4 }}>{c === DEFAULT_CATEGORY ? "Default (all guards)" : c}</div>
          {list.filter(p => p.category === c).map(p => {
            const st = statusOf(p, list, today)
            return (
              <div key={p.id ?? `${p.category}-${p.effectiveFrom}`} style={{ fontSize: 13, padding: "4px 0", borderTop: "1px solid var(--hf-border-subtle)", opacity: st === "Replaced" ? 0.65 : 1 }}>
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                  <span>From {dateText(p.effectiveFrom)}</span>
                  <Chip tone={st === "In force" ? "ok" : st === "Starts later" ? "warn" : "neutral"}>{st}</Chip>
                </div>
                <div style={{ overflowWrap: "anywhere" }}>{summaryOf(p)}</div>
                {p.legalBasis && <div style={muted}>Legal basis: {p.legalBasis}</div>}
              </div>
            )
          })}
        </div>
      ))}

      {canManage && open && (
        <form aria-label="New working-time policy" style={{ marginTop: 8 }} onSubmit={e => { e.preventDefault(); if (!problem && !save.isPending) { setNote(null); save.mutate() } }}>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(150px, 1fr))", gap: 8 }}>
            <label style={label}>Category<input aria-label="Category" list="wt-categories" maxLength={40} value={draft.category} onChange={e => set("category", e.target.value)} style={input} /></label>
            <datalist id="wt-categories">{categoriesOf(list ?? []).map(c => <option key={c} value={c} />)}</datalist>
            <label style={label}>Starts on<input aria-label="Starts on" type="date" min={today} value={draft.effectiveFrom} onChange={e => set("effectiveFrom", e.target.value)} style={input} /></label>
            {FIELDS.map(f => (
              <label key={f.key} style={label}>{f.text}<input aria-label={f.text} inputMode="decimal" value={draft[f.key]} onChange={e => set(f.key, e.target.value)} style={input} /></label>
            ))}
          </div>
          <div style={{ ...muted, marginTop: 6 }}>
            {ceiling !== null && <>Combined weekly ceiling: {ceiling} h (ordinary + overtime). </>}
            {/^\d{4}-\d{2}-\d{2}$/.test(draft.effectiveFrom) && draft.effectiveFrom >= today && <>First applies to the week starting {dateText(firstFullWeek(draft.effectiveFrom))}.</>}
          </div>
          <label style={{ ...label, marginTop: 8 }}>
            Legal basis{exceedsGeneral(draft) ? " (required: beyond the general BCEA figures)" : " (optional)"}
            <textarea aria-label="Legal basis" rows={2} maxLength={BASIS_MAX} value={draft.legalBasis} onChange={e => set("legalBasis", e.target.value)} style={{ ...input, resize: "vertical" }} />
          </label>
          {problem && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}
          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            <button type="submit" style={{ ...btn, opacity: problem ? 0.5 : 1 }} disabled={problem !== null || save.isPending}>Save policy</button>
            <button type="button" style={btn} disabled={save.isPending} onClick={() => { setOpen(false); setNote(null) }}>Cancel</button>
          </div>
        </form>
      )}

      {note && <div role={note.ok ? "status" : "alert"} style={{ marginTop: 8, fontSize: 13, color: note.ok ? "var(--hf-text)" : "var(--hf-danger-text)" }}>{note.text}</div>}
    </section>
  )
}
