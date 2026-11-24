// @vitest-environment jsdom
import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const period = { id: "p1", name: "November", periodType: "MONTHLY", periodStart: "2026-11-01", periodEnd: "2026-11-30", status: "APPROVED", totalHours: 20, totalAmountCents: 40000, totalAmountZar: 400, lineItemCount: 3, approvedBy: null, approvedAt: null, exportedAt: null, exportFormat: null, createdAt: "2026-11-30T10:00:00Z" }
const line = (id: string, lineType: string) => ({ id, guardId: "g1", lineType, shiftStartAt: "2026-11-22T04:00:00Z", shiftEndAt: "2026-11-22T14:00:00Z", hoursWorked: 6, overtimeHours: 0, hourlyRateCents: 10000, overtimeRateCents: 15000, grossAmountCents: 90000 })
const get = vi.fn((url: string) => {
  if (url.includes("/periods/p1/lines")) return Promise.resolve({ data: { data: [line("a", "REGULAR"), line("b", "SUNDAY"), line("c", "PUBLIC_HOLIDAY")] } })
  if (url.includes("/periods")) return Promise.resolve({ data: { data: { content: [period] } } })
  if (url.includes("/premiums/holidays")) return Promise.resolve({ data: { data: [] } })
  if (url.includes("/premiums")) return Promise.resolve({ data: { data: { enabled: true, sundayMultiplier: 1.5, holidayMultiplier: 2 } } })
  return Promise.resolve({ data: { data: [] } })
})
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string) => get(u), put: vi.fn(), post: vi.fn(), delete: vi.fn() } }))
let manage = true
vi.mock("./useSecurityAccess", () => ({ useSecurityAccess: () => ({ payrollRead: true, payrollManage: manage, payrollApprove: false, payrollPay: false }) }))
import PayrollTab from "./PayrollTab"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PayrollTab /></QueryClientProvider>)
afterEach(() => { cleanup(); get.mockClear(); manage = true })

describe("Payroll with Sunday and public-holiday pay", () => {
  it("names Sunday and public-holiday lines on a period", async () => {
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Lines/ }))
    expect(await screen.findByText("Sunday")).toBeTruthy()
    expect(screen.getByText("Public holiday")).toBeTruthy()
    expect(screen.getByText("Regular")).toBeTruthy()
  })

  it("opens the settings panel from the header and comes back", async () => {
    show()
    fireEvent.click(await screen.findByText("Sunday & holiday pay"))
    expect(await screen.findByTestId("pay-premium-panel")).toBeTruthy()
    await waitFor(() => expect((screen.getByLabelText("Pay Sunday and public-holiday rates") as HTMLInputElement).checked).toBe(true))
    fireEvent.click(screen.getByText("← Back to periods"))
    expect(await screen.findByText("November")).toBeTruthy()
    expect(screen.queryByTestId("pay-premium-panel")).toBeNull()
  })

  it("is read only for someone without payroll manage", async () => {
    manage = false
    show()
    fireEvent.click(await screen.findByText("Sunday & holiday pay"))
    await screen.findByTestId("pay-premium-panel")
    expect(screen.queryByText("Save")).toBeNull()
  })
})
