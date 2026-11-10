import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseLotsTab from "./WhseLotsTab"
import WhseTasksTab from "./WhseTasksTab"
import WhseInspectionTab from "./WhseInspectionTab"
import WhseInboundShipmentDetail from "./WhseInboundShipmentDetail"
import WhseReturnsTab from "./WhseReturnsTab"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.includes(p) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = []; post.mockResolvedValue({ data: null }) })
afterEach(cleanup)

const lot = (o: object = {}) => ({ id: "l1", clientId: "c", itemId: "i", sku: "MILK", lotNumber: "L7", expiryDate: "2026-12-01", manufacturedDate: null, daysToExpiry: 21, expiryStatus: "EXPIRING", onHand: 12, available: 8, blocked: false, blockedReason: null, ...o })

describe("lots and expiry", () => {
  it("blocks a lot with a reason and then lists the open orders that hold it", async () => {
    perms = ["WAREHOUSING_MANAGE"]
    get.mockImplementation(async (url: string) => (url.includes("expiry-report") ? { data: [] } : { data: [lot()] }))
    post.mockResolvedValueOnce({ data: [{ orderId: "o1", orderReference: "OUT-9", qty: 4 }] })
    wrap(<WhseLotsTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "All lots" }))
    fireEvent.click(await screen.findByText("Block"))
    const confirm = screen.getByText("Block lot") as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "supplier recall notice" } })
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/lots/l1/block", { reason: "supplier recall notice" }))
    await waitFor(() => expect(screen.getByText(/OUT-9/)).toBeTruthy())
    expect(screen.getByText(/re-allocate its lots/)).toBeTruthy()
  })

  it("shows a blocked lot with its reason and offers to release it", async () => {
    perms = ["WAREHOUSING_ADMIN"]
    get.mockImplementation(async (url: string) => (url.includes("expiry-report") ? { data: [] } : { data: [lot({ blocked: true, blockedReason: "recalled by supplier" })] }))
    wrap(<WhseLotsTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "All lots" }))
    expect(await screen.findByText("BLOCKED")).toBeTruthy()
    expect(screen.getByText("recalled by supplier")).toBeTruthy()
    fireEvent.click(screen.getByText("Release"))
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "cleared by QA" } })
    fireEvent.click(screen.getByText("Release lot"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/lots/l1/unblock", { reason: "cleared by QA" }))
  })

  it("hides block and release from people who cannot manage", async () => {
    get.mockImplementation(async (url: string) => (url.includes("expiry-report") ? { data: [] } : { data: [lot()] }))
    wrap(<WhseLotsTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "All lots" }))
    await screen.findByText(/lot L7/)
    expect(screen.queryByText("Block")).toBeNull()
  })

  it("lists expiring stock with how long is left", async () => {
    get.mockResolvedValue({ data: [{ inventoryId: "x", clientName: "Acme", sku: "MILK", locationCode: "A-01", stockStatus: "AVAILABLE", lotNumber: "L7", expiryDate: "2026-11-12", daysToExpiry: 2, expiryStatus: "EXPIRING", qtyOnHand: 5, qtyAllocated: 0 }] })
    wrap(<WhseLotsTab />)
    expect(await screen.findByText(/2 days left/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/warehousing/lots/expiry-report?days=30")
  })
})

const pick = (o: object = {}) => ({ id: "t1", taskType: "PICK", status: "OPEN", priority: 5, clientName: "Acme", sku: "MILK", itemDescription: null, lotNumber: "L7", lotExpiry: "2026-12-01",
  fromLocationId: "loc1", fromCode: "A-01-01", toLocationId: null, toCode: null, qty: 4, qtyDone: 0, referenceNumber: "OUT-1", assignedTo: null, note: null, ...o })

