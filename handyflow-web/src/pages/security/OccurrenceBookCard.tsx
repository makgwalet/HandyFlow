// src/pages/security/OccurrenceBookCard.tsx
//
// The Daily Occurrence Book of one site: day picker, the log oldest first, and an entry form for supervisors.
import { useRef, useState } from "react"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { BookOpen, ChevronLeft, ChevronRight, Printer } from "lucide-react"
import { apiClient } from "../../api/client"
import Chip from "../../components/ui/Chip"
import { printSection } from "./printSection"
import { useAuthStore } from "../../store/auth.store"
import {
  KIND_TONE, MAX_TEXT, PRINT_CSS, printSignLine, printTitle, printedAt, allowedKinds, canGoNext, canSign, effectiveKind, signBody, signWarning, signedText, dayTitle, emptyText, entryBody, entryProblem, kindLabel, newClientRef, shiftDay, timeText, todaySast,
  type ObDay,
} from "./occurrenceBookLogic"

const card: React.CSSProperties = { background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 16 }
const muted: React.CSSProperties = { fontSize: 13, color: "var(--hf-text-muted)" }
const navBtn: React.CSSProperties = { padding: "4px 8px", border: "1px solid var(--hf-border)", borderRadius: 7, background: "var(--hf-surface)", color: "var(--hf-text-secondary)", cursor: "pointer", display: "inline-flex", alignItems: "center" }

