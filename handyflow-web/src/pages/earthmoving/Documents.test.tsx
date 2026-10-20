import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const patch = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), patch: (...a: unknown[]) => patch(...a), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import DocumentsTab from "./DocumentsTab"

afterEach(cleanup)

const ver = (n: number) => ({ id: `v${n}`, versionNo: n, fileName: "policy.pdf", contentType: "application/pdf", sizeBytes: 2048, sha256: "x", scanStatus: "SKIPPED", note: null, uploadedBy: null, uploadedAt: "2026-10-01T08:00:00Z" })
const doc = (over: Record<string, unknown> = {}) => ({
  id: "d1", ownerType: "EARTH_ASSET", ownerId: "a1", category: "MANUAL", title: "Operator manual", description: null, expiresOn: null,
  status: "ACTIVE", archivedReason: null, currentVersion: 1, current: ver(1), createdAt: "2026-10-01T08:00:00Z", createdBy: null, ...over,
})
let docs: unknown[] = []
let expiring: unknown[] = []

const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><DocumentsTab /></QueryClientProvider>)
}
const pickMachine = async () => {
  await waitFor(() => expect(screen.getByRole("option", { name: /D9-001/ })).toBeTruthy())
  fireEvent.change(screen.getByLabelText("Machine"), { target: { value: "a1" } })
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ"]
  docs = [doc()]
  expiring = []
  get.mockImplementation((url: string) => {
    if (url.includes("/documents/expiring")) return Promise.resolve({ data: { data: expiring } })
    if (url.includes("/documents/d1/versions")) return Promise.resolve({ data: { data: [ver(2), ver(1)] } })
    if (url.includes("/documents?")) return Promise.resolve({ data: { data: docs } })
    if (url.includes("/assets")) return Promise.resolve({ data: { data: { content: [{ id: "a1", name: "CAT D9", fleetNumber: "D9-001" }], totalPages: 1 } } })
    if (url.includes("assignable-users")) return Promise.resolve({ data: { data: [{ id: "u9", name: "Thabo Operator" }] } })
    return Promise.resolve({ data: { data: [] } })
  })
  post.mockResolvedValue({ data: { data: doc() } })
  patch.mockResolvedValue({ data: { data: doc() } })
})

describe("Documents", () => {
  it("asks for a machine first, then lists its documents", async () => {
    mount()
    expect(screen.getByText(/Choose a machine to see its documents/)).toBeTruthy()
    await pickMachine()
    await waitFor(() => expect(screen.getByText("Operator manual")).toBeTruthy())
    expect(screen.getByText(/version 1/)).toBeTruthy()
  })

  it("shows documents that are expiring or expired in a warning list", async () => {
    expiring = [doc({ id: "d2", title: "Machine insurance", category: "INSURANCE", expiresOn: "2020-01-01" })]
    mount()
    await waitFor(() => expect(screen.getByLabelText("Expiring documents")).toBeTruthy())
    expect(screen.getByText(/Machine insurance/)).toBeTruthy()
    expect(screen.getAllByText(/expired/).length).toBeGreaterThan(0)
  })

  it("hides the change buttons from someone who can only read", async () => {
    mount()
    await pickMachine()
    await waitFor(() => screen.getByText("Operator manual"))
    expect(screen.queryByText("Add document")).toBeNull()
    expect(screen.queryByText("New version")).toBeNull()
    expect(screen.queryByText("Archive")).toBeNull()
    expect(screen.getByText("Download")).toBeTruthy()
  })

  it("lets a writer change an ordinary document but not insurance", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    docs = [doc(), doc({ id: "d3", title: "Policy", category: "INSURANCE", expiresOn: "2030-01-01" })]
    mount()
    await pickMachine()
    await waitFor(() => screen.getByText("Policy"))
    expect(screen.getAllByText("New version")).toHaveLength(1)
    expect(screen.getByText("Add document")).toBeTruthy()
  })

  it("will not send insurance without an expiry date or a file", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE", "EARTHMOVING_ADMIN"]
    mount()
    await pickMachine()
    fireEvent.click(await screen.findByText("Add document"))
    fireEvent.change(screen.getByLabelText("Kind of document"), { target: { value: "INSURANCE" } })
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Policy 77" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Add document" }).at(-1) as HTMLElement)
    expect(await screen.findByText("Choose a file.")).toBeTruthy()
    const file = new File(["%PDF-1.7"], "policy.pdf", { type: "application/pdf" })
    fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [file] } })
    fireEvent.click(screen.getAllByRole("button", { name: "Add document" }).at(-1) as HTMLElement)
    expect(await screen.findByText("This kind of document needs an expiry date.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("uploads a document as a multipart form", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    await pickMachine()
    fireEvent.click(await screen.findByText("Add document"))
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Front photo" } })
    fireEvent.change(screen.getByLabelText(/^File/), { target: { files: [new File(["x"], "front.png", { type: "image/png" })] } })
    fireEvent.click(screen.getAllByRole("button", { name: "Add document" }).at(-1) as HTMLElement)
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/earthmoving/documents")
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get("ownerKind")).toBe("ASSET")
    expect((body as FormData).get("ownerId")).toBe("a1")
    expect((body as FormData).get("category")).toBe("PHOTO")
    expect(((body as FormData).get("file") as File).name).toBe("front.png")
  })

  it("shows the version history", async () => {
    mount()
    await pickMachine()
    fireEvent.click(await screen.findByText("Versions"))
    await waitFor(() => expect(screen.getByText("1 (current)")).toBeTruthy())
    expect(screen.getByText("2")).toBeTruthy()
  })

  it("needs a reason to archive", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE", "EARTHMOVING_ADMIN"]
    mount()
    await pickMachine()
    fireEvent.click(await screen.findByRole("button", { name: "Archive" }))
    fireEvent.click(screen.getAllByRole("button", { name: "Archive" }).at(-1) as HTMLElement)
    expect(await screen.findByText("Say why it is being archived.")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Policy cancelled" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Archive" }).at(-1) as HTMLElement)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/documents/d1/archive", { reason: "Policy cancelled" }))
  })

  it("lists operators for operator certificates", async () => {
    mount()
    fireEvent.change(screen.getByLabelText("Documents for"), { target: { value: "OPERATOR" } })
    await waitFor(() => expect(screen.getByRole("option", { name: "Thabo Operator" })).toBeTruthy())
  })
})
