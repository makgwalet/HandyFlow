import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { MemoryRouter } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const row = (over: any = {}) => ({ id: "c1", siteId: "s1", siteName: "Centurion Mall", name: "North Gate", description: "Main entrance", active: true, hasNfc: true, hasBle: false,
  siteRequiresSignedQr: true, scans30d: 12, lastScanAt: "2026-10-05T10:00:00Z", activeRoutes: 2, ...over })
const rows = [row(), row({ id: "c2", name: "Server Room", hasNfc: false, scans30d: 0, lastScanAt: null, activeRoutes: 0 })]
const patch = vi.fn(() => Promise.resolve({ data: {} })), post = vi.fn(() => Promise.resolve({ data: {} }))
vi.mock("../../api/client", () => ({ apiClient: {
  get: vi.fn((url: string) => url.includes("qr-image") ? Promise.reject(new Error("no image")) : Promise.resolve({ data: { data: url.includes("/checkpoints?") ? rows : { content: [{ id: "s1", name: "Centurion Mall" }] } } })),
  patch: (...a: any[]) => (patch as any)(...a), post: (...a: any[]) => (post as any)(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
import CheckpointsTab from "./CheckpointsTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter><CheckpointsTab /></MemoryRouter></QueryClientProvider>)
afterEach(() => { cleanup(); patch.mockClear(); post.mockClear(); vi.restoreAllMocks() })

describe("CheckpointsTab", () => {
  it("lists checkpoints by site with methods, scan health and route use", async () => {
    show()
    expect(await screen.findByText("North Gate")).toBeTruthy()
    expect(screen.getByText("12 scans in 30 days")).toBeTruthy()
    expect(screen.getByText("Never scanned")).toBeTruthy()
    expect(screen.getByText("NFC")).toBeTruthy()
    expect(screen.getByText("2 routes")).toBeTruthy()
  })
  it("warns before switching off a checkpoint that routes use, and does nothing if declined", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false)
    show(); await screen.findByText("North Gate")
    fireEvent.click(screen.getByLabelText("Switch off North Gate"))
    expect(confirm.mock.calls[0][0]).toContain("2 active patrol routes")
    expect(patch).not.toHaveBeenCalled()
  })
  it("switches off without sending the NFC or Bluetooth identifiers, so they are kept", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    show(); await screen.findByText("North Gate")
    fireEvent.click(screen.getByLabelText("Switch off North Gate"))
    await waitFor(() => expect(patch).toHaveBeenCalled())
    const body = (patch.mock.calls[0] as any)[1]
    expect(body).toEqual({ name: "North Gate", description: "Main entrance", active: false })
    expect("nfcTagUid" in body).toBe(false)
  })
  it("edit leaves identifiers out unless typed or removed", async () => {
    show(); await screen.findByText("North Gate")
    fireEvent.click(screen.getByLabelText("Edit North Gate"))
    fireEvent.change(screen.getByDisplayValue("North Gate"), { target: { value: "North Gate 2" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect((patch.mock.calls[0] as any)[1]).toEqual({ name: "North Gate 2", description: "Main entrance", active: true })
  })
  it("removing the NFC tag sends a blank", async () => {
    show(); await screen.findByText("North Gate")
    fireEvent.click(screen.getByLabelText("Edit North Gate"))
    fireEvent.click(screen.getByLabelText("Remove the NFC tag"))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect((patch.mock.calls[0] as any)[1].nfcTagUid).toBe("")
  })
  it("asks before rotating a QR code", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(true)
    show(); await screen.findByText("North Gate")
    fireEvent.click(screen.getByLabelText("New QR for North Gate"))
    expect(confirm.mock.calls[0][0]).toContain("stops working immediately")
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/security/sites/s1/checkpoints/c1/qr-secret/regenerate"))
  })
})
