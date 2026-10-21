import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { ContactsPanel } from "./ContactsPanel"
import { EMPTY_CONTACT, contactBody, contactProblem, initials, mainContact, sortContacts, toForm, type Contact } from "./contacts.logic"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a), put: (...a: Args) => put(...a), delete: (...a: Args) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const contact = (id: string, first: string, last: string, o: Partial<Contact> = {}): Contact => ({ id, customerId: "c1", firstName: first, lastName: last, fullName: `${first} ${last}`, email: `${first.toLowerCase()}@x.co`, phone: null, jobTitle: null, primary: false, notes: null, ...o })
const people = [contact("1", "Ben", "Zulu"), contact("2", "Ann", "Smith", { primary: true, jobTitle: "Buyer", phone: "082 123 4567" }), contact("3", "Cara", "Smith")]
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ContactsPanel customerId="c1" /></QueryClientProvider>)
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); for (const m of [get, post, put, del]) m.mockReset(); get.mockImplementation(() => ok(people)); for (const m of [post, put, del]) m.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("contact rules", () => {
  it("puts the main contact first, then by last and first name", () => {
    expect(sortContacts(people).map(c => c.id)).toEqual(["2", "3", "1"]); expect(mainContact(people)?.id).toBe("2"); expect(mainContact([people[0]])).toBeNull(); expect(initials(people[1])).toBe("AS")
  })
  it("checks names, email, phone and repeated emails", () => {
    const f = { ...EMPTY_CONTACT, firstName: "Dan", lastName: "Dube" }
    expect(contactProblem(EMPTY_CONTACT, [], null)).toMatch(/first name/); expect(contactProblem({ ...f, lastName: " " }, [], null)).toMatch(/last name/); expect(contactProblem(f, [], null)).toBeNull()
    expect(contactProblem({ ...f, email: "nope" }, [], null)).toMatch(/email/); expect(contactProblem({ ...f, phone: "abc" }, [], null)).toMatch(/phone/); expect(contactProblem({ ...f, phone: "+27 82 123 4567" }, [], null)).toBeNull()
    expect(contactProblem({ ...f, email: "ANN@x.co" }, people, null)).toMatch(/already/); expect(contactProblem({ ...f, email: "ann@x.co" }, people, "2")).toBeNull()
    expect(contactProblem({ ...f, notes: "x".repeat(2001) }, [], null)).toMatch(/2000/)
  })
  it("sends blank boxes as null and trims the rest", () => {
    expect(contactBody({ ...EMPTY_CONTACT, firstName: " Dan ", lastName: "Dube", email: "  ", jobTitle: " Buyer ", primary: true })).toEqual({ firstName: "Dan", lastName: "Dube", email: null, phone: null, jobTitle: "Buyer", notes: null, primary: true })
    expect(toForm(people[1])).toMatchObject({ firstName: "Ann", phone: "082 123 4567", email: "ann@x.co", primary: true })
  })
})

describe("contacts panel", () => {
  it("lists the contacts with the main one first and marked", async () => {
    wrap(); const first = await screen.findByLabelText("Ann Smith"); expect(first.textContent).toContain("Main contact"); expect(first.textContent).toContain("Buyer"); expect(first.textContent).toContain("082 123 4567")
    const names = screen.getAllByRole("article").map(a => a.getAttribute("aria-label")); expect(names).toEqual(["Ann Smith", "Cara Smith", "Ben Zulu"])
  })
  it("adds a contact, blocking a bad email until it is fixed", async () => {
    wrap(); fireEvent.click(await screen.findByText("Add contact")); const form = screen.getByRole("form", { name: "New contact" })
    fireEvent.change(within(form).getByLabelText("First name"), { target: { value: "Dan" } }); fireEvent.change(within(form).getByLabelText("Last name"), { target: { value: "Dube" } }); fireEvent.change(within(form).getByLabelText("Email"), { target: { value: "dan" } })
    expect((within(form).getByText("Save contact") as HTMLButtonElement).disabled).toBe(true); expect(within(form).getByText(/doesn't look right/)).toBeTruthy()
    fireEvent.change(within(form).getByLabelText("Email"), { target: { value: "dan@x.co" } }); fireEvent.click(within(form).getByText("Save contact"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/c1/contacts", { firstName: "Dan", lastName: "Dube", email: "dan@x.co", phone: null, jobTitle: null, notes: null, primary: false }))
  })
  it("the first contact for a customer is ticked as the main contact", async () => {
    get.mockImplementation(() => ok([])); wrap(); expect(await screen.findByText(/No contacts yet/)).toBeTruthy(); fireEvent.click(screen.getByText("Add contact"))
    expect((screen.getByLabelText("Main contact") as HTMLInputElement).checked).toBe(true)
  })
  it("edits a contact with a PUT, and the main contact's box is locked", async () => {
    wrap(); fireEvent.click(await screen.findByLabelText("Edit Ann Smith")); const form = screen.getByRole("form", { name: "Edit Ann Smith" })
    expect((within(form).getByLabelText("Main contact") as HTMLInputElement).disabled).toBe(true); fireEvent.change(within(form).getByLabelText("Job title"), { target: { value: "Head of buying" } }); fireEvent.click(within(form).getByText("Save contact"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/customers/c1/contacts/2", expect.objectContaining({ jobTitle: "Head of buying", primary: true })))
  })
  it("makes another contact the main one", async () => {
    wrap(); fireEvent.click(await screen.findByLabelText("Make Ben Zulu the main contact")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/crm/customers/c1/contacts/1/primary"))
    expect(screen.queryByLabelText("Make Ann Smith the main contact")).toBeNull()
  })
  it("removes a contact only after a confirm", async () => {
    wrap(); fireEvent.click(await screen.findByLabelText("Remove Ben Zulu")); expect(del).not.toHaveBeenCalled(); fireEvent.click(screen.getByLabelText("Confirm remove Ben Zulu")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/customers/c1/contacts/1"))
  })
  it("shows the server's refusal and keeps the form", async () => {
    post.mockRejectedValue({ response: { data: { message: "This customer already has a contact with that email" } } }); wrap(); fireEvent.click(await screen.findByText("Add contact"))
    fireEvent.change(screen.getByLabelText("First name"), { target: { value: "Dan" } }); fireEvent.change(screen.getByLabelText("Last name"), { target: { value: "Dube" } }); fireEvent.click(screen.getByText("Save contact"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already has a contact/); expect(screen.getByRole("form", { name: "New contact" })).toBeTruthy()
  })
  it("read-only users see contacts without add, edit or remove; failures are explained", async () => {
    perms = new Set(["CUSTOMER_READ"]); wrap(); await screen.findByLabelText("Ann Smith"); expect(screen.queryByText("Add contact")).toBeNull(); expect(screen.queryByLabelText("Edit Ann Smith")).toBeNull(); expect(screen.queryByLabelText("Remove Ben Zulu")).toBeNull(); cleanup()
    get.mockRejectedValue(new Error("x")); wrap(); expect((await screen.findByRole("alert")).textContent).toMatch(/Couldn't load/)
  })
})
