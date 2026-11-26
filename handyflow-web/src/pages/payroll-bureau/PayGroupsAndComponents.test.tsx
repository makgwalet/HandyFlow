// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"

const getPayGroups = vi.fn()
const getPayComponents = vi.fn()
const getRunItems = vi.fn()
const setRunItem = vi.fn()
const nextPayPeriod = vi.fn()
vi.mock("../../api/payrollBureau.api", () => ({
  payrollBureauApi: {
    getPayGroups: (...a: unknown[]) => getPayGroups(...a),
    getPayComponents: (...a: unknown[]) => getPayComponents(...a),
    getRunItems: (...a: unknown[]) => getRunItems(...a),
    setRunItem: (...a: unknown[]) => setRunItem(...a),
    nextPayPeriod: (...a: unknown[]) => nextPayPeriod(...a),
  },
}))
import { PayGroupsTab, ComponentsTab, RunItemsModal, GroupPeriodPicker } from "./PayGroupsAndComponents"
import type { PayClient, PayEmployee, PayRun } from "../../types/payrollBureau.types"

const client = { id: "cl1" } as PayClient
const group = (over: object = {}) => ({ id: "g1", name: "Factory floor", frequency: "WEEKLY", anchorDate: "2026-03-02", payLagDays: 2, status: "ACTIVE", employeeCount: 12, ...over })
const component = (over: object = {}) => ({ id: "c1", code: "OVERTIME", name: "Overtime", role: "TAXABLE_EARNING", sarsCode: "3607", standard: true, status: "ACTIVE", ...over })

beforeEach(() => { [getPayGroups, getPayComponents, getRunItems, setRunItem, nextPayPeriod].forEach(f => f.mockReset()) })
afterEach(cleanup)

describe("pay groups", () => {
  it("lists groups and explains that group amounts are per pay period", async () => {
    getPayGroups.mockResolvedValue([group()])
    render(<PayGroupsTab client={client} />)
    expect(await screen.findByText("Factory floor")).toBeTruthy()
    expect(screen.getByText("Weekly")).toBeTruthy()
    expect(screen.getByText(/per pay period/)).toBeTruthy()
  })

  it("says everyone is monthly when there are no groups", async () => {
    getPayGroups.mockResolvedValue([])
    render(<PayGroupsTab client={client} />)
    expect(await screen.findByText(/Everyone is paid monthly/)).toBeTruthy()
  })

  it("fills the dates of a new run from the group's next period", async () => {
    nextPayPeriod.mockResolvedValue({ periodStart: "2027-03-01", periodEnd: "2027-03-07", payDate: "2027-03-09", taxYear: 2028 })
    const onPick = vi.fn()
    render(<GroupPeriodPicker groups={[group()] as never} value="" onPick={onPick} />)
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "g1" } })
    await waitFor(() => expect(onPick).toHaveBeenCalledWith("g1", { start: "2027-03-01", end: "2027-03-07", payDate: "2027-03-09" }))
  })

  it("offers no group choice when the client has no active group", () => {
    const { container } = render(<GroupPeriodPicker groups={[group({ status: "ARCHIVED" })] as never} value="" onPick={() => {}} />)
    expect(container.innerHTML).toBe("")
  })
})

describe("pay items", () => {
  it("shows the catalogue with how each item is treated", async () => {
    getPayComponents.mockResolvedValue([component(), component({ id: "c2", code: "LOAN_REPAYMENT", name: "Loan repayment", role: "DEDUCTION", sarsCode: null })])
    render(<ComponentsTab />)
    expect(await screen.findByText("Overtime")).toBeTruthy()
    expect(screen.getByText("Taxable earning")).toBeTruthy()
    expect(screen.getByText("Deduction")).toBeTruthy()
  })

  it("lets items be added to a draft run and locks a calculated one", async () => {
    getRunItems.mockResolvedValue([])
    getPayComponents.mockResolvedValue([component()])
    const employees = [{ id: "e1", fullName: "Thandi Nkosi", status: "ACTIVE" }] as PayEmployee[]
    const draft = { id: "r1", payRunNumber: "PR00001", status: "DRAFT" } as PayRun
    const { unmount } = render(<RunItemsModal run={draft} employees={employees} onClose={() => {}} />)
    expect(await screen.findByText("Add")).toBeTruthy()
    unmount()
    render(<RunItemsModal run={{ ...draft, status: "CALCULATED" }} employees={employees} onClose={() => {}} />)
    expect(await screen.findByText(/no longer a draft/)).toBeTruthy()
    expect(screen.queryByText("Add")).toBeNull()
  })
})
