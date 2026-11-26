// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"

const getPricing = vi.fn()
const updatePricing = vi.fn()
const addCharge = vi.fn()
const cancelFeeNote = vi.fn()
const getProfitability = vi.fn()
const getCostPerPayslip = vi.fn()
vi.mock("../../api/payrollBureau.api", () => ({
  payrollBureauApi: {
    getPricing: (...a: unknown[]) => getPricing(...a),
    updatePricing: (...a: unknown[]) => updatePricing(...a),
    addCharge: (...a: unknown[]) => addCharge(...a),
    archiveCharge: vi.fn(),
    cancelFeeNote: (...a: unknown[]) => cancelFeeNote(...a),
    getProfitability: (...a: unknown[]) => getProfitability(...a),
    getCostPerPayslip: (...a: unknown[]) => getCostPerPayslip(...a),
    setCostPerPayslip: vi.fn(),
  },
}))
import { PricingSection, CancelFeeNoteModal, ProfitabilityModal } from "./Pricing"
import type { PayClient, PayFeeNote } from "../../types/payrollBureau.types"

const client = { id: "cl1" } as PayClient
beforeEach(() => {
  [getPricing, updatePricing, addCharge, cancelFeeNote, getProfitability, getCostPerPayslip].forEach(f => f.mockReset())
  getPricing.mockResolvedValue({ perEmployeeFee: 50, minimumFee: 300, charges: [{ id: "c1", description: "Setup", amount: 500, billing: "ANNUAL" }] })
  getCostPerPayslip.mockResolvedValue({ costPerPayslip: 12 })
})
afterEach(cleanup)

describe("PricingSection", () => {
  it("shows the fee, the minimum and the extra charges", async () => {
    render(<PricingSection client={client} />)
    expect(await screen.findByDisplayValue("300")).toBeTruthy()
    expect(screen.getByText("Setup")).toBeTruthy()
    expect(screen.getAllByText("Once a year").length).toBeGreaterThan(1)
  })
  it("saves changed pricing as numbers", async () => {
    updatePricing.mockResolvedValue({ perEmployeeFee: 60, minimumFee: 300, charges: [] })
    render(<PricingSection client={client} />)
    const fee = await screen.findByLabelText("Fee per employee")
    fireEvent.change(fee, { target: { value: "60" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(updatePricing).toHaveBeenCalledWith("cl1", { perEmployeeFee: 60, minimumFee: 300 }))
  })
})

describe("CancelFeeNoteModal", () => {
  const note = { id: "f1", invoiceNumber: "PFN00001" } as PayFeeNote
  it("will not cancel without a reason", async () => {
    render(<CancelFeeNoteModal feeNote={note} onClose={() => {}} onCancelled={() => {}} />)
    fireEvent.click(screen.getByRole("button", { name: "Cancel invoice" }))
    expect(await screen.findByText(/Say why/)).toBeTruthy()
    expect(cancelFeeNote).not.toHaveBeenCalled()
  })
  it("sends the reason", async () => {
    cancelFeeNote.mockResolvedValue({})
    const done = vi.fn()
    render(<CancelFeeNoteModal feeNote={note} onClose={() => {}} onCancelled={done} />)
    fireEvent.change(screen.getByLabelText("Cancellation reason"), { target: { value: " Wrong run " } })
    fireEvent.click(screen.getByRole("button", { name: "Cancel invoice" }))
    await waitFor(() => expect(cancelFeeNote).toHaveBeenCalledWith("f1", "Wrong run"))
    await waitFor(() => expect(done).toHaveBeenCalled())
  })
})

describe("ProfitabilityModal", () => {
  it("lists clients with margin and unbilled runs", async () => {
    getProfitability.mockResolvedValue({
      from: "2026-11-01", to: "2026-11-30", costPerPayslip: 12, totalRevenue: 1000, totalCost: 240, totalMargin: 760,
      totalMarginPercent: 76, totalUnbilledRuns: 1, totalUnbilledEstimate: 300,
      clients: [{ clientId: "a", clientName: "Acme", runs: 2, payslips: 20, revenue: 1000, cost: 240, margin: 760, marginPercent: 76, unbilledRuns: 1, unbilledEstimate: 300 }],
    })
    render(<ProfitabilityModal onClose={() => {}} />)
    fireEvent.click(screen.getByText("Show"))
    expect(await screen.findByText("Acme")).toBeTruthy()
    expect(screen.getAllByText("76%").length).toBeGreaterThan(0)
    expect(screen.getByText(/1 run not billed/)).toBeTruthy()
  })
})
