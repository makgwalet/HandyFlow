import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SegmentsPanel from "./SegmentsPanel"
import { UNASSIGNED, conditionProblem, describe as say, newCondition, segmentProblem, toRequest, withField, type Condition, type SegmentRow } from "./segments.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const c = (field: Condition["field"], op: string, value = ""): Condition => ({ field, op, value })
const rows: SegmentRow[] = [{ id: "s1", name: "High value", conditions: [c("DEAL_VALUE", "GREATER_THAN", "100000"), c("OWNER", "IS", "u1")], count: 3 }]
const members = { total: 3, truncated: false, members: [{ id: "m1", name: "Acme", email: "a@acme.co.za", phone: null, customerType: "CUSTOMER", status: "ACTIVE" }] }
beforeEach(() => {
  perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset()
  get.mockImplementation(async (url: string) => url.includes("identity/users") ? { data: { data: { content: [{ id: "u1", firstName: "Thabang", lastName: "M" }] } } } : url.includes("/members") ? { data: { data: members } } : { data: { data: rows } })
  post.mockResolvedValue({ data: { data: { total: 2, truncated: false, members: [{ id: "x", name: "Zed", email: null, phone: null, customerType: null, status: null }] } } }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("segment rules", () => {
  it("starts each field with its first comparison and no value, and resets when the field changes", () => {
    expect(newCondition("TAG")).toEqual({ field: "TAG", op: "HAS", value: "" })
    expect(withField(c("DEAL_VALUE", "LESS_THAN", "5"), "CREATED")).toEqual({ field: "CREATED", op: "WITHIN_DAYS", value: "" })
  })
  it("checks each kind of value", () => {
    expect(conditionProblem(c("TYPE", "IS", ""))).toMatch(/Choose/)
    expect(conditionProblem(c("TAG", "HAS", ""))).toMatch(/tag/)
    expect(conditionProblem(c("DEAL_VALUE", "GREATER_THAN", "abc"))).toMatch(/number/)
    expect(conditionProblem(c("DEAL_VALUE", "GREATER_THAN", "-1"))).toMatch(/negative/)
    expect(conditionProblem(c("DEAL_VALUE", "GREATER_THAN", "1 000"))).toBeNull()
    expect(conditionProblem(c("CREATED", "WITHIN_DAYS", "1.5"))).toMatch(/whole/)
    expect(conditionProblem(c("CREATED", "WITHIN_DAYS", "4000"))).toMatch(/3650/)
    expect(conditionProblem(c("EMAIL", "IS_SET"))).toBeNull()
  })
  it("needs a name and a condition", () => {
    expect(segmentProblem(" ", [c("EMAIL", "IS_SET")])).toMatch(/name/)
    expect(segmentProblem("x", [])).toMatch(/at least one/)
    expect(segmentProblem("x", Array.from({ length: 11 }, () => c("EMAIL", "IS_SET")))).toMatch(/at most 10/)
    expect(segmentProblem("x", [c("EMAIL", "IS_SET")])).toBeNull()
  })
  it("sends trimmed values, none where no value belongs, and spaces removed from money", () => {
    expect(toRequest(" VIPs ", [c("TAG", "HAS", " vip "), c("EMAIL", "IS_SET", "junk"), c("DEAL_VALUE", "GREATER_THAN", "1 000")]))
      .toEqual({ name: "VIPs", conditions: [{ field: "TAG", op: "HAS", value: "vip" }, { field: "EMAIL", op: "IS_SET", value: "" }, { field: "DEAL_VALUE", op: "GREATER_THAN", value: "1000" }] })
  })
  it("describes conditions in words", () => {
    expect(say(c("SOURCE", "IS", "REFERRAL"))).toBe("Lead source is Referral")
    expect(say(c("OWNER", "IS", UNASSIGNED))).toBe("Owner is nobody")
    expect(say(c("OWNER", "IS", "gone"), {})).toBe("Owner is a former colleague")
    expect(say(c("CREATED", "WITHIN_DAYS", "30"))).toBe("Added in the last 30 days")
    expect(say(c("UPDATED", "OLDER_THAN_DAYS", "1"))).toBe("Last changed more than 1 day ago")
    expect(say(c("PHONE", "IS_NOT_SET"))).toBe("Phone is empty")
  })
})

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><SegmentsPanel /></QueryClientProvider>)
}

describe("SegmentsPanel", () => {
  it("lists segments with their count and a sentence for the conditions", async () => {
    mount()
    const s = await screen.findByLabelText("High value")
    expect(within(s).getByText("3 customers")).toBeTruthy()
    await waitFor(() => expect(within(s).getByText(/Owner is Thabang M/)).toBeTruthy())
    expect(within(s).getByText(/Deal value \(R\) is more than R 100/)).toBeTruthy()
  })
  it("shows who is in a segment", async () => {
    mount()
    fireEvent.click(within(await screen.findByLabelText("High value")).getByText("Show customers"))
    expect(await screen.findByText("a@acme.co.za")).toBeTruthy()
  })
  it("saves a new segment with its conditions", async () => {
    mount(); fireEvent.click(await screen.findByText("New segment"))
    fireEvent.change(screen.getByLabelText("Segment name"), { target: { value: "Referrals" } })
    fireEvent.change(screen.getByLabelText("Field for condition 1"), { target: { value: "SOURCE" } })
    fireEvent.change(screen.getByLabelText("Value for condition 1"), { target: { value: "REFERRAL" } })
    fireEvent.click(screen.getByText("Save segment"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/segments", { name: "Referrals", conditions: [{ field: "SOURCE", op: "IS", value: "REFERRAL" }] }))
  })
  it("will not save without a value, and says why", async () => {
    mount(); fireEvent.click(await screen.findByText("New segment"))
    fireEvent.change(screen.getByLabelText("Segment name"), { target: { value: "Empty" } })
    fireEvent.click(screen.getByText("Save segment"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Choose a value/)
    expect(post).not.toHaveBeenCalled()
  })
  it("previews who would match", async () => {
    mount(); fireEvent.click(await screen.findByText("New segment"))
    fireEvent.change(screen.getByLabelText("Field for condition 1"), { target: { value: "EMAIL" } })
    fireEvent.click(screen.getByText("Check who matches"))
    expect((await screen.findByRole("status")).textContent).toMatch(/2 customers.*Zed/)
  })
  it("edits and deletes after confirming", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    mount(); const s = await screen.findByLabelText("High value")
    fireEvent.click(within(s).getByText("Edit"))
    fireEvent.change(screen.getByLabelText("Segment name"), { target: { value: "Big deals" } })
    fireEvent.click(screen.getByText("Save changes"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/segments/s1", expect.objectContaining({ name: "Big deals" })))
    fireEvent.click(within(await screen.findByLabelText("High value")).getByText("Delete"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/segments/s1"))
  })
  it("hides the buttons that change things from someone who can only read", async () => {
    perms = new Set(["CUSTOMER_READ"]); mount(); await screen.findByLabelText("High value")
    expect(screen.queryByText("New segment")).toBeNull(); expect(screen.queryByText("Edit")).toBeNull(); expect(screen.queryByText("Delete")).toBeNull()
  })
})
