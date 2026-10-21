import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DuplicatesPanel from "./DuplicatesPanel"
import { fieldList, mergeBody, mergeSentence, movesList, resultText, sameRecord, takeBody, valueText, type MergeConflict, type MergeMoves, type MergePreview } from "./merge.logic"
import type { Duplicates } from "./duplicates.logic"

const get = vi.fn(), post = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })

const mm = (id: string, name: string, created: string, o: object = {}) => ({ id, name, email: null, phone: null, taxNumber: null, customerType: "CUSTOMER", createdAt: created, ...o })
const dups: Duplicates = { groups: [{ customers: [mm("2", "Tau Mining (Pty) Ltd", "2026-05-01T08:00:00Z"), mm("1", "Tau Mining Ltd", "2026-01-01T08:00:00Z")], reasons: ["Same name"] }], scanned: 40, truncated: false }
const none: MergeMoves = { contacts: 0, tasks: 0, communications: 0, opportunities: 0, campaigns: 0, branches: 0, activities: 0 }
const side = (id: string, name: string) => ({ id, name, email: null, phone: null, customerType: "CUSTOMER" as const, createdAt: "2026-01-01T08:00:00Z" })
const conflict = (field: string, label: string, keep: string, other: string): MergeConflict => ({ field, label, keep, other })
const preview = (over: Partial<MergePreview> = {}): MergePreview => ({
  keep: side("1", "Tau Mining Ltd"), merge: side("2", "Tau Mining (Pty) Ltd"), conflicts: [conflict("owner", "Owner", "u1", "u2"), conflict("dealValue", "Deal value", "1000", "2000")],
  moves: { ...none, contacts: 2, tasks: 1, opportunities: 1 }, willFill: ["email", "custom.region"], blockers: [], canMerge: true, ...over,
})

