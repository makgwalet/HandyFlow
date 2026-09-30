// src/pages/property/PropertyPage.tsx
//
// Sections are routes (/property/:section) with navigation in the sidebar
// (see navigation/moduleSections.ts and components/shell/SectionedModulePage).
//
// The Dashboard hands a filter or lease id to Leases and Payments ("Leases
// expiring soon" -> Leases pre-filtered; an outstanding payment -> Payments
// with that lease pre-selected). That used to live in state; it is now in the
// URL (/property/leases?filter=EXPIRING_SOON, /property/payments?lease=<id>),
// so it survives refresh and can be shared. Clicking a section in the sidebar
// clears it, as clicking a tab did before.
import { useNavigate, useSearchParams } from "react-router-dom"
import PropertyDashboard  from "./PropertyDashboard"
import PropertiesTab      from "./PropertiesTab"
import LeasesTab          from "./LeasesTab"
import PaymentsTab        from "./PaymentsTab"
import InspectionsTab     from "./InspectionsTab"
import { SectionedModulePage } from "../../components/shell/SectionedModulePage"
import { PROPERTY_SECTIONS } from "../../navigation/moduleSections"

export function PropertyPage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const leasesFilter    = searchParams.get("filter") ?? undefined
  const paymentsLeaseId = searchParams.get("lease")  ?? undefined

  const navigateFromDashboard = (id: string, payload?: { leasesFilter?: string; paymentsLeaseId?: string }) => {
    const q = new URLSearchParams()
    if (id === "leases"   && payload?.leasesFilter)    q.set("filter", payload.leasesFilter)
    if (id === "payments" && payload?.paymentsLeaseId) q.set("lease",  payload.paymentsLeaseId)
    const qs = q.toString()
    navigate(`${PROPERTY_SECTIONS.basePath}/${id}${qs ? `?${qs}` : ""}`)
  }

  return (
    <SectionedModulePage config={PROPERTY_SECTIONS}
      subtitle="Portfolio management · Leases · Rent tracking · Inspections"
      render={id => {
        switch (id) {
          case "dashboard":   return <PropertyDashboard onNavigate={navigateFromDashboard} />
          case "properties":  return <PropertiesTab />
          case "leases":      return <LeasesTab key={leasesFilter ?? "all"} initialFilter={leasesFilter} />
          case "payments":    return <PaymentsTab key={paymentsLeaseId ?? "all"} initialLeaseId={paymentsLeaseId} />
          case "inspections": return <InspectionsTab />
          default:            return null
        }
      }} />
  )
}