describe("tasks", () => {
  function serve(tasks: object[]) {
    get.mockImplementation(async (url: string) => {
      if (url.includes("/tasks/summary")) return { data: { openPicks: 1, inProgressPicks: 0, shortPicks: 2, openPutaways: 0, inProgressPutaways: 0 } }
      if (url.includes("/suggestions")) return { data: [{ locationId: "d1", code: "B-02-01", reason: "Already holds this item", roomLeft: 8 }] }
      return { data: tasks }
    })
  }

  it("shows the pick, its lot and place, and claims it", async () => {
    perms = ["WAREHOUSING_MANAGE"]; serve([pick()])
    wrap(<WhseTasksTab />)
    expect(await screen.findByText(/Pick 4 × MILK/)).toBeTruthy()
    expect(screen.getByText(/lot L7/)).toBeTruthy()
    fireEvent.click(screen.getByText("Claim"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/tasks/t1/claim"))
  })

  it("completes a full pick without a reason", async () => {
    perms = ["WAREHOUSING_MANAGE"]; serve([pick({ status: "IN_PROGRESS", assignedTo: "me" })])
    wrap(<WhseTasksTab />)
    fireEvent.click(await screen.findByText("Do it"))
    fireEvent.click(screen.getByText("Complete"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/tasks/t1/complete", { qty: 4, reason: null, toLocationId: null }))
  })

  it("needs a reason for a short pick", async () => {
    perms = ["WAREHOUSING_MANAGE"]; serve([pick({ status: "IN_PROGRESS", assignedTo: "me" })])
    wrap(<WhseTasksTab />)
    fireEvent.click(await screen.findByText("Do it"))
    fireEvent.change(screen.getByLabelText("Quantity done"), { target: { value: "2" } })
    const record = screen.getByText("Record short pick") as HTMLButtonElement
    expect(record.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Short pick reason"), { target: { value: "only two on the shelf" } })
    expect(record.disabled).toBe(false)
    fireEvent.click(record)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/tasks/t1/complete", { qty: 2, reason: "only two on the shelf", toLocationId: null }))
  })

  it("explains a short task and what to do about it", async () => {
    perms = ["WAREHOUSING_MANAGE"]; serve([pick({ status: "SHORT", qtyDone: 2, note: "only two on the shelf" })])
    wrap(<WhseTasksTab />)
    expect(await screen.findByText(/Re-allocate or cancel the order/)).toBeTruthy()
    expect(screen.queryByText("Do it")).toBeNull()
  })

  it("syncs the put-away list and moves the stock to a suggested location", async () => {
    perms = ["WAREHOUSING_MANAGE"]
    serve([pick({ id: "p1", taskType: "PUTAWAY", fromCode: "DOCK-1", toLocationId: "d1", toCode: "B-02-01", lotNumber: null, lotExpiry: null, qty: 6, referenceNumber: null })])
    post.mockResolvedValue({ data: { created: 1 } })
    wrap(<WhseTasksTab />)
    fireEvent.click(screen.getByRole("tab", { name: "Put-away" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/tasks/putaway/sync"))
    fireEvent.click(await screen.findByText("Do it"))
    await screen.findByText(/Already holds this item/)
    fireEvent.click(screen.getByText("Complete"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/tasks/p1/complete", { qty: 6, reason: null, toLocationId: "d1" }))
  })

  it("does not offer actions to a read-only user", async () => {
    serve([pick()])
    wrap(<WhseTasksTab />)
    await screen.findByText(/Pick 4 × MILK/)
    expect(screen.queryByText("Claim")).toBeNull()
  })
})

describe("inspection", () => {
  const row = { receiptId: "r1", shipmentId: "s1", shipmentReference: "PO-7", clientName: "Acme", sku: "MILK", description: null, locationCode: "QC-1", heldPending: 5, reason: "box crushed", receivedAt: "2026-11-09T10:00:00Z", lotNumber: "L7", expiryDate: "2026-12-01" }

  it("records a pass with a reason", async () => {
    perms = ["WAREHOUSING_QC_RELEASE"]; get.mockResolvedValue({ data: [row] })
    wrap(<WhseInspectionTab />)
    await screen.findByText(/5 held at QC-1/)
    const record = screen.getByText("Record") as HTMLButtonElement
    expect(record.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Inspection reason"), { target: { value: "contents fine" } })
    fireEvent.click(record)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/receiving/receipts/r1/inspect", { outcome: "PASS", qty: 5, reason: "contents fine" }))
  })

  it("tells someone without the permission why they cannot inspect", async () => {
    get.mockResolvedValue({ data: [row] })
    wrap(<WhseInspectionTab />)
    expect(await screen.findByText(/QC release permission/)).toBeTruthy()
    expect(screen.queryByText("Record")).toBeNull()
  })
})

describe("receiving", () => {
  const items = [{ id: "i1", sku: "MILK", lotTracked: true, expiryTracked: true, minShelfLifeDays: 30 }, { id: "i2", sku: "BOLT" }]
  function serve(report: object = { closed: false, closeNote: null, needsSignOff: false, lines: [] }) {
    get.mockImplementation(async (url: string) => {
      if (url.endsWith("/lines")) return { data: [{ id: "ln1", shipmentId: "s1", itemId: "i1", expectedQty: 10, qtyReceived: 0, notes: null }, { id: "ln2", shipmentId: "s1", itemId: "i2", expectedQty: 5, qtyReceived: 0, notes: null }] }
      if (url.endsWith("/receipts")) return { data: [] }
      if (url.endsWith("/discrepancies")) return { data: report }
      if (url.endsWith("/locations")) return { data: [{ id: "loc1", code: "DOCK-1", active: true }] }
      return { data: { id: "s1", referenceNumber: "PO-7", expectedDate: null, receivedDate: null, status: "EXPECTED", notes: null } }
    })
  }

  it("asks for the lot and expiry of a tracked item and sends them", async () => {
    serve()
    wrap(<WhseInboundShipmentDetail shipmentId="s1" clientId="c1" items={items} onBack={() => {}} />)
    fireEvent.click((await screen.findAllByText("Receive"))[0])
    const go = screen.getAllByText("Receive").find(b => (b as HTMLButtonElement).type !== undefined && b.closest("div[style*='grid']")) as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Good"), { target: { value: "10" } })
    fireEvent.change(screen.getByLabelText("Put away at"), { target: { value: "loc1" } })
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Lot number"), { target: { value: "L7" } })
    fireEvent.change(screen.getByLabelText("Expiry date"), { target: { value: "2026-12-01" } })
    await waitFor(() => expect(go.disabled).toBe(false))
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/inbound-shipments/s1/lines/ln1/receipts",
      expect.objectContaining({ qtyGood: 10, locationId: "loc1", lotNumber: "L7", expiryDate: "2026-12-01" })))
  })

  it("does not ask for a lot on an untracked item", async () => {
    serve()
    wrap(<WhseInboundShipmentDetail shipmentId="s1" clientId="c1" items={items} onBack={() => {}} />)
    fireEvent.click((await screen.findAllByText("Receive"))[1])
    expect(screen.queryByLabelText("Lot number")).toBeNull()
    expect(screen.getByLabelText("Good")).toBeTruthy()
  })

  it("flags discrepancies and needs a note to close the delivery", async () => {
    serve({ closed: false, closeNote: null, needsSignOff: true, lines: [{ lineId: "ln1", sku: "MILK", unexpected: false, expected: 10, received: 8, shortQty: 2, overQty: 0, heldPending: 0, types: ["SHORT"] }] })
    wrap(<WhseInboundShipmentDetail shipmentId="s1" clientId="c1" items={items} onBack={() => {}} />)
    expect((await screen.findAllByText("Short")).length).toBeGreaterThan(0)
    const close = screen.getByText("Close delivery") as HTMLButtonElement
    expect(close.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Closing note"), { target: { value: "driver confirmed two short" } })
    expect(close.disabled).toBe(false)
    fireEvent.click(close)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/inbound-shipments/s1/close", { note: "driver confirmed two short" }))
  })
})

describe("returns", () => {
  const rma = { id: "r1", rmaNumber: "RMA-0001", clientId: "c1", clientName: "Acme", orderReference: "OUT-3", status: "EXPECTED", reason: "wrong size", closeNote: null, createdAt: "2026-11-09T10:00:00Z" }

  it("lists returns and raises a new one with its lines", async () => {
    get.mockImplementation(async (url: string) => {
      if (url.includes("/clients/c1/items")) return { data: [{ id: "i1", sku: "BOLT" }] }
      if (url.includes("/clients?")) return { data: { content: [{ id: "c1", tradingName: "Acme" }] } }
      if (url.includes("/returns/r1")) return { data: { header: rma, lines: [] } }
      return { data: [rma] }
    })
    post.mockResolvedValueOnce({ data: { id: "r1" } })
    wrap(<WhseReturnsTab />)
    expect(await screen.findByText(/RMA-0001/)).toBeTruthy()
    fireEvent.click(screen.getByText("New return"))
    const dialog = await screen.findByRole("dialog")
    await within(dialog).findByText("Acme") // the client list loads first
    fireEvent.change(within(dialog).getByLabelText("Client"), { target: { value: "c1" } })
    fireEvent.change(within(dialog).getByLabelText("Return reason"), { target: { value: "wrong size" } })
    await within(dialog).findByText("BOLT")
    fireEvent.change(within(dialog).getByLabelText("Item 1"), { target: { value: "i1" } })
    fireEvent.change(within(dialog).getByLabelText("Quantity 1"), { target: { value: "3" } })
    fireEvent.click(within(dialog).getByText("Create return"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/returns", { clientId: "c1", reason: "wrong size", lines: [{ itemId: "i1", expectedQty: 3 }] }))
  })

  it("will not create a return with a missing quantity", async () => {
    get.mockImplementation(async (url: string) => (url.includes("/clients?") ? { data: { content: [{ id: "c1", tradingName: "Acme" }] } } : { data: [] }))
    wrap(<WhseReturnsTab />)
    fireEvent.click(await screen.findByText("New return"))
    const dialog = await screen.findByRole("dialog")
    fireEvent.change(within(dialog).getByLabelText("Return reason"), { target: { value: "wrong size" } })
    expect((within(dialog).getByText("Create return") as HTMLButtonElement).disabled).toBe(true)
  })
})
