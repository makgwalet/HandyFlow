import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SegmentsPanel from "./SegmentsPanel"
import { CustomFieldInputs } from "./CustomFieldInputs"
import { definitionProblem, displayValue, hasOptions, multiValues, toggleMulti, valueProblem, type CustomField } from "./customFields.logic"
import { allFields, conditionProblem, customDef, describe as say, fieldDef, needsValue, newCondition, segmentProblem, toRequest, type Condition, type SegmentRow } from "./segments.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))

const f = (over: Partial<CustomField>): CustomField => ({ id: "f1", key: "k", label: "Thing", type: "TEXT", required: false, options: [], sortOrder: 0, active: true, ...over })
const svc = f({ key: "svc", label: "Services", type: "MULTI_CHOICE", options: ["Tyres", "Fuel", "Wash"] })
const size = f({ key: "size", label: "Fleet size", type: "NUMBER" })
const renew = f({ key: "renew", label: "Renewal", type: "DATE" })
const tier = f({ key: "tier", label: "Tier", type: "CHOICE", options: ["Gold", "Silver"] })
const vip = f({ key: "vip", label: "VIP", type: "YES_NO" })
const note = f({ key: "note", label: "Note", type: "TEXT" })
const c = (field: string, op: string, value = ""): Condition => ({ field: field as Condition["field"], op, value })

describe("new custom field kinds", () => {
  it("knows which kinds have options", () => {
    expect(hasOptions("CHOICE") && hasOptions("MULTI_CHOICE")).toBe(true)
    expect(hasOptions("EMAIL") || hasOptions("TEXT") || hasOptions("")).toBe(false)
  })
  it("checks the definition of a several-choices field", () => {
    expect(definitionProblem("Services", "MULTI_CHOICE", "A")).toBe("A choice field needs at least two options.")
    expect(definitionProblem("Services", "MULTI_CHOICE", "A, B")).toBeNull()
    expect(definitionProblem("Services", "MULTI_CHOICE", "A|x, B")).toBe("Options cannot contain the | character.")
    expect(definitionProblem("Site", "URL", "")).toBeNull()
    expect(definitionProblem("Mail", "EMAIL", "")).toBeNull()
  })
  it("checks several choices", () => {
    expect(valueProblem(svc, "Tyres|Wash")).toBeNull()
    expect(valueProblem(svc, "Tyres|Oil")).toBe("Services must only include the options.")
    expect(valueProblem(svc, "|")).toBe("Services needs at least one choice.")
    expect(valueProblem({ ...svc, required: true }, "")).toBe("Services is required.")
  })
  it("checks email and web addresses", () => {
    const mail = f({ type: "EMAIL", label: "Mail" }), site = f({ type: "URL", label: "Site" })
    expect(valueProblem(mail, "bob@example.co.za")).toBeNull()
    expect(valueProblem(mail, "bob")).toBe("Mail must be an email address.")
    expect(valueProblem(mail, "bob@x")).toBe("Mail must be an email address.")
    expect(valueProblem(site, "https://acme.example/a")).toBeNull()
    expect(valueProblem(site, "acme.example")).toBe("Site must be a web address starting with http:// or https://.")
    expect(valueProblem(site, "https://a b.co")).toBe("Site must be a web address starting with http:// or https://.")
    expect(valueProblem(site, "javascript:alert(1)")).toBe("Site must be a web address starting with http:// or https://.")
  })
  it("reads and ticks several choices in the order of the options", () => {
    expect(multiValues("Tyres| Wash |")).toEqual(["Tyres", "Wash"])
    expect(multiValues(undefined)).toEqual([])
    expect(toggleMulti(svc, "Wash", "Tyres", true)).toBe("Tyres|Wash")
    expect(toggleMulti(svc, "Tyres|Wash", "Tyres", false)).toBe("Wash")
    expect(displayValue(svc, "Tyres|Wash")).toBe("Tyres, Wash")
  })
  it("draws one tick box per option and stores what is ticked", () => {
    const onChange = vi.fn()
    render(<CustomFieldInputs fields={[svc]} values={{ svc: "Tyres" }} onChange={onChange} />)
    const group = screen.getByRole("group", { name: "Services" })
    expect((within(group).getByLabelText("Services: Tyres") as HTMLInputElement).checked).toBe(true)
    expect((within(group).getByLabelText("Services: Fuel") as HTMLInputElement).checked).toBe(false)
    fireEvent.click(within(group).getByLabelText("Services: Wash"))
    expect(onChange).toHaveBeenCalledWith("svc", "Tyres|Wash")
    cleanup()
  })
})