export default function OccurrenceBookCard({ siteId }: { siteId: string }) {
  const [date, setDate] = useState(() => todaySast())
  const [kind, setKind] = useState("NOTE")
  const [text, setText] = useState("")
  const [err, setErr] = useState("")
  const [signing, setSigning] = useState(false)
  const [signNote, setSignNote] = useState("")
  const [signErr, setSignErr] = useState("")
  const ref = useRef<string | null>(null)
  const canWrite = useAuthStore(s => s.hasPermission("SECURITY_MANAGE"))
  const qc = useQueryClient()

  const { data, isLoading, error } = useQuery<ObDay>({
    queryKey: ["occurrence-book", siteId, date],
    queryFn: async () => (await apiClient.get(`/api/v1/security/sites/${siteId}/occurrence-book`, { params: { date } })).data?.data,
    refetchInterval: date === todaySast() ? 60000 : false,
  })
  const lines = Array.isArray(data?.lines) ? data!.lines : null
  const signoff = data?.signoff ?? null
  const signed = !!signoff
  const useKind = effectiveKind(kind, signed)

  const sign = useMutation({
    mutationFn: async () => { await apiClient.post(`/api/v1/security/sites/${siteId}/occurrence-book/sign`, signBody(signNote), { params: { date } }) },
    onSuccess: () => { setSigning(false); setSignNote(""); setSignErr(""); qc.invalidateQueries({ queryKey: ["occurrence-book", siteId] }) },
    onError: (e: unknown) => setSignErr((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Could not sign the day. Please try again."),
  })

  const add = useMutation({
    mutationFn: async () => {
      if (!ref.current) ref.current = newClientRef()
      await apiClient.post(`/api/v1/security/sites/${siteId}/occurrence-book/entries`, entryBody(useKind, text, ref.current))
    },
    onSuccess: () => { ref.current = null; setText(""); setErr(""); qc.invalidateQueries({ queryKey: ["occurrence-book", siteId] }) },
    onError: (e: unknown) => setErr((e as { response?: { data?: { message?: string } } })?.response?.data?.message ?? "Could not add the entry. Please try again."),
  })

  const submit = () => {
    const p = entryProblem(text)
    if (p) { setErr(p); return }
    setErr("")
    add.mutate()
  }

  return (
    <section aria-label="Occurrence Book" style={{ ...card, marginTop: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 8, flexWrap: "wrap", marginBottom: 10 }}>
        <div style={{ display: "flex", alignItems: "center", gap: 8, fontWeight: 700, color: "var(--hf-text-primary)" }}><BookOpen size={16} /> Occurrence Book</div>
        <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
          <button type="button" aria-label="Previous day" style={navBtn} onClick={() => setDate(d => shiftDay(d, -1))}><ChevronLeft size={14} /></button>
          <input type="date" aria-label="Day" value={date} max={todaySast()} onChange={e => e.target.value && e.target.value <= todaySast() && setDate(e.target.value)} />
          <button type="button" aria-label="Print this day" style={{ ...navBtn, opacity: lines ? 1 : 0.4 }} disabled={!lines} onClick={() => printSection("ob")}><Printer size={14} /></button>
          <button type="button" aria-label="Next day" style={{ ...navBtn, opacity: canGoNext(date) ? 1 : 0.4 }} disabled={!canGoNext(date)} onClick={() => setDate(d => shiftDay(d, 1))}><ChevronRight size={14} /></button>
        </div>
      </div>
      <div style={{ ...muted, marginBottom: 8 }}>{dayTitle(date)}</div>
      {signoff && (
        <div style={{ marginBottom: 8, fontSize: 13 }}>
          <Chip tone="ok">Signed</Chip> <span>{signedText(signoff)}</span>{signoff.note && <span style={muted}> “{signoff.note}”</span>}
        </div>
      )}

      {isLoading && <div style={muted}>Loading…</div>}
      {(error || (data && !lines)) && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)" }}>The Occurrence Book could not be loaded.</div>}
      {lines && lines.length === 0 && <div style={muted}>{emptyText(date, todaySast())}</div>}
      {lines && lines.length > 0 && (
        <ol style={{ listStyle: "none", margin: 0, padding: 0 }}>
          {lines.map((l, i) => (
            <li key={`${l.refId ?? "x"}-${i}`} style={{ display: "flex", gap: 10, alignItems: "baseline", padding: "7px 0", borderTop: i ? "1px solid var(--hf-border-subtle)" : "none", fontSize: 13 }}>
              <span style={{ width: 44, flex: "none", fontVariantNumeric: "tabular-nums", color: "var(--hf-text-muted)" }}>{timeText(l.at)}</span>
              <span style={{ width: 84, flex: "none" }}><Chip tone={KIND_TONE[l.kind] ?? "neutral"}>{kindLabel(l.kind)}</Chip></span>
              <span style={{ flex: 1 }}>{l.text}{l.guardName && <span style={muted}> · {l.guardName}</span>}</span>
            </li>
          ))}
        </ol>
      )}
      {data?.truncated && <div style={{ ...muted, marginTop: 6 }}>This day has more entries than can be shown here.</div>}

      {canWrite && (
        <div style={{ marginTop: 14, paddingTop: 12, borderTop: "1px solid var(--hf-border)" }}>
          <div style={{ display: "flex", gap: 8, flexWrap: "wrap", marginBottom: 6 }}>
            <select aria-label="Entry type" value={useKind} onChange={e => { ref.current = null; setKind(e.target.value) }}>
              {allowedKinds(signed).map(k => <option key={k} value={k}>{kindLabel(k)}</option>)}
            </select>
            <textarea aria-label="Entry text" value={text} maxLength={MAX_TEXT} rows={2} onChange={e => { ref.current = null; setText(e.target.value) }} placeholder="What should the next person know?" style={{ flex: 1, minWidth: 220 }} />
            <button type="button" onClick={submit} disabled={add.isPending} style={{ ...navBtn, fontWeight: 600 }}>{add.isPending ? "Adding…" : "Add entry"}</button>
          </div>
          <div style={muted}>{signed ? "This day is signed. Only Corrections can be added." : "Entries cannot be edited or deleted. To fix a mistake, add a Correction."}</div>
          {err && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 4 }}>{err}</div>}
          {canSign(canWrite, signed) && lines && (
            <div style={{ marginTop: 10 }}>
              {!signing ? (
                <button type="button" style={{ ...navBtn, fontWeight: 600 }} onClick={() => { setSigning(true); setSignErr("") }}>Sign this day</button>
              ) : (
                <div role="group" aria-label="Sign this day" style={{ display: "flex", gap: 8, flexWrap: "wrap", alignItems: "center" }}>
                  <span style={{ fontSize: 13 }}>{signWarning(date, todaySast())}</span>
                  <input aria-label="Sign-off note" value={signNote} maxLength={500} placeholder="Note (optional)" onChange={e => setSignNote(e.target.value)} style={{ minWidth: 200 }} />
                  <button type="button" style={{ ...navBtn, fontWeight: 600 }} disabled={sign.isPending} onClick={() => sign.mutate()}>{sign.isPending ? "Signing…" : "Confirm sign-off"}</button>
                  <button type="button" style={navBtn} onClick={() => { setSigning(false); setSignErr("") }}>Cancel</button>
                </div>
              )}
              {signErr && <div role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", marginTop: 4 }}>{signErr}</div>}
            </div>
          )}
        </div>
      )}

      {lines && data && (
        <div className="ob-print" aria-hidden="true" data-testid="ob-print">
          <style>{PRINT_CSS}</style>
          <h1 style={{ fontSize: 18, margin: "0 0 4px" }}>{printTitle(data.siteName, data.date)}</h1>
          <div style={{ marginBottom: 8, fontWeight: 700 }}>{printSignLine(signoff)}</div>
          <table>
            <thead><tr><th>Time</th><th>Type</th><th>Entry</th><th>Guard</th></tr></thead>
            <tbody>
              {lines.map((l, i) => (
                <tr key={`${l.refId ?? "x"}-${i}`}><td>{timeText(l.at)}</td><td>{kindLabel(l.kind)}</td><td>{l.text}</td><td>{l.guardName ?? ""}</td></tr>
              ))}
              {lines.length === 0 && <tr><td colSpan={4}>No entries.</td></tr>}
            </tbody>
          </table>
          {data.truncated && <div>This day has more entries than are shown here.</div>}
          <div style={{ marginTop: 10, fontSize: 11 }}>{printedAt()}</div>
        </div>
      )}
    </section>
  )
}
