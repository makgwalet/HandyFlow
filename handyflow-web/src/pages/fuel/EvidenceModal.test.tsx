import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EvidenceModal from "./EvidenceModal"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []; let me = "bob"
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: any) => unknown) => sel({ user: { userId: me } }) }))

const ev = (o: any = {}) => ({ id: "e1", deliveryId: "d1", kind: "PHOTO", fileRef: "pod.jpg", capturedAt: "2026-11-11T10:00:00Z", capturedBy: "alice", status: "ACTIVE", locked: false, ...o })
function mount(canAdd = true) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><EvidenceModal deliveryId="d1" label="DR-001" canAdd={canAdd} onClose={() => {}} /></QueryClientProvider>)
}

describe("EvidenceModal", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); perms = ["FUEL_MANAGE", "FUEL_EVIDENCE_VERIFY"]; me = "bob"; post.mockResolvedValue({ data: {} }); get.mockResolvedValue({ data: { data: [ev()] } }) })
  afterEach(cleanup)

  it("warns when there is no verified proof", async () => {
    mount()
    expect(await screen.findByText("No verified proof yet.")).toBeTruthy()
  })
  it("lets someone other than the capturer verify", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Verify Photo"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/evidence/e1/verify"))
  })
  it("does not offer the capturer Verify", async () => {
    me = "alice"
    mount()
    await screen.findByText("pod.jpg")
    expect(screen.queryByLabelText("Verify Photo")).toBeNull()
  })
  it("shows locked evidence with no Verify or Replace", async () => {
    get.mockResolvedValue({ data: { data: [ev({ locked: true })] } })
    mount()
    expect(await screen.findByText("Verified and locked")).toBeTruthy()
    expect(screen.queryByLabelText("Replace Photo")).toBeNull()
    expect(screen.queryByText("No verified proof yet.")).toBeNull()
  })
  it("adds GPS evidence only with valid coordinates", async () => {
    mount()
    fireEvent.click(await screen.findByText("Add evidence"))
    fireEvent.change(screen.getByLabelText("Type"), { target: { value: "GPS" } })
    fireEvent.click(screen.getByText("Save evidence"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/latitude/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Latitude"), { target: { value: "-26.2" } })
    fireEvent.change(screen.getByLabelText("Longitude"), { target: { value: "28.04" } })
    fireEvent.click(screen.getByText("Save evidence"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/deliveries/d1/evidence", expect.objectContaining({ kind: "GPS", latitude: -26.2, longitude: 28.04 })))
  })
  it("replaces evidence with a reason and the corrected record", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Replace Photo"))
    fireEvent.change(screen.getByLabelText("File link or reference"), { target: { value: "right.jpg" } })
    const replace = screen.getByText("Replace evidence") as HTMLButtonElement
    expect(replace.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for replacing *"), { target: { value: "Photo of wrong tank" } })
    fireEvent.click(replace)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/evidence/e1/supersede", expect.objectContaining({ reason: "Photo of wrong tank", replacement: expect.objectContaining({ fileRef: "right.jpg" }) })))
  })
  it("hides Add when the caller cannot add", async () => {
    mount(false)
    await screen.findByText("pod.jpg")
    expect(screen.queryByText("Add evidence")).toBeNull()
  })
})
