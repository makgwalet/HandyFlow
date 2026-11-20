import { describe, it, expect, vi } from "vitest"
import { renderToStaticMarkup } from "react-dom/server"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

let admin = false
vi.mock("../../api/client", () => ({ apiClient: { get: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => admin }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { firstName: "Thabang" } }) }))

import LegalComplianceDashboard from "./LegalComplianceDashboard"

const metrics = { "obligations.total": 48, "obligations.compliant": 36, "obligations.dueSoon": 2, "obligations.nonCompliant": 4, "obligations.overdue": 6, "findings.open": 3, "risks.open": 20, "risks.critical": 2, "risks.high": 8 }
const day = (n: number) => { const d = new Date(); d.setDate(d.getDate() + n); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }

function render(seed: boolean, isAdmin: boolean) {
  admin = isAdmin
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  if (seed) {
    qc.setQueryData(["lc-exec", "overview"], { generatedAt: new Date().toISOString(), metrics, attention: [{ key: "a", severity: "CRITICAL", text: "6 obligations are overdue", count: 6, tab: "obligations" }] })
    qc.setQueryData(["lc-calendar-62"], [{ date: day(2), sourceType: "OBLIGATION", sourceId: "1", title: "VAT return", detail: "d" }, { date: day(-1), sourceType: "LITIGATION", sourceId: "2", title: "Hearing", detail: "d" }])
    qc.setQueryData(["lc-dashboard-activity"], { rows: [["2026-11-20 09:00", "Risks", "Risk updated", "Anna"]] })
  }
  return renderToStaticMarkup(<QueryClientProvider client={qc}><LegalComplianceDashboard onNavigate={() => {}} /></QueryClientProvider>)
}

describe("LegalComplianceDashboard", () => {
  it("shows the greeting, the five figures, the donut, the deadlines, the risk bars and the calendar", () => {
    const h = render(true, false)
    expect(h).toContain("Thabang")
    for (const label of ["Total obligations", "Compliant", "At risk", "Overdue", "Open findings"]) expect(h).toContain(label)
    expect(h).toContain("75%"); expect(h).toContain("48"); expect(h).toContain("36")
    expect(h).toContain("VAT return"); expect(h).toContain("Due soon"); expect(h).toContain("Hearing"); expect(h).toContain("Overdue")
    expect(h).toContain("Critical"); expect(h).toContain("Other open"); expect(h).toContain("Compliance calendar")
    expect(h).toContain("Needs attention"); expect(h).toContain("6 obligations are overdue"); expect(h).not.toContain("Recent activity")
  })
  it("administrators see recent activity instead of the attention list", () => {
    const h = render(true, true)
    expect(h).toContain("Recent activity"); expect(h).toContain("Risk updated"); expect(h).not.toContain("6 obligations are overdue")
  })
  it("renders without data (loading) without crashing, with zeros and no invented percentages", () => {
    const h = render(false, false)
    expect(h).toContain("No obligations recorded yet"); expect(h).toContain("Nothing due soon"); expect(h).not.toContain("NaN"); expect(h).not.toContain("undefined")
  })
})
