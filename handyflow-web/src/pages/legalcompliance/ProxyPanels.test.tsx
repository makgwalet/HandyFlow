import { describe, it, expect, vi } from "vitest"
import { renderToStaticMarkup } from "react-dom/server"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("../../api/client", () => ({ apiClient: { get: vi.fn(), post: vi.fn(), put: vi.fn() } }))

import { MeetingProxies, ProxyVotesSection } from "./ProxyPanels"
import type { PMember } from "./proxyLogic"

const m = (id: string, role = "MEMBER"): PMember => ({ id, memberName: id, memberRole: role, appointedOn: "2026-01-01", endedOn: null, active: true })
const members = [m("anna", "CHAIR"), m("ben"), m("dev")]
const proxy = (o: Record<string, unknown>) => ({ id: "p1", grantorMemberId: "dev", grantorName: "Dev", holderMemberId: "ben", holderName: "Ben", charterClause: "Charter 7.2", instructions: null, status: "ACTIVE", usable: false, unusableReason: null,
  appointedBy: "Sec", revokedReason: null, revokedBy: null, revokedAt: null, appointedAt: "2026-11-01T08:00:00Z", ...o })

function render(node: React.ReactNode, seed: [unknown[], unknown][]) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  seed.forEach(([k, v]) => qc.setQueryData(k, v))
  return renderToStaticMarkup(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe("MeetingProxies", () => {
  const meeting = (status: string) => ({ id: "mt", meetingDate: "2026-12-01", status })
  it("a scheduled meeting lists proxies, offers to record one, and says they do not count toward quorum", () => {
    const h = render(<MeetingProxies committeeId="c" meeting={meeting("SCHEDULED")} members={members} canManage />, [[["lc-com", "proxies", "mt"], [proxy({})]]])
    expect(h).toContain("Dev"); expect(h).toContain("Ben"); expect(h).toContain("Charter 7.2"); expect(h).toContain("Active"); expect(h).toContain("Record a proxy"); expect(h).toContain("lapses if the member who gave it attends")
  })
  it("people without manage rights see no buttons", () => {
    const h = render(<MeetingProxies committeeId="c" meeting={meeting("SCHEDULED")} members={members} canManage={false} />, [[["lc-com", "proxies", "mt"], [proxy({})]]])
    expect(h).not.toContain("Record a proxy"); expect(h).not.toContain(">Revoke<")
  })
  it("a held meeting shows in-use and lapsed proxies; one with no proxies shows nothing", () => {
    const h = render(<MeetingProxies committeeId="c" meeting={meeting("HELD")} members={members} canManage />, [[["lc-com", "proxies", "mt"], [proxy({ usable: true }), proxy({ id: "p2", grantorName: "Eve", usable: false, unusableReason: "Eve attended, so votes for themselves" })]]])
    expect(h).toContain("In use"); expect(h).toContain("Lapsed"); expect(h).toContain("Eve attended"); expect(h).not.toContain("Record a proxy")
    expect(render(<MeetingProxies committeeId="c" meeting={meeting("HELD")} members={members} canManage />, [[["lc-com", "proxies", "mt"], []]])).toBe("")
  })
  it("a revoked proxy shows why", () => {
    const h = render(<MeetingProxies committeeId="c" meeting={meeting("SCHEDULED")} members={members} canManage />, [[["lc-com", "proxies", "mt"], [proxy({ status: "REVOKED", revokedReason: "Dev can attend after all", revokedBy: "Sec", revokedAt: "2026-11-05T08:00:00Z" })]]])
    expect(h).toContain("Revoked"); expect(h).toContain("Dev can attend after all")
  })
})

describe("ProxyVotesSection", () => {
  const key = ["lc-com", "proxy-votes", "b"]
  const voters = [{ proxyId: "p1", grantorName: "Dev", holderName: "Ben", usable: true, unusableReason: null, choice: null, reason: null }, { proxyId: "p2", grantorName: "Eve", holderName: "Ben", usable: false, unusableReason: "Eve attended, so votes for themselves", choice: null, reason: null }]
  it("shows an editable row for a usable proxy and the reason for a lapsed one", () => {
    const h = render(<ProxyVotesSection base="b" canManage onChanged={() => {}} />, [[key, { resolutionId: "r", open: true, canVote: true, voters }]])
    expect(h).toContain("Votes by proxy"); expect(h).toContain("for Dev"); expect(h).toContain("Abstain"); expect(h).toContain("Eve attended")
  })
  it("is read-only once voting has closed, and absent when there are no proxies", () => {
    const h = render(<ProxyVotesSection base="b" canManage onChanged={() => {}} />, [[key, { resolutionId: "r", open: false, canVote: false, voters: [{ ...voters[0], choice: "FOR" }] }]])
    expect(h).toContain("For"); expect(h).not.toContain("type=\"radio\"")
    expect(render(<ProxyVotesSection base="b" canManage onChanged={() => {}} />, [[key, { resolutionId: "r", open: true, canVote: true, voters: [] }]])).toBe("")
  })
})
