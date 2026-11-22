import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

import { useAuthStore } from "../../store/auth.store"
import OccurrenceBookCard from "./OccurrenceBookCard"

const day = (lines: unknown[], signoff: unknown = null) => ({ data: { data: { siteId: "s1", siteName: "Mall", date: "2026-11-20", lines, truncated: false, signoff } } })
const line = (kind: string, text: string, at = "2026-11-20T04:01:00Z") => ({ at, kind, text, guardId: "g1", guardName: "Thabo Mokoena", refId: kind + text })

function as(perms: string[]) { useAuthStore.setState({ user: { permissions: perms } } as never) }
function show() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><OccurrenceBookCard siteId="s1" /></QueryClientProvider>)
}

describe("OccurrenceBookCard", () => {
  beforeEach(() => { api.get.mockReset(); api.post.mockReset(); api.post.mockResolvedValue({ data: {} }) })
  afterEach(() => cleanup())

  it("lists the day's lines with time, kind and guard", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue(day([line("ON_DUTY", "Went on duty"), line("INCIDENT", "Fence cut (HIGH, OPEN)", "2026-11-20T09:00:00Z")]))
    show()
    expect((await screen.findAllByText(/Went on duty/)).length).toBe(2) // screen + print copy
    expect(screen.getAllByText("06:01").length).toBe(2)
    expect(screen.getAllByText("Incident").length).toBe(2)
    expect(screen.getAllByText(/Thabo Mokoena/).length).toBe(4)
    expect(api.get.mock.calls[0][0]).toBe("/api/v1/security/sites/s1/occurrence-book")
  })

  it("says nothing was recorded for an empty day", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue(day([]))
    show()
    expect(await screen.findByText(/Nothing has been recorded/)).toBeTruthy()
  })

  it("shows an error, not an empty book, when loading fails", async () => {
    as(["SECURITY_READ"])
    api.get.mockImplementation(() => Promise.reject(new Error("x")))
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
    expect(screen.queryByText(/Nothing has been recorded/)).toBeNull()
  })

  it("treats a malformed answer as an error", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue({ data: { data: { site: {} } } })
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
  })

  it("read-only users get no entry form", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue(day([]))
    show()
    await screen.findByText(/Nothing has been recorded/)
    expect(screen.queryByLabelText("Entry text")).toBeNull()
  })

  it("managers add an entry, with a reference, and the text clears", async () => {
    as(["SECURITY_READ", "SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([]))
    show()
    fireEvent.change(await screen.findByLabelText("Entry text"), { target: { value: "  Gate 2 lock fixed " } })
    fireEvent.change(screen.getByLabelText("Entry type"), { target: { value: "HANDOVER" } })
    fireEvent.click(screen.getByText("Add entry"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    const [url, body] = api.post.mock.calls[0]
    expect(url).toBe("/api/v1/security/sites/s1/occurrence-book/entries")
    expect(body.kind).toBe("HANDOVER")
    expect(body.text).toBe("Gate 2 lock fixed")
    expect(typeof body.clientRef).toBe("string")
    await waitFor(() => expect((screen.getByLabelText("Entry text") as HTMLTextAreaElement).value).toBe(""))
  })

  it("refuses a too-short entry without calling the server", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([]))
    show()
    fireEvent.change(await screen.findByLabelText("Entry text"), { target: { value: "ab" } })
    fireEvent.click(screen.getByText("Add entry"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/at least 3/)
    expect(api.post).not.toHaveBeenCalled()
  })

  it("keeps the text and shows the server message when adding fails", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([]))
    api.post.mockImplementation(() => Promise.reject({ response: { data: { message: "Site not found" } } }))
    show()
    fireEvent.change(await screen.findByLabelText("Entry text"), { target: { value: "hello there" } })
    fireEvent.click(screen.getByText("Add entry"))
    expect((await screen.findByRole("alert")).textContent).toBe("Site not found")
    expect((screen.getByLabelText("Entry text") as HTMLTextAreaElement).value).toBe("hello there")
  })

  it("a retry of the same text reuses the reference, edited text gets a new one", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([]))
    api.post.mockImplementation(() => Promise.reject(new Error("net")))
    show()
    const box = await screen.findByLabelText("Entry text")
    fireEvent.change(box, { target: { value: "hello there" } })
    fireEvent.click(screen.getByText("Add entry"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    await screen.findByRole("alert")
    fireEvent.click(screen.getByText("Add entry"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(2))
    expect(api.post.mock.calls[1][1].clientRef).toBe(api.post.mock.calls[0][1].clientRef)
    fireEvent.change(box, { target: { value: "hello there again" } })
    fireEvent.click(screen.getByText("Add entry"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(3))
    expect(api.post.mock.calls[2][1].clientRef).not.toBe(api.post.mock.calls[0][1].clientRef)
  })

  it("going back a day asks for that day; next is disabled on today", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue(day([]))
    show()
    await screen.findByText(/Nothing has been recorded/)
    expect((screen.getByLabelText("Next day") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByLabelText("Previous day"))
    await waitFor(() => expect(api.get.mock.calls.length).toBe(2))
    expect(api.get.mock.calls[1][1].params.date < api.get.mock.calls[0][1].params.date).toBe(true)
    expect(await screen.findByText(/on this day/)).toBeTruthy()
  })

  const signedOff = { signedBy: "u1", signedByName: "Ann Smith", signedAt: "2026-11-20T20:10:00Z", note: "quiet night" }

  it("shows who signed the day and offers corrections only", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([], signedOff))
    show()
    expect((await screen.findAllByText(/Signed off by Ann Smith/)).length).toBe(2)
    expect(screen.getAllByText(/quiet night/).length).toBe(2)
    const opts = Array.from((screen.getByLabelText("Entry type") as HTMLSelectElement).options).map(o => o.value)
    expect(opts).toEqual(["CORRECTION"])
    expect(screen.queryByText("Sign this day")).toBeNull()
  })

  it("a correction on a signed day is sent as CORRECTION even if Note was chosen before", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([], signedOff))
    show()
    fireEvent.change(await screen.findByLabelText("Entry text"), { target: { value: "Time was 22:05" } })
    fireEvent.click(screen.getByText("Add entry"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    expect(api.post.mock.calls[0][1].kind).toBe("CORRECTION")
  })

  it("read-only users see the signature but no sign button", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue(day([]))
    show()
    await screen.findByText(/Nothing has been recorded/)
    expect(screen.queryByText("Sign this day")).toBeNull()
  })

  it("managers confirm before signing, then the day is signed with the date", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([]))
    show()
    fireEvent.click(await screen.findByText("Sign this day"))
    expect(api.post).not.toHaveBeenCalled()
    expect(screen.getByText(/Today is not over/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Sign-off note"), { target: { value: " all quiet " } })
    fireEvent.click(screen.getByText("Confirm sign-off"))
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1))
    const [url, body, cfg] = api.post.mock.calls[0]
    expect(url).toBe("/api/v1/security/sites/s1/occurrence-book/sign")
    expect(body).toEqual({ note: "all quiet" })
    expect(cfg.params.date).toMatch(/^\d{4}-\d{2}-\d{2}$/)
  })

  it("cancel closes the confirmation without signing", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([]))
    show()
    fireEvent.click(await screen.findByText("Sign this day"))
    fireEvent.click(screen.getByText("Cancel"))
    expect(screen.queryByText("Confirm sign-off")).toBeNull()
    expect(api.post).not.toHaveBeenCalled()
  })

  it("shows the server message when signing fails", async () => {
    as(["SECURITY_MANAGE"])
    api.get.mockResolvedValue(day([]))
    api.post.mockImplementation(() => Promise.reject({ response: { data: { message: "That day has not happened yet." } } }))
    show()
    fireEvent.click(await screen.findByText("Sign this day"))
    fireEvent.click(screen.getByText("Confirm sign-off"))
    expect((await screen.findByRole("alert")).textContent).toBe("That day has not happened yet.")
  })

  it("the print copy carries the title, the lines, and says when the day is not signed", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue(day([line("ON_DUTY", "Went on duty")]))
    show()
    await screen.findAllByText(/Went on duty/)
    const printed = screen.getByTestId("ob-print")
    expect(printed.textContent).toContain("Daily Occurrence Book — Mall")
    expect(printed.textContent).toContain("NOT SIGNED OFF.")
    expect(printed.textContent).toContain("Went on duty")
    expect(printed.textContent).toContain("Thabo Mokoena")
    expect(printed.textContent).toMatch(/Printed .*SAST/)
  })

  it("the print copy shows the signature on a signed day", async () => {
    as(["SECURITY_READ"])
    api.get.mockResolvedValue(day([], { signedBy: "u1", signedByName: "Ann Smith", signedAt: "2026-11-20T20:10:00Z", note: null }))
    show()
    await screen.findAllByText(/Signed off by Ann Smith/)
    const printed = screen.getByTestId("ob-print")
    expect(printed.textContent).toContain("Signed off by Ann Smith")
    expect(printed.textContent).not.toContain("NOT SIGNED OFF")
    expect(printed.textContent).toContain("No entries.")
  })

  it("the print button calls the browser print, and is off while nothing has loaded", async () => {
    as(["SECURITY_READ"])
    const pr = vi.spyOn(window, "print").mockImplementation(() => {})
    api.get.mockResolvedValue(day([line("NOTE", "hello there")]))
    show()
    expect((screen.getByLabelText("Print this day") as HTMLButtonElement).disabled).toBe(true)
    await screen.findAllByText(/hello there/)
    fireEvent.click(screen.getByLabelText("Print this day"))
    expect(pr).toHaveBeenCalledTimes(1)
    pr.mockRestore()
  })

  it("there is no print copy when the book failed to load", async () => {
    as(["SECURITY_READ"])
    api.get.mockImplementation(() => Promise.reject(new Error("x")))
    show()
    await waitFor(() => expect(screen.getByRole("alert")).toBeTruthy())
    expect(screen.queryByTestId("ob-print")).toBeNull()
  })
})
