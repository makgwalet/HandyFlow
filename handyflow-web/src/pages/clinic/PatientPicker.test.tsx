import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import PatientPicker, { patientSearchUrl, patientDetail } from "./PatientPicker"

const ada = { id: "1", fullName: "Ada Lovelace", dateOfBirth: "1990-01-02", phone: "0821112222" }
const ada2 = { id: "2", fullName: "Ada Lovelace", dateOfBirth: "2001-05-06", patientNumber: "P-9" }
const reply = (rows: any[]) => ({ data: { data: { content: rows } } })
const show = (props: any = {}) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <PatientPicker value={null} onChange={() => {}} {...props} /></QueryClientProvider>)

// Braces matter: vitest runs whatever a beforeEach returns as a cleanup hook, and mockReset() returns the mock.
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("helpers", () => {
  it("asks the server to search, never to load everything", () => {
    expect(patientSearchUrl("  ada ")).toBe("/api/v1/clinic/patients?size=15&search=ada")
    expect(patientSearchUrl("")).toBe("/api/v1/clinic/patients?size=15")
    expect(patientSearchUrl("a b&c")).toContain("search=a+b%26c")
  })
  it("describes a patient so namesakes can be told apart", () => {
    expect(patientDetail(ada2)).toBe("DOB 2001-05-06 · #P-9")
    expect(patientDetail({ id: "x", fullName: "No Detail" })).toBe("")
  })
})

describe("PatientPicker", () => {
  it("searches the server as you type and picks the chosen patient", async () => {
    get.mockResolvedValue(reply([ada, ada2]))
    const onChange = vi.fn()
    show({ onChange })
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "ada" } })
    await waitFor(() => expect(get).toHaveBeenCalledWith(expect.stringContaining("search=ada")))
    const options = await screen.findAllByRole("option")
    expect(options).toHaveLength(2)
    fireEvent.mouseDown(options[1])
    expect(onChange).toHaveBeenCalledWith(ada2)
  })
  it("picks with the keyboard", async () => {
    get.mockResolvedValue(reply([ada, ada2]))
    const onChange = vi.fn()
    show({ onChange })
    const box = screen.getByRole("combobox")
    fireEvent.focus(box)
    await screen.findAllByRole("option")
    fireEvent.keyDown(box, { key: "ArrowDown" })
    fireEvent.keyDown(box, { key: "Enter" })
    expect(onChange).toHaveBeenCalledWith(ada2)
  })
  it("says so when nothing matches", async () => {
    get.mockResolvedValue(reply([]))
    show()
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "zzz" } })
    expect(await screen.findByText("No patients match.")).toBeTruthy()
  })
  it("shows an error instead of an empty list when the search fails", async () => {
    get.mockRejectedValue({ response: { status: 500 } })
    show()
    fireEvent.focus(screen.getByRole("combobox"))
    expect(await screen.findByText(/Could not search/)).toBeTruthy()
  })
  it("shows the chosen patient and lets you change it", () => {
    const onChange = vi.fn()
    show({ value: ada, onChange })
    expect(screen.getByText("Ada Lovelace")).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Change patient"))
    expect(onChange).toHaveBeenCalledWith(null)
  })
  it("shows a validation message", () => {
    show({ error: "Patient is required" })
    expect(screen.getByRole("alert").textContent).toBe("Patient is required")
  })
})
