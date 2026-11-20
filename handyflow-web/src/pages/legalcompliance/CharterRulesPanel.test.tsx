import { describe, it, expect, vi } from "vitest"
import { renderToStaticMarkup } from "react-dom/server"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("../../api/client", () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }))

import { CharterRulesPanel, CharterRecordForm, type CharterData } from "./CharterRulesPanel"
import { startForm, charterKey } from "./charterLogic"

const ver = (o: Record<string, unknown>) => ({ id: "v1", versionNo: 1, charterReference: "Board charter 2026", adoptedOn: "2026-01-10", effectiveFrom: "2026-02-01", castingVote: true, writtenMode: "EITHER", quorumAfterRecusal: true, specialPercent: 75,
  reason: "Adopted at the annual meeting", recordedBy: "Sec", inForce: true, ...o })
const data = (o: Partial<CharterData> = {}): CharterData => ({ committeeId: "c", recorded: true, currentVersion: 1, castingVote: true, writtenMode: "EITHER", quorumAfterRecusal: true, specialPercent: 75,
  allowedWrittenThresholds: ["UNANIMOUS", "MAJORITY_OF_ALL"], summary: ["The chair has a casting vote on a tied ordinary resolution"], history: [ver({})] as never, ...o })

function render(node: React.ReactNode, d: CharterData | undefined) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  if (d) qc.setQueryData(charterKey("c"), d)
  return renderToStaticMarkup(<QueryClientProvider client={qc}>{node}</QueryClientProvider>)
}

describe("CharterRulesPanel", () => {
  it("says it is loading until the rules arrive", () => expect(render(<CharterRulesPanel committeeId="c" canRecord live />, undefined)).toContain("Loading charter rules"))
  it("shows the rules in force and that the tick-box is ignored", () => {
    const h = render(<CharterRulesPanel committeeId="c" canRecord live />, data())
    expect(h).toContain("Version 1 in force"); expect(h).toContain("casting vote on a tied"); expect(h).toContain("tick-box is ignored"); expect(h).toContain("Record a change"); expect(h).toContain("History (1)")
  })
  it("with no charter recorded, explains the house rules and offers to record one", () => {
    const h = render(<CharterRulesPanel committeeId="c" canRecord live />, data({ recorded: false, currentVersion: null, history: [], summary: ["No charter rules recorded: house rules apply"] }))
    expect(h).toContain("Not recorded"); expect(h).toContain("house rules"); expect(h).toContain("Record charter rules"); expect(h).not.toContain("History"); expect(h).not.toContain("tick-box is ignored")
  })
  it("hides the button from people who cannot record, and for a dissolved committee", () => {
    expect(render(<CharterRulesPanel committeeId="c" canRecord={false} live />, data())).not.toContain("Record a change")
    expect(render(<CharterRulesPanel committeeId="c" canRecord live={false} />, data())).not.toContain("Record a change")
  })
  it("announces a version that is recorded ahead of time, with what it changes", () => {
    const h = render(<CharterRulesPanel committeeId="c" canRecord live />, data({ history: [ver({ id: "v2", versionNo: 2, effectiveFrom: "2027-01-01", inForce: false, specialPercent: 80 }), ver({})] as never }))
    expect(h).toContain("Version 2 takes effect on"); expect(h).toContain("Special resolutions: 75% → 80%")
  })
})

describe("CharterRecordForm", () => {
  const f = { ...startForm(null, "2026-11-20"), adoptedOn: "2026-10-01", reason: "Adopted by the board" }
  const html = (o: Partial<Parameters<typeof CharterRecordForm>[0]> = {}) => renderToStaticMarkup(<CharterRecordForm form={f} setForm={() => {}} problem={null} pending={false} err="" onCancel={() => {}} onSave={() => {}} {...o} />)
  it("offers every written-resolution choice and the percentage", () => {
    const h = html(); expect(h).toContain("Not allowed"); expect(h).toContain("Only if every member agrees"); expect(h).toContain("majority of all members"); expect(h).toContain('value="75"')
  })
  it("disables saving while there is a problem and shows it", () => {
    const h = html({ problem: "Give a reason of at least 10 characters" })
    expect(h).toContain("Give a reason"); expect(h).toMatch(/<button[^>]*disabled[^>]*>Save rules/)
  })
  it("can be saved when there is no problem, and says it cannot be edited afterwards", () => {
    const h = html(); expect(h).not.toMatch(/<button[^>]*disabled[^>]*>Save rules/); expect(h).toContain("cannot be edited afterwards")
  })
  it("shows a server error", () => expect(html({ err: "A later version is already recorded" })).toContain("A later version is already recorded"))
})
