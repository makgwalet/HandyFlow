// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"

const getEmp201s = vi.fn()
const prepareEmp201 = vi.fn()
const setEtiClaim = vi.fn()
const getEmp501 = vi.fn()
const setEmployeeEti = vi.fn()
vi.mock("../../api/payrollBureau.api", () => ({
  payrollBureauApi: {
    getEmp201s: (...a: unknown[]) => getEmp201s(...a),
    prepareEmp201: (...a: unknown[]) => prepareEmp201(...a),
    setEtiClaim: (...a: unknown[]) => setEtiClaim(...a),
    getEmp501: (...a: unknown[]) => getEmp501(...a),
    setEmployeeEti: (...a: unknown[]) => setEmployeeEti(...a),
  },
}))
import { SarsTab, EmployeeEtiModal } from "./SarsFiling"
import { currentTaxYear, monthsOfTaxYear } from "./sarsHelpers"
import type { PayClient, PayEmployee } from "../../types/payrollBureau.types"

const client = { id: "cl1", etiClaimable: false } as PayClient

beforeEach(() => { [getEmp201s, prepareEmp201, setEtiClaim, getEmp501, setEmployeeEti].forEach(f => f.mockReset()) })
afterEach(cleanup)

describe("tax year helpers", () => {
  it("names the tax year by the year it ends in", () => {
    expect(currentTaxYear(new Date(2026, 10, 26))).toBe(2027)
    expect(currentTaxYear(new Date(2027, 1, 28))).toBe(2027)
    expect(currentTaxYear(new Date(2027, 2, 1))).toBe(2028)
  })
  it("lists March to February", () => {
    const m = monthsOfTaxYear(2027)
    expect(m[0]).toBe("2026-03")
    expect(m[11]).toBe("2027-02")
    expect(m).toHaveLength(12)
  })
})

describe("SARS tab", () => {
  it("shows months without a return as not prepared and prepares one", async () => {
    getEmp201s.mockResolvedValue([])
    prepareEmp201.mockResolvedValue({})
    render(<SarsTab client={client} />)
    expect((await screen.findAllByText("Not prepared")).length).toBe(12)
    fireEvent.click(screen.getAllByText("Prepare")[0])
    await waitFor(() => expect(prepareEmp201).toHaveBeenCalledWith("cl1", "2026-03"))
  })

  it("saves the ETI claim flag with its note", async () => {
    getEmp201s.mockResolvedValue([])
    setEtiClaim.mockResolvedValue({})
    render(<SarsTab client={client} />)
    fireEvent.click(await screen.findByRole("checkbox"))
    fireEvent.change(screen.getByLabelText("Note"), { target: { value: "Returns up to date, 26 Nov" } })
    fireEvent.click(screen.getAllByText("Save")[0])
    await waitFor(() => expect(setEtiClaim).toHaveBeenCalledWith("cl1", true, "Returns up to date, 26 Nov"))
  })

  it("shows what must be fixed in the reconciliation", async () => {
    getEmp201s.mockResolvedValue([])
    getEmp501.mockResolvedValue({
      clientId: "cl1", period: "ANNUAL", taxYear: 2027, from: "2026-03-01", to: "2027-02-28", certificateCount: 1, balanced: false, blocked: true,
      differences: [{ what: "PAYE", emp201: 6000, certificates: 5900, difference: 100 }], etiUsed: 0, etiUnusedAtEnd: 0,
      findings: [{ severity: "BLOCK", code: "EMP201_MISSING", message: "No EMP201 prepared for 2026-04" }],
      certificates: [{ employeeId: "e1", employeeName: "Ann Dube", type: "IRP5", income: [], deductions: [], nonTaxable3696: 0, grossTaxable3699: 20000, paye4102: 5900, uif4141: 0, sdl4142: 0, total4149: 5900, blocked: true, findings: [] }],
    })
    render(<SarsTab client={client} />)
    fireEvent.click(await screen.findByText("Build reconciliation"))
    expect(await screen.findByText(/Not ready/)).toBeTruthy()
    expect(screen.getByText(/No EMP201 prepared for 2026-04/)).toBeTruthy()
    expect(screen.getByText("Ann Dube")).toBeTruthy()
  })
})

describe("employee ETI facts", () => {
  const emp = { id: "e1", fullName: "Ann Dube", etiMonthsPrior: 0 } as PayEmployee
  it("rejects months outside 0 to 24 without calling the server", async () => {
    render(<EmployeeEtiModal clientId="cl1" employee={emp} onClose={() => {}} onSaved={() => {}} />)
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "30" } })
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText(/whole number/)).toBeTruthy()
    expect(setEmployeeEti).not.toHaveBeenCalled()
  })
  it("sends the facts", async () => {
    setEmployeeEti.mockResolvedValue({})
    const onSaved = vi.fn()
    render(<EmployeeEtiModal clientId="cl1" employee={emp} onClose={() => {}} onSaved={onSaved} />)
    fireEvent.click(screen.getByLabelText(/Domestic worker/))
    fireEvent.change(screen.getByRole("textbox"), { target: { value: "5" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(setEmployeeEti).toHaveBeenCalledWith("cl1", "e1",
      { idKind: null, domesticWorker: true, connectedPerson: false, belowMinimumWage: false, monthsPrior: 5 }))
    expect(onSaved).toHaveBeenCalled()
  })
})
