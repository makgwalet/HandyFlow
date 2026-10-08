import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
const downloadPdf = vi.fn()
vi.mock("./patientFile.shared", async () => ({ ...(await vi.importActual<any>("./patientFile.shared")), downloadPdf: (...a: any[]) => downloadPdf(...a) }))
import PatientAccountTab from "./PatientAccountTab"

const account: any = { patientId: "p1", totalCharged: 800, schemeShare: 400, patientShare: 400, paidByPatient: 150, balanceOwing: 250, schemeOutstanding: 400, visitsNotBilled: 1,
  visits: [
    { consultationId: "c1", claimId: "k1", visitDate: "2026-10-07T08:00:00Z", chiefComplaint: "Cough", claimStatus: "ACCEPTED", schemeName: "Discovery", charged: 500, schemePortion: 400, patientPortion: 100, schemeOutstanding: 400, paidAgainstVisit: 0 },
    { consultationId: "c2", claimId: null, visitDate: "2026-10-08T08:00:00Z", chiefComplaint: "Rash", claimStatus: "NOT_BILLED", charged: 350, paidAgainstVisit: 0 }],
  payments: [{ id: "pay1", claimId: null, paidAt: "2026-10-08T09:00:00Z", method: "CASH", amount: 150, reference: "R-1" }] }

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PatientAccountTab patientId="p1" /></QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { get.mockReset(); post.mockReset(); downloadPdf.mockReset(); for (const k of Object.keys(perms)) delete perms[k]
  get.mockResolvedValue({ data: { data: account } }) })

describe("patient account", () => {
  it("shows the totals, each visit with its claim state, and the payments", async () => {
    show()
    expect(await screen.findByText("Scheme accepted")).toBeTruthy()
    expect(screen.getByText("Not billed yet")).toBeTruthy()
    expect(screen.getByText("Discovery")).toBeTruthy()
    expect(screen.getByText((_, el) => el?.tagName === "STRONG" && /150/.test(el.textContent ?? ""))).toBeTruthy()
    expect(screen.getByText(/1 visit has not been billed yet/)).toBeTruthy()
    expect(screen.queryByText(/Add item/i)).toBeNull()
  })
  it("downloads the statement and a visit's invoice", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Statement/ }))
    expect(downloadPdf).toHaveBeenCalledWith("/api/v1/clinic/billing/patients/p1/statement-pdf", "statement-p1.pdf")
    fireEvent.click(screen.getByRole("button", { name: "Invoice" }))
    expect(downloadPdf.mock.calls[1][0]).toBe("/api/v1/clinic/billing/claims/k1/patient-invoice-pdf")
  })
  it("hides Record payment without the permission", async () => {
    show(); await screen.findByText("Scheme accepted")
    expect(screen.queryByRole("button", { name: /Record payment/ })).toBeNull()
  })
  it("records a payment for this patient and reloads the account", async () => {
    perms["CLINIC_PAYMENT_CREATE"] = true
    post.mockResolvedValue({ data: {} })
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Record payment/ }))
    const save = screen.getByRole("button", { name: "Save payment" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "100" } })
    fireEvent.change(screen.getByLabelText("Method"), { target: { value: "EFT" } })
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/billing/payments", { patientId: "p1", method: "EFT", amount: 100, reference: undefined }))
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(1))
  })
  it("says so when the account cannot be loaded", async () => {
    get.mockRejectedValue(new Error("x")); show()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})
