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

  it("uploads a photo as a file and refreshes", async () => {
    mount()
    fireEvent.click(await screen.findByText("Upload photo or file"))
    fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [new File(["x"], "pod.jpg", { type: "image/jpeg" })] } })
    fireEvent.change(screen.getByLabelText(/^Note/), { target: { value: "at the gate" } })
    fireEvent.click(screen.getByText("Upload"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [path, body] = post.mock.calls[0]
    expect(path).toBe("/api/v1/fuel/deliveries/d1/evidence/file")
    expect((body as FormData).get("kind")).toBe("PHOTO"); expect((body as FormData).get("note")).toBe("at the gate")
    expect(((body as FormData).get("file") as File).name).toBe("pod.jpg")
  })
  it("will not upload with no file or the wrong type", async () => {
    mount()
    fireEvent.click(await screen.findByText("Upload photo or file"))
    fireEvent.click(screen.getByText("Upload"))
    expect(screen.getByRole("alert").textContent).toMatch(/Choose a file/)
    fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [new File(["x"], "a.pdf", { type: "application/pdf" })] } })
    fireEvent.click(screen.getByText("Upload"))
    expect(screen.getByRole("alert").textContent).toMatch(/JPEG, PNG or WebP/); expect(post).not.toHaveBeenCalled()
  })
  it("offers View only for a stored file and opens it from a signed-in fetch", async () => {
    get.mockImplementation(async (u: string) => u.endsWith("/file") ? { data: new Blob(["img"]) } : { data: { data: [ev({ fileStored: true, fileRef: "pod.jpg", fileSizeBytes: 2048 }), ev({ id: "e2", kind: "DOCUMENT", fileRef: "typed-ref" })] } })
    const open = vi.spyOn(window, "open").mockReturnValue(null); (URL as any).createObjectURL = vi.fn(() => "blob:x"); (URL as any).revokeObjectURL = vi.fn()
    mount()
    await screen.findByText(/2 KB/)
    expect(screen.getAllByText("View")).toHaveLength(1)
    fireEvent.click(screen.getByLabelText("View Photo"))
    await waitFor(() => expect(open).toHaveBeenCalledWith("blob:x", "_blank", "noopener"))
    expect(get).toHaveBeenCalledWith("/api/v1/fuel/evidence/e1/file", { responseType: "blob" })
    open.mockRestore()
  })
  it("says so when a file cannot be opened", async () => {
    get.mockImplementation(async (u: string) => { if (u.endsWith("/file")) throw new Error("x"); return { data: { data: [ev({ fileStored: true })] } } })
    mount()
    fireEvent.click(await screen.findByLabelText("View Photo"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Could not open the file/)
  })
})

