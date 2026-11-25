// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ApprovalRulesPage from "./ApprovalRulesPage"
import { emptyForm, formProblem, toBody, when } from "./approvalRuleRules"

const get = vi.fn()
const post = vi.fn()
let admin = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => admin, usePermission: () => admin }))

const rule = (over: object = {}) => ({ id: "r1", name: "Large quotes", minFees: 50000, serviceCode: null, riskRating: null, clientId: null, clientName: null, requiredApprover: "PRACTICE_OWNER", reason: "Large quotes need a second look", status: "PROPOSED", proposedBy: "u1", activatedBy: null, retireNote: null, ...over })
const wrap = (ui: React.ReactNode) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  admin = true; get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("approval-rules") ? [rule(), rule({ id: "r2", name: "Old", status: "RETIRED", retireNote: "Replaced by a new fee rule" })] : [{ id: "c1", name: "Acme" }] } }))
})
afterEach(cleanup)

describe("approval rule wording and checks", () => {
  it("states conditions in plain words", () => {
    expect(when({ minFees: 50000, serviceCode: "CS-X", riskRating: "HIGH", clientId: null, clientName: null })).toContain("risk rating is high")
    expect(when({ minFees: null, serviceCode: null, riskRating: null, clientId: "c1", clientName: "Acme" })).toContain("Acme")
  })
  it("a rule needs a condition, a sane amount and a reason", () => {
    expect(formProblem({ ...emptyForm, name: "Big", reason: "Large quotes need a second look" })).toContain("when the rule applies")
    expect(formProblem({ ...emptyForm, name: "Big", minFees: "-5", reason: "Large quotes need a second look" })).toContain("more than zero")
    expect(formProblem({ ...emptyForm, name: "Big", minFees: "100", reason: "short" })).toContain("reason")
    expect(formProblem({ ...emptyForm, name: "Big", minFees: "100", reason: "Large quotes need a second look" })).toBeNull()
    expect(toBody({ ...emptyForm, name: " Big ", minFees: "100", reason: "Large quotes need a second look" }).minFees).toBe(100)
  })
})

describe("approval rules page", () => {
  it("lists rules, with retired ones faded and no policy supplied", async () => {
    wrap(<ApprovalRulesPage />)
    await waitFor(() => expect(screen.getByText("Large quotes")).toBeTruthy())
    expect(document.body.textContent).toContain("Waiting for a second administrator")
    expect(document.body.textContent).toContain("Retired: Replaced by a new fee rule")
  })
  it("proposing without a condition does not call the server", async () => {
    wrap(<ApprovalRulesPage />)
    fireEvent.click(await screen.findByText("Propose a rule"))
    fireEvent.change(screen.getByLabelText("Rule name"), { target: { value: "Big" } })
    fireEvent.change(screen.getByLabelText("Why"), { target: { value: "Large quotes need a second look" } })
    fireEvent.click(screen.getByText("Propose"))
    await waitFor(() => expect(document.body.textContent).toContain("Say when the rule applies"))
    expect(post).not.toHaveBeenCalled()
  })
  it("proposes a rule with a fee amount", async () => {
    post.mockResolvedValue({ data: { data: rule() } })
    wrap(<ApprovalRulesPage />)
    fireEvent.click(await screen.findByText("Propose a rule"))
    fireEvent.change(screen.getByLabelText("Rule name"), { target: { value: "Big" } })
    fireEvent.change(screen.getByLabelText("Professional fees at or above"), { target: { value: "75000" } })
    fireEvent.change(screen.getByLabelText("Why"), { target: { value: "Large quotes need a second look" } })
    fireEvent.click(screen.getByText("Propose"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/compliance-services/approval-rules", expect.objectContaining({ name: "Big", minFees: 75000, requiredApprover: "COMPLIANCE_MANAGER" })))
  })
  it("retiring needs a reason", async () => {
    wrap(<ApprovalRulesPage />)
    fireEvent.click((await screen.findAllByText("Retire"))[0])
    fireEvent.click(screen.getByText("Retire the rule"))
    await waitFor(() => expect(document.body.textContent).toContain("at least 10 characters"))
    expect(post).not.toHaveBeenCalled()
  })
  it("someone who is not an administrator sees rules but no buttons", async () => {
    admin = false
    wrap(<ApprovalRulesPage />)
    await screen.findByText("Large quotes")
    expect(screen.queryByText("Propose a rule")).toBeNull()
    expect(screen.queryByText("Activate")).toBeNull()
  })
})
