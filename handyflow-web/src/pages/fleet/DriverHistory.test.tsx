import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DriverHistory, { RecentActivity } from "./DriverHistory"
import { buildDriverTimeline } from "./driverTimeline.logic"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const docs = [
  { id: "d2", type: "PRDP", title: null, version: 2, uploadedAt: "2026-10-01T08:00:00Z", fileName: "prdp-new.pdf" },
  { id: "d1", type: "PRDP", title: null, version: 1, uploadedAt: "2025-10-01T08:00:00Z", fileName: "prdp-old.pdf" },
  { id: "d3", type: "TRAINING_CERTIFICATE", title: "Defensive driving", version: 1, uploadedAt: "2026-06-01T08:00:00Z", fileName: "dd.pdf" },
]
const accidents = [{ id: "a1", referenceNumber: "ACC-2026-00021", occurredAt: "2026-08-15T10:00:00Z", status: "IN_REPAIR" }]
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("driver timeline", () => {
  it("merges registration, documents and accidents, newest first", () => {
    const t = buildDriverTimeline({ createdAt: "2025-01-10T00:00:00Z", documents: docs, accidents })
    expect(t.map(i => i.title)).toEqual(["PrDP replaced", "Accident ACC-2026-00021", "Defensive driving added", "PrDP added", "Registered as a driver"])
    expect(t[0].detail).toBe("prdp-new.pdf · version 2"); expect(t[1]).toMatchObject({ detail: "In repair", tone: "bad" })
  })
  it("skips documents with no upload time and copes with nothing", () => {
    expect(buildDriverTimeline({ documents: [{ id: "x", type: "PRDP", title: null, version: 1, fileName: "f" }], accidents: [] })).toEqual([])
  })
})
describe("driver history screens", () => {
  it("shows the full history on the tab", async () => {
    get.mockResolvedValue({ data: { data: docs } })
    wrap(<DriverHistory driverId="dr1" createdAt="2025-01-10T00:00:00Z" accidents={accidents} />)
    expect(await screen.findByText("PrDP replaced")).toBeTruthy(); expect(screen.getByText("Registered as a driver")).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/fleet/drivers/dr1/documents?includeSuperseded=true")
    expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–5 of 5")
  })
  it("shows only the latest five as recent activity, and says when there is nothing", async () => {
    const many = Array.from({ length: 8 }, (_, i) => ({ id: `d${i}`, type: "OTHER", title: `Doc ${i}`, version: 1, uploadedAt: `2026-0${i + 1}-01T00:00:00Z`, fileName: "f.pdf" }))
    get.mockResolvedValue({ data: { data: many } })
    wrap(<RecentActivity driverId="dr1" createdAt={null} accidents={[]} />)
    expect(await screen.findByText("Doc 7 added")).toBeTruthy(); expect(screen.queryByText("Doc 2 added")).toBeNull()
    cleanup(); get.mockResolvedValue({ data: { data: [] } }); wrap(<RecentActivity driverId="dr1" createdAt={null} accidents={[]} />)
    expect(await screen.findByText("Nothing recorded yet.")).toBeTruthy()
  })
  it("reports a failure", async () => {
    get.mockRejectedValue(new Error("x")); wrap(<DriverHistory driverId="dr1" createdAt={null} accidents={[]} />)
    expect((await screen.findByRole("alert")).textContent).toContain("Couldn't load the history"); fireEvent.click(document.body)
  })
})
