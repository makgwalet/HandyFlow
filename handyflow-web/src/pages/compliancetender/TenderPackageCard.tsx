// src/pages/compliancetender/TenderPackageCard.tsx
//
// A short summary of a tender's submission package on its detail page, linking to the full package screen (ADR-005).
import { Link } from "react-router-dom"
import { Package } from "lucide-react"
import { usePackages } from "./package.api"
import { fmtSize, fmtWhen } from "./package.logic"

export default function TenderPackageCard({ tenderId }: { tenderId: string }) {
  const { data, isError } = usePackages(tenderId)
  const latest = data && data.length > 0 ? data[0] : null
  return (
    <section aria-label="Submission package" style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 20, marginBottom: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <h3 style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: 0, display: "flex", alignItems: "center", gap: 8 }}>
          <Package size={15} aria-hidden="true" /> Submission package
        </h3>
        <Link to={`/compliancetender/tenders/${tenderId}/package`} style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-sky-text-strong)" }}>
          {latest ? "Open package" : "Build package"}
        </Link>
      </div>
      {isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>We couldn't load the packages.</p>}
      {data && !latest && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>No package built yet.</p>}
      {latest && (
        <p style={{ fontSize: 13, color: "var(--hf-text-secondary)", margin: "8px 0 0" }}>
          <strong style={{ color: "var(--hf-text)" }}>Version {latest.versionNo}</strong> · {latest.submissionReady ? "ready to submit" : "draft"} · {fmtSize(latest.sizeBytes)} · built {fmtWhen(latest.createdAt)}
          {data && data.length > 1 ? ` · ${data.length} versions` : ""}
        </p>
      )}
    </section>
  )
}
