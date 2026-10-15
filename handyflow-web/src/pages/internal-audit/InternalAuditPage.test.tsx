import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: vi.fn(), put: vi.fn(), delete: vi.fn() } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: any) => sel({ user: { userId: "u1", permissions: ["AUDIT_READ", "AUDIT_MANAGE"] } }) }))
import InternalAuditPage from "./InternalAuditPage"

afterEach(cleanup)
const mount = (path: string) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter initialEntries={[path]}><Routes><Route path="/internal-audit/:section" element={<InternalAuditPage />} /></Routes></MemoryRouter></QueryClientProvider>)
}
beforeEach(() => { vi.clearAllMocks(); get.mockResolvedValue({ data: [] }) })

describe("Internal Audit page", () => {
  it("an empty universe says so", async () => {
    mount("/internal-audit/universe")
    expect(await screen.findByText(/No universe entries yet/)).toBeTruthy()
  })

  it("a failed load is announced and not shown as an empty list", async () => {
    get.mockRejectedValue(new Error("boom"))
    mount("/internal-audit/universe")
    expect((await screen.findByRole("alert")).textContent).toContain("could not be loaded")
  })

  it("the new-entry form has labelled fields", async () => {
    mount("/internal-audit/universe")
    fireEvent.click(await screen.findByText(/New Universe Entry/))
    expect(await screen.findByLabelText(/Name \*/)).toBeTruthy()
    expect(screen.getByLabelText("Process Area")).toBeTruthy()
    expect(screen.getByRole("dialog")).toBeTruthy()
  })
})
