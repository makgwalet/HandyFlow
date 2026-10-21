import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import CustomFieldsAdmin from "./CustomFieldsAdmin"
import { CustomFieldInputs } from "./CustomFieldInputs"
import { activeFields, definitionProblem, displayValue, firstValueProblem, parseOptions, typeLabel, valueProblem, valuesBody, type CustomField } from "./customFields.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const fld = (key: string, type: CustomField["type"], o: Partial<CustomField> = {}): CustomField => ({ id: `id-${key}`, key, label: key.toUpperCase(), type, required: false, options: [], sortOrder: 0, active: true, ...o })
const fields = [fld("size", "NUMBER", { required: true }), fld("since", "DATE"), fld("region", "CHOICE", { options: ["North", "South"] }), fld("vip", "YES_NO"), fld("note", "TEXT", { active: false })]
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); get.mockReset(); post.mockReset(); put.mockReset(); post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("custom field rules", () => {
  it("cleans option lists and checks definitions", () => {
    expect(parseOptions("North, South, , north\nEast")).toEqual(["North", "South", "East"])
    expect(definitionProblem("", "TEXT", "")).toMatch(/name/); expect(definitionProblem("X", "", "")).toMatch(/kind/); expect(definitionProblem("X", "CHOICE", "a")).toMatch(/two options/)
    expect(definitionProblem("X", "CHOICE", "a, b")).toBeNull(); expect(definitionProblem("x".repeat(61), "TEXT", "")).toMatch(/60/); expect(typeLabel("YES_NO")).toBe("Yes / No")
  })
  it("checks typed values like the server does", () => {
    const [size, since, region, vip] = fields
    expect(valueProblem(size, "")).toBe("SIZE is required."); expect(valueProblem(size, "abc")).toMatch(/number/); expect(valueProblem(size, "12 000.5")).toBeNull()
    expect(valueProblem(since, "2026-02-30")).toMatch(/date/); expect(valueProblem(since, "2026-02-28")).toBeNull(); expect(valueProblem(since, "")).toBeNull()
    expect(valueProblem(region, "East")).toMatch(/options/); expect(valueProblem(region, "North")).toBeNull(); expect(valueProblem(vip, "maybe")).toMatch(/yes or no/)
    expect(valueProblem(fld("t", "TEXT"), "x".repeat(501))).toMatch(/500/)
    expect(firstValueProblem(fields, {})).toBe("SIZE is required."); expect(firstValueProblem(fields, { size: "1" })).toBeNull()
  })
  it("sends only active fields with a value, and shows stored values in words", () => {
    expect(valuesBody(fields, { size: " 5 ", since: "", note: "ignored", region: "South" })).toEqual({ size: "5", region: "South" })
    expect(displayValue(fields[3], "yes")).toBe("Yes"); expect(displayValue(fields[3], undefined)).toBe("—"); expect(displayValue(fields[0], "5")).toBe("5")
    expect(activeFields(fields).map(f => f.key)).toEqual(["region", "since", "size", "vip"].sort((a, b) => a.toUpperCase().localeCompare(b.toUpperCase())))
  })
})

describe("CustomFieldInputs", () => {
  it("draws the right input for each kind, marks required, and reports changes", () => {
    const onChange = vi.fn(); render(<CustomFieldInputs fields={activeFields(fields)} values={{ region: "North" }} onChange={onChange} />)
    expect(screen.getByText("SIZE *")).toBeTruthy(); expect((screen.getByLabelText("REGION") as HTMLSelectElement).value).toBe("North"); expect((screen.getByLabelText("SINCE") as HTMLInputElement).type).toBe("date")
    fireEvent.change(screen.getByLabelText("SIZE"), { target: { value: "7" } }); expect(onChange).toHaveBeenCalledWith("size", "7")
    fireEvent.change(screen.getByLabelText("VIP"), { target: { value: "yes" } }); expect(onChange).toHaveBeenCalledWith("vip", "yes")
  })
  it("draws nothing when the account has no fields", () => { const { container } = render(<CustomFieldInputs fields={[]} values={{}} onChange={vi.fn()} />); expect(container.firstChild).toBeNull() })
})

describe("CustomFieldsAdmin", () => {
  it("lists fields including switched-off ones", async () => {
    get.mockResolvedValue({ data: { data: fields } }); wrap(<CustomFieldsAdmin />)
    expect(await screen.findByLabelText("NOTE")).toBeTruthy(); expect(screen.getByLabelText("NOTE").textContent).toContain("Switched off"); expect(screen.getByLabelText("REGION").textContent).toContain("North, South")
    expect(get).toHaveBeenCalledWith("/api/v1/crm/custom-fields", { params: { includeInactive: true } })
  })
  it("adds a field after checking it, including a choice list", async () => {
    get.mockResolvedValue({ data: { data: [] } }); wrap(<CustomFieldsAdmin />); await screen.findByText("No custom fields yet.")
    fireEvent.click(screen.getByRole("button", { name: "Add field" })); expect((await screen.findByRole("alert")).textContent).toMatch(/name/); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("New field name"), { target: { value: "Region" } }); fireEvent.change(screen.getByLabelText("New field kind"), { target: { value: "CHOICE" } })
    fireEvent.change(screen.getByLabelText("New field options"), { target: { value: "North" } }); fireEvent.click(screen.getByRole("button", { name: "Add field" })); expect((await screen.findByRole("alert")).textContent).toMatch(/two options/)
    fireEvent.change(screen.getByLabelText("New field options"), { target: { value: "North, South" } }); fireEvent.click(screen.getByLabelText("Add a custom field").querySelector("input[type=checkbox]")!); fireEvent.click(screen.getByRole("button", { name: "Add field" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/custom-fields", { label: "Region", type: "CHOICE", required: true, options: ["North", "South"] }))
  })
  it("renames, makes required and switches off", async () => {
    get.mockResolvedValue({ data: { data: fields } }); wrap(<CustomFieldsAdmin />)
    fireEvent.click(await screen.findByLabelText("Switch off VIP")); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/custom-fields/id-vip", { active: false }))
    fireEvent.click(screen.getByLabelText("Make required: SINCE")); await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/custom-fields/id-since", { required: true }))
    fireEvent.click(screen.getByLabelText("Rename VIP")); fireEvent.change(screen.getByLabelText("Field name"), { target: { value: "Key account" } }); fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/custom-fields/id-vip", { label: "Key account", options: undefined }))
  })
  it("shows server errors, hides editing from read-only users, and reports a failed load", async () => {
    perms = new Set(["CUSTOMER_READ"]); get.mockResolvedValue({ data: { data: fields } }); wrap(<CustomFieldsAdmin />)
    await screen.findByLabelText("SIZE"); expect(screen.queryByRole("button", { name: "Add field" })).toBeNull(); expect(screen.queryByLabelText("Switch off VIP")).toBeNull(); cleanup()
    get.mockImplementation(() => Promise.reject(new Error("x"))); wrap(<CustomFieldsAdmin />); expect((await screen.findByRole("alert")).textContent).toContain("Couldn't load")
  })
})