describe("segment conditions on custom fields", () => {
  const defs = allFields([svc, size, renew, tier, vip, note, f({ key: "old", label: "Old", active: false })])

  it("offers the active custom fields after the built-in ones", () => {
    const ids = defs.map(d => d.id)
    expect(ids.slice(-6)).toEqual(["CF:svc", "CF:size", "CF:renew", "CF:tier", "CF:vip", "CF:note"])
    expect(ids).not.toContain("CF:old")
  })
  it("offers comparisons that fit each kind", () => {
    const ops = (field: CustomField) => customDef(field).ops.map(o => o.id)
    expect(ops(size)).toEqual(["GREATER_THAN", "LESS_THAN", "EQUALS", "IS_SET", "IS_NOT_SET"])
    expect(ops(renew)).toEqual(["BEFORE", "AFTER", "IS_SET", "IS_NOT_SET"])
    expect(ops(tier)).toEqual(["IS", "IS_NOT", "IS_SET", "IS_NOT_SET"])
    expect(ops(svc)).toEqual(["HAS", "NOT_HAS", "IS_SET", "IS_NOT_SET"])
    expect(ops(vip)).toEqual(["IS", "IS_SET", "IS_NOT_SET"])
    expect(ops(note)).toEqual(["CONTAINS", "IS", "IS_SET", "IS_NOT_SET"])
    expect(customDef(vip).choices).toEqual([{ id: "yes", label: "Yes" }, { id: "no", label: "No" }])
  })
  it("starts a condition on a custom field with its first comparison", () => {
    expect(newCondition("CF:size", defs)).toEqual({ field: "CF:size", op: "GREATER_THAN", value: "" })
    expect(needsValue(c("CF:size", "IS_SET"), defs)).toBe(false)
    expect(needsValue(c("CF:size", "EQUALS"), defs)).toBe(true)
    expect(needsValue(c("EMAIL", "IS_SET"))).toBe(false)
  })
  it("checks the value for each kind", () => {
    expect(conditionProblem(c("CF:size", "GREATER_THAN", ""), defs)).toBe("Enter a value for Fleet size.")
    expect(conditionProblem(c("CF:size", "GREATER_THAN", "abc"), defs)).toBe("Fleet size must be compared with a number.")
    expect(conditionProblem(c("CF:size", "GREATER_THAN", "1 000"), defs)).toBeNull()
    expect(conditionProblem(c("CF:size", "IS_SET"), defs)).toBeNull()
    expect(conditionProblem(c("CF:renew", "BEFORE", ""), defs)).toBe("Choose a date for Renewal.")
    expect(conditionProblem(c("CF:renew", "BEFORE", "2026-02-31"), defs)).toBe("Use a real date.")
    expect(conditionProblem(c("CF:renew", "BEFORE", "2026-12-01"), defs)).toBeNull()
    expect(conditionProblem(c("CF:tier", "IS", ""), defs)).toBe("Choose a value for Tier.")
    expect(conditionProblem(c("CF:tier", "IS", "Gold"), defs)).toBeNull()
    expect(conditionProblem(c("CF:note", "CONTAINS", "x".repeat(501)), defs)).toBe("The value can be at most 500 characters.")
  })
  it("refuses a condition on a field that has been switched off", () => {
    expect(conditionProblem(c("CF:old", "IS", "a"), defs)).toBe("That custom field is no longer in use. Remove this condition.")
    expect(segmentProblem("x", [c("CF:old", "IS", "a")], defs)).toBe("That custom field is no longer in use. Remove this condition.")
    expect(fieldDef("CF:old", defs).label).toBe("A removed field")
  })
  it("sends only what each condition uses", () => {
    expect(toRequest(" Big ", [c("CF:size", "GREATER_THAN", "1 000"), c("CF:vip", "IS_SET", "junk"), c("CF:note", "CONTAINS", " ab ")], defs).conditions).toEqual([
      { field: "CF:size", op: "GREATER_THAN", value: "1000" }, { field: "CF:vip", op: "IS_SET", value: "" }, { field: "CF:note", op: "CONTAINS", value: "ab" }])
  })
  it("describes them in words", () => {
    expect(say(c("CF:size", "GREATER_THAN", "20"), {}, defs)).toBe("Fleet size is more than 20")
    expect(say(c("CF:tier", "IS_NOT", "Gold"), {}, defs)).toBe("Tier is not Gold")
    expect(say(c("CF:svc", "HAS", "Fuel"), {}, defs)).toBe("Services includes Fuel")
    expect(say(c("CF:vip", "IS", "yes"), {}, defs)).toBe("VIP is Yes")
    expect(say(c("CF:note", "IS_NOT_SET"), {}, defs)).toBe("Note is empty")
    expect(say(c("CF:old", "IS", "a"), {}, defs)).toBe("A removed field is a")
  })
})

