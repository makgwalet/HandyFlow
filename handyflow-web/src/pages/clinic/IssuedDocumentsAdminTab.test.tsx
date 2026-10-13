import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import IssuedDocumentsAdminTab from "./IssuedDocumentsAdminTab"

const row = (o: any) => ({ id: "d1", number: "SN-2026-000001", type: "SICK_NOTE", typeLabel: "Sick note", patientName: "Thandi Nkosi", issuedAt: "2026-10-13T08:00:00Z", status: "ISSUED", ...o })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><IssuedDocumentsAdminTab /></QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: [row({}), row({ id: "d2", number: "RF-2026-000001", typeLabel: "Referral letter", status: "VOIDED", voidReason: "Wrong patient" })] } }) })
afterEach(cleanup)

describe("IssuedDocumentsAdminTab", () => {
  it("lists documents, and offers Withdraw only on one that stands", async () => {
    show()
    await screen.findByText("SN-2026-000001")
    expect(screen.getByText("Wrong patient")).toBeTruthy()
    expect(screen.getAllByRole("button", { name: /Withdraw/ })).toHaveLength(1)
  })

  it("withdraws with a reason and refreshes", async () => {
    post.mockResolvedValue({ data: {} })
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Withdraw…/ }))
    const go = screen.getByRole("button", { name: "Withdraw document" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Issued to the wrong patient" } })
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/issued-documents/d1/void", { reason: "Issued to the wrong patient" }))
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(1))
  })

  it("passes the filters to the server", async () => {
    show()
    await screen.findByText("SN-2026-000001")
    fireEvent.change(screen.getByLabelText("Type"), { target: { value: "REFERRAL" } })
    await waitFor(() => expect(get.mock.calls.some(c => c[1]?.params?.type === "REFERRAL")).toBe(true))
  })
})
