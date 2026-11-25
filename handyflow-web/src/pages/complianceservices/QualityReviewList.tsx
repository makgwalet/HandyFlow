// src/pages/complianceservices/QualityReviewList.tsx
//
// A list of reviews, each opening into its checklist. Used on the Quality page (all clients) and on a client's own tab.
import { useState } from "react"
import { Link } from "react-router-dom"
import QualityReviewPanel from "./QualityReviewPanel"
import { ordered, progress, statusLabel, subjectLabel, type ReviewSummary } from "./qualityRules"

export default function QualityReviewList({ reviews, canManage, showClient, onChanged, initialOpen }: {
  reviews: ReviewSummary[]; canManage: boolean; showClient?: boolean; onChanged: () => void; initialOpen?: string | null
}) {
  const [open, setOpen] = useState<string | null>(initialOpen ?? null)
  return (
    <div style={{ display: "grid", gap: 8 }}>
      {ordered(reviews).map(r => (
        <div key={r.id} style={{ border: "1px solid var(--hf-border)", borderRadius: 10, padding: "8px 10px", display: "grid", gap: 6 }}>
          <button onClick={() => setOpen(open === r.id ? null : r.id)} aria-expanded={open === r.id}
            style={{ all: "unset", cursor: "pointer", display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap" }}>
            <span style={{ fontWeight: 700, fontSize: 13, color: "var(--hf-text)" }}>{r.subjectLabel}</span>
            <span style={{ flex: 1 }} />
            <span style={{ fontSize: 12, color: "var(--hf-text-muted)" }}>{subjectLabel(r.subjectType)} · round {r.round} · {progress(r)}</span>
            <span style={{ fontSize: 12, fontWeight: 600, color: "var(--hf-text-muted)" }}>{statusLabel(r.status)}</span>
          </button>
          {showClient && r.clientName && <div style={{ fontSize: 12 }}><Link to={`/complianceservices/clients/${r.clientId}?tab=quality`}>{r.clientName}</Link> · {r.templateName}</div>}
          {open === r.id && <QualityReviewPanel reviewId={r.id} canManage={canManage} onChanged={onChanged} />}
        </div>
      ))}
    </div>
  )
}