describe("SegmentsPanel with custom fields", () => {
  const rows: SegmentRow[] = [{ id: "s1", name: "Big fleets", conditions: [c("CF:size", "GREATER_THAN", "20")], count: 4 }]
  beforeEach(() => {
    perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"])
    for (const m of [get, post, put, del]) { m.mockReset() }
    get.mockImplementation(async (url: string) => url.includes("custom-fields") ? { data: { data: [size, tier, f({ key: "old", label: "Old", active: false })] } } : url.includes("identity/users") ? { data: { data: { content: [] } } } : { data: { data: rows } })
    post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
  })
  afterEach(cleanup)
  const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SegmentsPanel /></QueryClientProvider>)

  it("describes a saved condition with the field's own name", async () => {
    mount()
    const s = await screen.findByLabelText("Big fleets")
    await waitFor(() => expect(within(s).getByText("Fleet size is more than 20")).toBeTruthy())
  })
  it("lists custom fields in their own group and saves a condition on one", async () => {
    mount(); fireEvent.click(await screen.findByText("New segment"))
    const group = await waitFor(() => { const g = screen.getByLabelText("Field for condition 1").querySelector("optgroup"); if (!g) throw new Error("not yet"); return g })
    expect(group.label).toBe("Custom fields")
    expect(Array.from(group.querySelectorAll("option")).map(o => o.textContent)).toEqual(["Fleet size", "Tier"])
    fireEvent.change(screen.getByLabelText("Segment name"), { target: { value: "Big" } })
    fireEvent.change(screen.getByLabelText("Field for condition 1"), { target: { value: "CF:size" } })
    fireEvent.change(screen.getByLabelText("Value for condition 1"), { target: { value: "20" } })
    fireEvent.click(screen.getByText("Save segment"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/segments", { name: "Big", conditions: [{ field: "CF:size", op: "GREATER_THAN", value: "20" }] }))
  })
  it("hides the value box for filled in / empty", async () => {
    mount(); fireEvent.click(await screen.findByText("New segment"))
    await waitFor(() => expect(screen.getByLabelText("Field for condition 1").querySelector("optgroup")).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Field for condition 1"), { target: { value: "CF:size" } })
    expect(screen.getByLabelText("Value for condition 1")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Comparison for condition 1"), { target: { value: "IS_SET" } })
    expect(screen.queryByLabelText("Value for condition 1")).toBeNull()
  })
})