beforeEach(() => {
  perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"])
  for (const m of [get, post, del]) { m.mockReset() }
  del.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

function serve(p: MergePreview = preview()) {
  get.mockImplementation((url: string) => {
    if (url.includes("customer-merge/preview")) return ok(p)
    if (url.includes("identity/users")) return ok({ content: [{ id: "u1", firstName: "Olive", lastName: "Other" }, { id: "u2", firstName: "Sam", lastName: "Seller" }] })
    if (url.includes("custom-fields")) return ok([{ id: "f1", key: "region", label: "Region" }])
    return ok(dups)
  })
}
const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><DuplicatesPanel /></QueryClientProvider>)
async function openReview() {
  mount()
  fireEvent.click(await screen.findByRole("button", { name: "Merge…" }))
  return await screen.findByRole("group", { name: "Merge review" })
}

describe("merge logic", () => {
  const cs = [conflict("owner", "Owner", "u1", "u2"), conflict("dealValue", "Deal value", "1", "2"), conflict("phone", "Phone", "1", "2")]
  it("sends only the fields where the other's value was chosen", () => {
    expect(takeBody(cs, {})).toEqual([])
    expect(takeBody(cs, { owner: "other", dealValue: "keep", phone: "other" })).toEqual(["owner", "phone"])
    expect(mergeBody("a", "b", cs, { owner: "other" })).toEqual({ keepId: "a", mergeId: "b", take: ["owner"] })
  })
  it("names fields in a list", () => {
    expect(fieldList([])).toBe("")
    expect(fieldList(["email"])).toBe("email")
    expect(fieldList(["email", "taxNumber", "custom.region"], { region: "Region" })).toBe("email, tax number and Region")
    expect(fieldList(["custom.zzz"])).toBe("zzz")
  })
  it("reads values for people", () => {
    expect(valueText("owner", "u1", { u1: "Olive Other" })).toBe("Olive Other")
    expect(valueText("owner", "gone", {})).toBe("A former colleague")
    expect(valueText("source", "REFERRAL")).toBe("Referral")
    expect(valueText("dealValue", "2000")).toMatch(/^R\s?2\D?000/)
    expect(valueText("custom.vip", "yes")).toBe("Yes")
    expect(valueText("custom.svc", "Tyres|Wash")).toBe("Tyres, Wash")
    expect(valueText("name", "Acme")).toBe("Acme")
  })
  it("lists only what will move", () => {
    expect(movesList(none)).toEqual([])
    expect(movesList({ ...none, contacts: 1, tasks: 2, communications: 1, opportunities: 1, campaigns: 3, branches: 1, activities: 9 }))
      .toEqual(["1 contact person", "2 tasks", "1 logged message", "1 opportunity", "3 campaign places", "1 branch", "9 timeline entries"])
  })
  it("words the sentences", () => {
    expect(mergeSentence(preview())).toBe("Tau Mining (Pty) Ltd will be merged into Tau Mining Ltd and removed. Tau Mining Ltd stays.")
    expect(resultText({ id: "1", name: "Tau", filled: ["email"], moved: none, consentNote: null })).toBe("Merged into Tau. It gained email.")
    expect(resultText({ id: "1", name: "Tau", filled: [], moved: none, consentNote: null })).toBe("Merged into Tau.")
    expect(sameRecord("a", "a") && sameRecord("", "b") && !sameRecord("a", "b")).toBe(true)
  })
})

describe("merging from the duplicates tab", () => {
  it("offers Merge only to people who can edit", async () => {
    perms = new Set(["CUSTOMER_READ"])
    serve(); mount()
    await screen.findByText("Same name")
    expect(screen.queryByRole("button", { name: "Merge…" })).toBeNull()
  })
  it("keeps the oldest record by default and previews the merge", async () => {
    serve()
    const review = await openReview()
    expect((screen.getByLabelText("Record to keep") as HTMLSelectElement).value).toBe("1")
    expect((screen.getByLabelText("Record to merge in") as HTMLSelectElement).value).toBe("2")
    expect(get).toHaveBeenCalledWith("/api/v1/crm/customer-merge/preview", { params: { keepId: "1", mergeId: "2" } })
    expect(within(review).getByText("Tau Mining (Pty) Ltd will be merged into Tau Mining Ltd and removed. Tau Mining Ltd stays.")).toBeTruthy()
    expect(within(review).getByText(/Moved onto Tau Mining Ltd: 2 contact people, 1 task, 1 opportunity\./)).toBeTruthy()
    await waitFor(() => expect(within(review).getByText(/will also gain email and Region from/)).toBeTruthy())
    expect(within(review).getByRole("radiogroup", { name: "Owner" })).toBeTruthy()
  })
  it("shows owners by name once they load", async () => {
    serve()
    const review = await openReview()
    await waitFor(() => expect(within(review).getByText("Olive Other")).toBeTruthy())
    expect(within(review).getByText("Sam Seller")).toBeTruthy()
  })
  it("asks twice, then merges with the chosen fields", async () => {
    serve()
    post.mockResolvedValue({ data: { data: { id: "1", name: "Tau Mining Ltd", filled: ["email"], moved: none, consentNote: null } } })
    const review = await openReview()
    fireEvent.click(within(review).getByLabelText("Sam Seller"))
    fireEvent.click(within(review).getByRole("button", { name: "Merge" }))
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(within(review).getByRole("button", { name: "Yes, merge Tau Mining (Pty) Ltd into Tau Mining Ltd" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customer-merge", { keepId: "1", mergeId: "2", take: ["owner"] }))
    expect(await screen.findByText("Merged into Tau Mining Ltd. It gained email.")).toBeTruthy()
  })
  it("tells the person when consent was not moved", async () => {
    serve()
    post.mockResolvedValue({ data: { data: { id: "1", name: "Tau", filled: [], moved: none, consentNote: "The kept record already had its own consent, so the removed record's consent was not moved. Check the kept record's consent is right." } } })
    const review = await openReview()
    fireEvent.click(within(review).getByRole("button", { name: "Merge" }))
    fireEvent.click(within(review).getByRole("button", { name: /^Yes, merge/ }))
    expect(await screen.findByText(/consent was not moved/)).toBeTruthy()
  })
  it("will not merge when the server says something blocks it", async () => {
    serve(preview({ blockers: ["'Tau Mining (Pty) Ltd' has invoices (3) linked to it. Keep that record and merge the other one into it instead"], canMerge: false }))
    const review = await openReview()
    expect(within(review).getByRole("alert").textContent).toContain("has invoices (3) linked to it")
    expect((within(review).getByRole("button", { name: "Merge" }) as HTMLButtonElement).disabled).toBe(true)
  })
  it("swaps which record is kept", async () => {
    serve()
    const review = await openReview()
    fireEvent.click(within(review).getByRole("button", { name: "Keep Tau Mining (Pty) Ltd instead" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/crm/customer-merge/preview", { params: { keepId: "2", mergeId: "1" } }))
    expect((screen.getByLabelText("Record to keep") as HTMLSelectElement).value).toBe("2")
  })
  it("shows the server's reason when the merge fails and changes nothing", async () => {
    serve()
    post.mockRejectedValue({ response: { data: { message: "Cannot merge: something changed." } } })
    const review = await openReview()
    fireEvent.click(within(review).getByRole("button", { name: "Merge" }))
    fireEvent.click(within(review).getByRole("button", { name: /^Yes, merge/ }))
    expect((await within(review).findByText("Cannot merge: something changed.")).getAttribute("role")).toBe("alert")
    expect(within(review).getByRole("button", { name: "Merge" })).toBeTruthy()
  })
  it("will not review a record merged into itself", async () => {
    serve()
    await openReview()
    fireEvent.change(screen.getByLabelText("Record to merge in"), { target: { value: "1" } })
    expect(await screen.findByText("Choose two different records.")).toBeTruthy()
    expect(screen.queryByRole("group", { name: "Merge review" })).toBeNull()
  })
})
