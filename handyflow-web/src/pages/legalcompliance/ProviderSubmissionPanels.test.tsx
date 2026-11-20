import { describe, it, expect, vi } from "vitest"
import { renderToStaticMarkup } from "react-dom/server"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("../../api/client", () => ({ apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))

import { ClientSubmissions, SubmissionInbox } from "./ProviderSubmissionPanels"

const sub = (o: Record<string, unknown>) => ({
  id: "1", reference: "PSB-00001", relationshipId: "r", providerName: "Alpha Advisory", kind: "OBSERVATION", area: "CONTROLS", priority: "NORMAL", title: "Missing DPA clause", body: "The supplier contract has no data-processing clause.",
  respondBy: null, submittedBy: "Cora", status: "SUBMITTED", response: null, respondedBy: null, respondedAt: null, sentAt: "2026-11-20T08:00:00Z", overdue: false, ...o,
})

function render(node: React.ReactNode, seed: [unknown[], unknown][]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  seed.forEach(([k, v]) => qc.setQueryData(k, v))
  return renderToStaticMarkup(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe("ClientSubmissions (provider side)", () => {
  it("without the grant it explains, and managers can ask for it", () => {
    const h = render(<ClientSubmissions id="r" status="ACTIVE" granted={["SUMMARY"]} requested={["SUMMARY"]} canManage />, [])
    expect(h).toContain("has not given you permission"); expect(h).toContain("Ask the client for permission to send submissions"); expect(h).not.toContain("New submission")
  })
  it("a consultant who is not a manager cannot ask, and a pending ask says so", () => {
    expect(render(<ClientSubmissions id="r" status="ACTIVE" granted={["SUMMARY"]} requested={["SUMMARY"]} canManage={false} />, [])).not.toContain("Ask the client")
    expect(render(<ClientSubmissions id="r" status="ACTIVE" granted={["SUMMARY"]} requested={["SUMMARY", "SUBMIT"]} canManage />, [])).toContain("waiting for the client")
  })
  it("with the grant it lists what was sent, the reply, and a withdraw button only while unanswered", () => {
    const h = render(<ClientSubmissions id="r" status="ACTIVE" granted={["SUBMIT"]} requested={["SUBMIT"]} canManage={false} />, [[["lc-provider", "submissions", "r"], [sub({}), sub({ id: "2", reference: "PSB-00002", title: "Second one", status: "COMPLETED", response: "Clause added", respondedBy: "Admin", overdue: false })]]])
    expect(h).toContain("New submission"); expect(h).toContain("Missing DPA clause"); expect(h).toContain("Clause added"); expect(h.match(/Withdraw/g)?.length).toBe(1)
    expect(h).toContain("Do not put personal information")
  })
  it("suspended access hides the form", () => {
    expect(render(<ClientSubmissions id="r" status="SUSPENDED" granted={["SUBMIT"]} requested={["SUBMIT"]} canManage />, [])).not.toContain("New submission")
  })
})

describe("SubmissionInbox (client side)", () => {
  const seed: [unknown[], unknown][] = [[["lc-provider-inbox", ""], { open: 1, items: [sub({ overdue: true, respondBy: "2026-11-01" }), sub({ id: "3", status: "DECLINED", title: "Old one here", response: "Not relevant to us" })] }]]
  it("shows each submission with the reply warning and actions only for open ones", () => {
    const h = render(<SubmissionInbox canAnswer />, seed)
    expect(h).toContain("Your reply is visible to the provider"); expect(h).toContain("1 waiting for you"); expect(h).toContain("Reply overdue"); expect(h).toContain("Reply was wanted by 2026-11-01")
    expect(h).toContain("Not relevant to us"); expect(h.match(/>Decline<\/button>/g)?.length).toBe(1)
    expect(h).toContain("Mark done")
  })
  it("read-only users see no action buttons", () => {
    const h = render(<SubmissionInbox canAnswer={false} />, seed)
    expect(h).not.toContain("Mark done"); expect(h).toContain("Missing DPA clause")
  })
})
