import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a) } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: any) => sel({ user: { email: "doc@clinic.test" } }) }))
import PatientsTab from "./PatientsTab"

const show = (onOpenPatient = vi.fn()) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PatientsTab onOpenPatient={onOpenPatient} /></QueryClientProvider>)
beforeEach(() => {
  for (const m of [get, post, put]) m.mockReset()
  get.mockImplementation(async (url: string) => url.includes("practitioners") ? { data: { data: [] } }
    : url.includes("duplicate") ? { data: { data: [] } } : { data: { data: { content: [], page: 0, size: 25, total: 0 } } })
  post.mockResolvedValue({ data: { data: { id: "new1", firstName: "Jo", lastName: "Doe" } } }); put.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

const open = async () => {
  fireEvent.click(await screen.findByRole("button", { name: /Register patient/ }))
  await screen.findByPlaceholderText("Jane")
}
const fillNames = () => {
  fireEvent.change(screen.getByPlaceholderText("Jane"), { target: { value: "Jo" } })
  fireEvent.change(screen.getByPlaceholderText("Smith"), { target: { value: "Doe" } })
}

describe("registering with a passport", () => {
  it("keeps letters and digits, does not fill the birth date, and records the ID type on the profile", async () => {
    show(); await open(); fillNames()
    fireEvent.change(screen.getByLabelText("ID type"), { target: { value: "PASSPORT" } })
    const box = screen.getByPlaceholderText("A01234567") as HTMLInputElement
    fireEvent.change(box, { target: { value: "a01-234567" } })
    expect(box.value).toBe("A01234567")
    fireEvent.click(screen.getAllByRole("button", { name: /Register patient/ }).pop()!)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/clinic/patients/new1/profile", { idType: "PASSPORT" }))
    expect(post.mock.calls[0][1]).toMatchObject({ idNumber: "A01234567", dateOfBirth: null })
  })
  it("an SA ID is still digits only and is not sent with an ID type", async () => {
    show(); await open(); fillNames()
    const box = screen.getByPlaceholderText("8501015026083") as HTMLInputElement
    fireEvent.change(box, { target: { value: "8501015026083" } })
    fireEvent.click(screen.getAllByRole("button", { name: /Register patient/ }).pop()!)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ idNumber: "8501015026083", dateOfBirth: "1985-01-01" })
    expect(put).not.toHaveBeenCalled()
  })
  it("a half-typed SA ID is refused with a reason", async () => {
    show(); await open(); fillNames()
    fireEvent.change(screen.getByPlaceholderText("8501015026083"), { target: { value: "85010" } })
    fireEvent.click(screen.getAllByRole("button", { name: /Register patient/ }).pop()!)
    expect(await screen.findByText("An SA ID number is 13 digits")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
})
