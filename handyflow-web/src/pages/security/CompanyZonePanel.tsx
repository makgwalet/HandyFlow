// src/pages/security/CompanyZonePanel.tsx
//
// The time zone this company's days, weeks and months are decided in. A change is previewed, then confirmed.
import { useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { apiClient } from "../../api/client"
import { useAuthStore } from "../../store/auth.store"
import { COMMON_ZONES, errorText, shiftText, validEffect, validView, zoneProblem, type ZoneEffect } from "./companyZoneLogic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16, marginBottom: 16 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const input: React.CSSProperties = { padding: "6px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text)", fontSize: 13, minWidth: 0, width: "100%", boxSizing: "border-box" }
const btn: React.CSSProperties = { padding: "6px 12px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", fontSize: 13 }

export default function CompanyZonePanel() {
  const qc = useQueryClient()
  const canManage = useAuthStore(s => s.hasPermission("SECURITY_MANAGE"))
  const [open, setOpen] = useState(false)
  const [wanted, setWanted] = useState("")
  const [effect, setEffect] = useState<ZoneEffect | null>(null)
  const [read, setRead] = useState(false)
  const [note, setNote] = useState<{ ok: boolean; text: string } | null>(null)

  const { data, isLoading, error } = useQuery({
    queryKey: ["company-time-zone"],
    queryFn: async () => (await apiClient.get("/api/v1/security/company-time-zone")).data?.data,
  })
  const view = validView(data)
  const problem = zoneProblem(wanted, view?.timeZone ?? null)

  const close = () => { setOpen(false); setWanted(""); setEffect(null); setRead(false) }
  const preview = useMutation({
    mutationFn: async () => validEffect((await apiClient.get("/api/v1/security/company-time-zone/preview", { params: { timeZone: wanted.trim() } })).data?.data),
    onSuccess: e => { if (e) { setEffect(e); setRead(false) } else setNote({ ok: false, text: "The effect of the change could not be shown. Nothing was changed." }) },
    onError: e => setNote({ ok: false, text: errorText(e, "The effect of the change could not be shown.") }),
  })
  const change = useMutation({
    mutationFn: async () => { await apiClient.put("/api/v1/security/company-time-zone", { timeZone: wanted.trim(), confirm: true }) },
    onSuccess: () => {
      const to = wanted.trim()
      close()
      setNote({ ok: true, text: `The company is now on ${to}. Other signed-in users may see the old zone for up to 5 minutes.` })
      for (const k of ["company-time-zone", "weekly-hours", "working-time-policies", "coverage"]) qc.invalidateQueries({ queryKey: [k] })
    },
    onError: e => setNote({ ok: false, text: errorText(e, "The time zone could not be changed.") }),
  })
  const busy = preview.isPending || change.isPending

  return (
    <section aria-label="Company time zone" style={card}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 8 }}>
        <div style={{ fontWeight: 700 }}>Company time zone</div>
        {canManage && view && !open && <button type="button" style={btn} onClick={() => { setNote(null); setOpen(true) }}>Change</button>}
      </div>
      <div style={{ ...muted, marginBottom: 8 }}>Days, weeks and months in hours, coverage, reports and payroll start and end at midnight in this zone. Times are stored in UTC and are not changed.</div>

      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data !== undefined && view === null)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The company time zone could not be loaded.</div>}
      {view && <div style={{ fontSize: 14 }}><strong>{view.timeZone}</strong>{view.abbreviation ? ` (${view.abbreviation})` : ""} <span style={muted}>· now {view.now}</span></div>}

      {canManage && open && view && (
        <form aria-label="Change time zone" style={{ marginTop: 8 }} onSubmit={e => { e.preventDefault(); if (!problem && !busy && !effect) { setNote(null); preview.mutate() } }}>
          <label style={{ display: "flex", flexDirection: "column", gap: 3, fontSize: 12, color: "var(--hf-text-muted)" }}>
            New time zone
            <input aria-label="New time zone" list="cz-zones" maxLength={60} value={wanted} onChange={e => { setWanted(e.target.value); setEffect(null); setRead(false) }} style={input} />
          </label>
          <datalist id="cz-zones">{COMMON_ZONES.filter(z => z !== view.timeZone).map(z => <option key={z} value={z} />)}</datalist>
          {wanted.trim() !== "" && problem && <div style={{ ...muted, marginTop: 6 }}>{problem}</div>}

          {effect && (
            <div role="region" aria-label="What this changes" style={{ marginTop: 8, padding: 10, border: "1px solid var(--hf-border)", borderRadius: 8 }}>
              <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 4 }}>{effect.from} → {effect.to}: the clock moves {shiftText(effect.offsetChangeMinutes)}</div>
              <div style={{ fontSize: 13, overflowWrap: "anywhere" }}>{effect.warning}</div>
              <label style={{ display: "flex", gap: 6, alignItems: "center", fontSize: 13, marginTop: 8 }}>
                <input type="checkbox" aria-label="I have read what this changes" checked={read} onChange={e => setRead(e.target.checked)} />
                I have read what this changes
              </label>
            </div>
          )}

          <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
            {!effect
              ? <button type="submit" style={{ ...btn, opacity: problem ? 0.5 : 1 }} disabled={problem !== null || busy}>Show what changes</button>
              : <button type="button" style={{ ...btn, opacity: read ? 1 : 0.5 }} disabled={!read || busy} onClick={() => { setNote(null); change.mutate() }}>Change time zone</button>}
            <button type="button" style={btn} disabled={busy} onClick={() => { close(); setNote(null) }}>Cancel</button>
          </div>
        </form>
      )}

      {note && <div role={note.ok ? "status" : "alert"} style={{ marginTop: 8, fontSize: 13, color: note.ok ? "var(--hf-text)" : "var(--hf-danger-text)" }}>{note.text}</div>}
    </section>
  )
}
