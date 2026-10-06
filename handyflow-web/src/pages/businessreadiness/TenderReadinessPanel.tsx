// src/pages/businessreadiness/TenderReadinessPanel.tsx
//
// "Readiness check" on a tender (ADR-003): each requirement judged against the registrations and documents the business holds, as of the closing date. One panel serves a tenant's own
// tenders and a client's; only the URL differs. It is READ-ONLY: it never changes a requirement's status (the user's own tick stays theirs), it only says where the evidence disagrees.
import { AlertTriangle, CheckCircle2, CircleHelp, Clock3, XCircle, MinusCircle } from "lucide-react"
import { Link } from "react-router-dom"
import { useReadiness, type ReadinessResult } from "./readiness.api"
import { MANUAL_LABEL, RESULT_LABEL, basisText, chips, headline, itemNotes, sortItems } from "./readiness.logic"

const TONE: Record<ReadinessResult, { color: string; bg: string; Icon: React.ElementType }> = {
  MET: { color: "var(--hf-success-text-strong)", bg: "var(--hf-success-soft-strong)", Icon: CheckCircle2 },
  MISSING: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", Icon: XCircle },
  EXPIRED: { color: "var(--hf-danger-text)", bg: "var(--hf-danger-soft)", Icon: AlertTriangle },
  PENDING: { color: "var(--hf-warning-text)", bg: "var(--hf-warning-soft)", Icon: Clock3 },
  NOT_EVALUATED: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", Icon: CircleHelp },
  NOT_APPLICABLE: { color: "var(--hf-text-muted)", bg: "var(--hf-surface-sunken)", Icon: MinusCircle },
}

export default function TenderReadinessPanel({ url, catalogueHref }: { url: string; catalogueHref: string }) {
  const { data, isLoading, isError, refetch } = useReadiness(url)

  return (
    <section aria-label="Readiness check" style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 20, marginBottom: 16 }}>
      <h3 style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: "0 0 2px" }}>Readiness check</h3>
      {isLoading && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: "6px 0 0" }}>Checking readiness…</p>}
      {isError && (
        <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "6px 0 0" }}>
          We couldn't check readiness. <button type="button" onClick={() => refetch()} style={{ background: "none", border: "none", textDecoration: "underline", cursor: "pointer", color: "inherit", fontWeight: 700 }}>Try again</button>
        </p>
      )}
      {data && (
        <>
          <p style={{ fontSize: 12, color: "var(--hf-text-muted)", margin: "0 0 10px" }}>{basisText(data)}</p>
          <p style={{ fontSize: 15, fontWeight: 700, color: "var(--hf-text)", margin: "0 0 8px" }}>{headline(data.summary)}</p>
          {data.summary.total > 0 && (
            <ul aria-label="Readiness summary" style={{ listStyle: "none", padding: 0, margin: "0 0 12px", display: "flex", flexWrap: "wrap", gap: 6 }}>
              {chips(data.summary).map(c => <li key={c.key} style={{ fontSize: 12, fontWeight: 600, padding: "3px 10px", borderRadius: 999, background: "var(--hf-surface-sunken)", color: "var(--hf-text-secondary)" }}>{c.text}</li>)}
            </ul>
          )}
          {data.items.length === 0 ? (
            <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: 0 }}>Add requirements to this tender to see how ready you are.</p>
          ) : (
            <ul aria-label="Readiness by requirement" style={{ listStyle: "none", padding: 0, margin: 0, display: "flex", flexDirection: "column", gap: 6 }}>
              {sortItems(data.items).map((it, i) => {
                const tone = TONE[it.result]; const notes = itemNotes(it)
                return (
                  <li key={`${it.requirementId ?? "x"}-${i}`} aria-label={`Requirement ${it.label}`} style={{ padding: "10px 14px", background: "var(--hf-surface-muted, var(--hf-surface-sunken))", border: "1px solid var(--hf-border-subtle)", borderRadius: 8 }}>
                    <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", gap: 12 }}>
                      <span style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-text)", minWidth: 0 }}>{it.label}</span>
                      <span style={{ display: "inline-flex", alignItems: "center", gap: 5, fontSize: 11, fontWeight: 700, color: tone.color, background: tone.bg, padding: "4px 10px", borderRadius: 6, whiteSpace: "nowrap" }}>
                        <tone.Icon size={12} aria-hidden="true" />{RESULT_LABEL[it.result]}
                      </span>
                    </div>
                    <div style={{ fontSize: 12, color: "var(--hf-text-secondary)", marginTop: 3 }}>{it.detail}</div>
                    {notes.map(n => <div key={n} style={{ fontSize: 12, color: "var(--hf-warning-text)", marginTop: 3 }}>{n}</div>)}
                    <div style={{ fontSize: 11, color: "var(--hf-text-faint)", marginTop: 3 }}>Your status: {MANUAL_LABEL[it.manualStatus] ?? it.manualStatus}</div>
                  </li>
                )
              })}
            </ul>
          )}
          <p style={{ fontSize: 11.5, color: "var(--hf-text-faint)", margin: "12px 0 0" }}>
            This compares each requirement's evidence rule with the registrations and documents recorded. It never changes your status. Requirements with no rule aren't checked: set one under <Link to={catalogueHref} style={{ color: "var(--hf-sky-text-strong)", fontWeight: 600 }}>Requirements</Link>.
          </p>
        </>
      )}
    </section>
  )
}
