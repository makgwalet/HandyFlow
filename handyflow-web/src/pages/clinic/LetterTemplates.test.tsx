import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
import LetterTemplatePicker from "./LetterTemplatePicker"
import SaveAsTemplate from "./SaveAsTemplate"
import WriteLetterModal from "./WriteLetterModal"
import LetterTemplatesManager from "./LetterTemplatesManager"

const tpl = (o: object = {}) => ({ id: "t1", kind: "GENERAL_LETTER", name: "Repeat prescription letter", title: "Repeat prescription", body: "Dear {{patient.name}}", ...o })
afterEach(cleanup)
beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); for (const k of Object.keys(perms)) delete perms[k] })

describe("template picker", () => {
  it("shows nothing without permission and loads nothing", () => {
    const { container } = render(<LetterTemplatePicker kind="SICK_NOTE" consultationId="c1" onApply={() => {}} />)
    expect(container.textContent).toBe(""); expect(get).not.toHaveBeenCalled()
  })
  it("shows nothing when there are no templates or they cannot be loaded", async () => {
    perms.CLINIC_DOCUMENT_READ = true
    get.mockResolvedValue({ data: { data: [] } })
    const { container } = render(<LetterTemplatePicker kind="SICK_NOTE" consultationId="c1" onApply={() => {}} />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
  it("fills the template for this visit through the server and hands it over", async () => {
    perms.CLINIC_DOCUMENT_READ = true
    get.mockImplementation((url: string) => url.endsWith("/render") ? Promise.resolve({ data: { data: tpl({ body: "Dear Liam Botha" }) } }) : Promise.resolve({ data: { data: [tpl()] } }))
    const onApply = vi.fn()
    render(<LetterTemplatePicker kind="GENERAL_LETTER" consultationId="c1" onApply={onApply} />)
    fireEvent.change(await screen.findByLabelText("Use a template"), { target: { value: "t1" } })
    await waitFor(() => expect(onApply).toHaveBeenCalledWith(expect.objectContaining({ body: "Dear Liam Botha" })))
    expect(get).toHaveBeenCalledWith("/api/v1/clinic/letter-templates/t1/render", { params: { consultationId: "c1" } })
  })
})

describe("save as template", () => {
  it("is hidden without the permission", () => {
    const { container } = render(<SaveAsTemplate kind="SICK_NOTE" payload={() => ({ body: "x" })} />)
    expect(container.textContent).toBe("")
  })
  it("saves what was typed under a name", async () => {
    perms.CLINIC_DOCUMENT_CREATE = true; post.mockResolvedValue({ data: {} })
    render(<SaveAsTemplate kind="SICK_NOTE" payload={() => ({ body: "Seen", unfitDays: 2 })} />)
    fireEvent.click(screen.getByRole("button", { name: "Save as template" }))
    const save = screen.getByRole("button", { name: "Save" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Template name"), { target: { value: " Flu rest " } })
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/letter-templates", { kind: "SICK_NOTE", name: "Flu rest", body: "Seen", unfitDays: 2 }))
    expect(await screen.findByRole("status")).toBeTruthy()
  })
  it("shows the server's reason, such as a name that is taken", async () => {
    perms.CLINIC_DOCUMENT_CREATE = true; post.mockRejectedValue({ response: { data: { message: "A template called \"x\" already exists" } } })
    render(<SaveAsTemplate kind="REFERRAL" payload={() => ({ title: "r" })} />)
    fireEvent.click(screen.getByRole("button", { name: "Save as template" }))
    fireEvent.change(screen.getByLabelText("Template name"), { target: { value: "x" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already exists/)
  })
})

describe("write a letter", () => {
  const visits = [{ id: "c1", consultedAt: "2026-10-07T08:00:00Z", chiefComplaint: "Cough" }]
  beforeEach(() => { (URL as any).createObjectURL = vi.fn(() => "blob:x"); (URL as any).revokeObjectURL = vi.fn(); vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => {}) })
  it("needs a title and text, inserts merge fields, and posts the letter for the visit", async () => {
    post.mockResolvedValue({ data: new Blob(["x"]) })
    const onClose = vi.fn()
    render(<WriteLetterModal visits={visits} onClose={onClose} />)
    const go = screen.getByRole("button", { name: "Download letter" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Repeat prescription" } })
    fireEvent.change(screen.getByLabelText("Text"), { target: { value: "Dear " } })
    fireEvent.click(screen.getByRole("button", { name: "{{patient.name}}" }))
    expect((screen.getByLabelText("Text") as HTMLTextAreaElement).value).toBe("Dear {{patient.name}}")
    fireEvent.change(screen.getByLabelText("Text"), { target: { value: "Dear Liam" } })
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/letter", { title: "Repeat prescription", body: "Dear Liam" }, { responseType: "blob" }))
    await waitFor(() => expect(onClose).toHaveBeenCalled())
  })
  it("says so when the letter cannot be written", async () => {
    post.mockRejectedValue({ response: { data: new Blob(["x"]) } })
    render(<WriteLetterModal visits={visits} onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "T" } })
    fireEvent.change(screen.getByLabelText("Text"), { target: { value: "B" } })
    fireEvent.click(screen.getByRole("button", { name: "Download letter" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Could not write the letter/)
  })
})

describe("template manager", () => {
  it("lists templates by kind, archives one and reloads", async () => {
    get.mockResolvedValue({ data: { data: [tpl(), tpl({ id: "t2", kind: "SICK_NOTE", name: "Flu rest", title: null })] } })
    del.mockResolvedValue({ data: {} })
    render(<LetterTemplatesManager onClose={() => {}} />)
    expect(await screen.findByText("Repeat prescription letter")).toBeTruthy()
    expect(screen.getByText("Flu rest")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Archive Flu rest" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/clinic/letter-templates/t2"))
    await waitFor(() => expect(get.mock.calls.length).toBeGreaterThan(1))
  })
  it("will not save a template without a name and then creates it", async () => {
    get.mockResolvedValue({ data: { data: [] } }); post.mockResolvedValue({ data: {} })
    render(<LetterTemplatesManager onClose={() => {}} />)
    fireEvent.click(await screen.findByRole("button", { name: "New template" }))
    fireEvent.click(screen.getByRole("button", { name: "Save template" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/name/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Template name"), { target: { value: "Fitness letter" } })
    fireEvent.change(screen.getByLabelText("Letter title"), { target: { value: "Fitness to work" } })
    fireEvent.change(screen.getByLabelText("Letter text"), { target: { value: "{{patient.name}} is fit." } })
    fireEvent.click(screen.getByRole("button", { name: "Save template" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/letter-templates",
      { kind: "GENERAL_LETTER", name: "Fitness letter", title: "Fitness to work", body: "{{patient.name}} is fit.", specialty: null, urgency: null, unfitDays: null }))
  })
})
