// src/pages/compliancetender/TenderPricingCard.tsx
//
// A short summary of a tender's price on its detail page, linking to the full pricing screen (ADR-004). Only people who can manage tenders see it: pricing is commercially sensitive.
import { Link } from "react-router-dom"
import { Calculator, Lock } from "lucide-react"
import { usePermission } from "../../hooks/usePermission"
import { COMPANY_SCOPE, usePricing, type PricingScope } from "./pricing.api"
import { fmtZar, marginText } from "./pricing.logic"

export default function TenderPricingCard({ tenderId, scope = COMPANY_SCOPE }: { tenderId: string; scope?: PricingScope }) {
  const canManage = usePermission(scope.manage)
  const canAdmin = usePermission(scope.admin)
  const allowed = canManage || canAdmin
  const { data, isError } = usePricing(tenderId, allowed, scope)
  if (!allowed) return null

  const priced = !!data && (data.configured || data.lines.length > 0)
  return (
    <section aria-label="Pricing" style={{ background: "var(--hf-surface)", border: "1px solid var(--hf-border)", borderRadius: 12, padding: 20, marginBottom: 16 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: 12, flexWrap: "wrap" }}>
        <h3 style={{ fontSize: 14, fontWeight: 800, color: "var(--hf-text)", margin: 0, display: "flex", alignItems: "center", gap: 8 }}>
          <Calculator size={15} aria-hidden="true" /> Pricing {data && !data.editable && <Lock size={13} aria-label="Locked" />}
        </h3>
        <Link to={scope.pricingPage(tenderId)} style={{ fontSize: 13, fontWeight: 600, color: "var(--hf-sky-text-strong)" }}>
          {data && !data.editable ? "View pricing" : priced ? "Open pricing" : "Start pricing"}
        </Link>
      </div>
      {isError && <p role="alert" style={{ fontSize: 13, color: "var(--hf-danger-text)", margin: "8px 0 0" }}>We couldn't load the pricing.</p>}
      {data && !priced && <p style={{ fontSize: 13, color: "var(--hf-text-faint)", margin: "8px 0 0" }}>Not priced yet.</p>}
      {data && priced && (
        <p style={{ fontSize: 13, color: "var(--hf-text-secondary)", margin: "8px 0 0" }}>
          <strong style={{ color: "var(--hf-text)" }}>{fmtZar(data.breakdown.priceInclVat)}</strong> including VAT
          {" · "}{fmtZar(data.breakdown.priceExVat)} excluding · {data.lines.length} {data.lines.length === 1 ? "item" : "items"} · {marginText(data.breakdown.marginPct)}
        </p>
      )}
    </section>
  )
}
