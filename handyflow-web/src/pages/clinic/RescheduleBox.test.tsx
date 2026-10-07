import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a) } }))
import RescheduleBox, { moveBody, NO_ROOM, moveProblem, toLocalInput } from "./RescheduleBox"

const appt = { id: "a1", scheduledAt: "2026-10-20T08:00:00Z", durationMinutes: 30 }
const show = (onMoved = vi.fn()) => ({ onMoved, ...render(
  <QueryClientProvider client={new QueryClient()}>
    <RescheduleBox appointment={appt} onMoved={onMoved} /></QueryClientProvider>) })
const FUTURE = "2099-01-01T10:00"

beforeEach(() => post.mockReset())
afterEach(cleanup)

describe("moveProblem", () => {
  it("needs a time and not the past", () => {
    const now = new Date("2026-10-07T10:00:00").getTime()
    expect(moveProblem("", now)).toMatch(/Choose/)
    expect(moveProblem("2026-10-07T09:00", now)).toMatch(/past/)
    expect(moveProblem("2026-10-07T11:00", now)).toBeNull()
  })
  it("formats an instant for the input", () => expect(toLocalInput("2026-10-20T08:00:00Z")).toMatch(/^2026-10-20T\d\d:\d\d$/))
})

describe("RescheduleBox", () => {
  it("opens, sends the new time and reports the moved appointment", async () => {
    post.mockResolvedValue({ data: { data: { id: "a1", status: "SCHEDULED" } } })
    const { onMoved } = show()
    fireEvent.click(screen.getByText("Reschedule"))
    fireEvent.change(screen.getByLabelText("New date and time"), { target: { value: FUTURE } })
    fireEvent.click(screen.getByText("Move appointment"))
    await waitFor(() => expect(onMoved).toHaveBeenCalledWith({ id: "a1", status: "SCHEDULED" }))
    expect(post.mock.calls[0][0]).toBe("/api/v1/clinic/appointments/a1/reschedule")
    expect(post.mock.calls[0][1].scheduledAt).toBe(new Date(FUTURE).toISOString())
    expect(post.mock.calls[0][2]).toBeUndefined()
  })

  it("shows the server's clash warning and moves anyway when asked", async () => {
    post.mockRejectedValueOnce({ response: { status: 409, data: { message: "Dr Lee already has an appointment 10:00–10:30 with Sam." } } })
    post.mockResolvedValueOnce({ data: { data: { id: "a1" } } })
    const { onMoved } = show()
    fireEvent.click(screen.getByText("Reschedule"))
    fireEvent.change(screen.getByLabelText("New date and time"), { target: { value: FUTURE } })
    fireEvent.click(screen.getByText("Move appointment"))
    expect(await screen.findByText(/already has an appointment/)).toBeTruthy()
    expect(onMoved).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Move anyway"))
    await waitFor(() => expect(onMoved).toHaveBeenCalled())
    expect(post.mock.calls[1][2]).toEqual({ params: { allowOverlap: true } })
  })

  it("shows other errors without offering to move anyway", async () => {
    post.mockRejectedValueOnce({ response: { status: 400, data: { message: "Only a SCHEDULED or CONFIRMED appointment can be moved" } } })
    show()
    fireEvent.click(screen.getByText("Reschedule"))
    fireEvent.change(screen.getByLabelText("New date and time"), { target: { value: FUTURE } })
    fireEvent.click(screen.getByText("Move appointment"))
    expect(await screen.findByText(/can be moved/)).toBeTruthy()
    expect(screen.queryByText("Move anyway")).toBeNull()
  })

  it("does not call the server for a time in the past", () => {
    show()
    fireEvent.click(screen.getByText("Reschedule"))
    fireEvent.change(screen.getByLabelText("New date and time"), { target: { value: "2001-01-01T10:00" } })
    fireEvent.click(screen.getByText("Move appointment"))
    expect(screen.getByText(/into the past/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("cancel closes the form", () => {
    show()
    fireEvent.click(screen.getByText("Reschedule"))
    fireEvent.click(screen.getByText("Cancel"))
    expect(screen.queryByLabelText("New date and time")).toBeNull()
  })
})

describe("room on reschedule", () => {
  it("sends the room only when a different one is picked", () => {
    expect(moveBody(FUTURE, "", null).roomId).toBeUndefined()
    expect(moveBody(FUTURE, "r1", "r1").roomId).toBeUndefined()
    expect(moveBody(FUTURE, "r2", "r1").roomId).toBe("r2")
    expect(moveBody(FUTURE, "r2", null).roomId).toBe("r2")
  })
  it("asks for the room to be cleared only when there is one to clear", () => {
    expect(moveBody(FUTURE, NO_ROOM, "r1").clearRoom).toBe(true)
    expect(moveBody(FUTURE, NO_ROOM, "r1").roomId).toBeUndefined()
    expect(moveBody(FUTURE, NO_ROOM, null).clearRoom).toBeUndefined()
  })
  it("shows the room choice only when rooms exist and sends the pick", async () => {
    post.mockResolvedValue({ data: { data: { id: "a1" } } })
    const rooms = [{ id: "r1", name: "Room 1" }, { id: "r2", name: "Room 2" }]
    render(<QueryClientProvider client={new QueryClient()}>
      <RescheduleBox appointment={{ ...appt, roomId: "r1" }} onMoved={vi.fn()} rooms={rooms} /></QueryClientProvider>)
    fireEvent.click(screen.getByText("Reschedule"))
    fireEvent.change(screen.getByLabelText("New date and time"), { target: { value: FUTURE } })
    fireEvent.change(screen.getByLabelText("Room"), { target: { value: "r2" } })
    fireEvent.click(screen.getByText("Move appointment"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1].roomId).toBe("r2")
  })
  it("hides the room select when there are no rooms", () => {
    show(); fireEvent.click(screen.getByText("Reschedule"))
    expect(screen.queryByLabelText("Room")).toBeNull()
  })
})
