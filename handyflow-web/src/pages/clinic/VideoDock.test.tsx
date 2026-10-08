import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { MemoryRouter, Routes, Route, useNavigate } from "react-router-dom"
import { roomUrlFrom, isSafeRoomUrl, sameCall } from "./videoDock"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a), get: vi.fn() } }))
import VideoDockProvider, { useVideoDock } from "./VideoDockProvider"

afterEach(cleanup)
beforeEach(() => post.mockReset())

describe("video dock rules", () => {
  it("reads the room address from wrapped or bare responses", () => {
    expect(roomUrlFrom({ data: { data: { videoRoomUrl: "https://x.daily.co/r" } } })).toBe("https://x.daily.co/r")
    expect(roomUrlFrom({ data: { videoRoomUrl: "https://x.daily.co/r" } })).toBe("https://x.daily.co/r")
    expect(roomUrlFrom({ data: {} })).toBeNull()
  })
  it("only https addresses go in the frame", () => {
    expect(isSafeRoomUrl("https://x.daily.co/r")).toBe(true)
    for (const bad of ["http://x.daily.co/r", "javascript:alert(1)", "data:text/html,hi", "not a url", "", null]) expect(isSafeRoomUrl(bad as any)).toBe(false)
  })
  it("recognises the call already open", () => {
    expect(sameCall({ appointmentId: "a" }, "a")).toBe(true)
    expect(sameCall({ appointmentId: "a" }, "b")).toBe(false)
    expect(sameCall(null, "a")).toBe(false)
  })
})

function Join({ id }: { id: string }) {
  const v = useVideoDock(); const nav = useNavigate()
  return <div><button onClick={() => v.join(id, "Thandi Z")}>join</button><button onClick={() => nav("/other")}>go</button></div>
}
const app = () => render(
  <MemoryRouter initialEntries={["/home"]}><VideoDockProvider>
    <Routes><Route path="/home" element={<Join id="a1" />} /><Route path="/other" element={<div>other page<Join id="a1" /></div>} /></Routes>
  </VideoDockProvider></MemoryRouter>)

describe("VideoDockProvider", () => {
  it("opens the call in a frame and keeps it when the page changes", async () => {
    post.mockResolvedValue({ data: { data: { videoRoomUrl: "https://x.daily.co/r" } } })
    app()
    fireEvent.click(screen.getByText("join"))
    const frame = await screen.findByTitle("Video call with Thandi Z") as HTMLIFrameElement
    expect(frame.src).toBe("https://x.daily.co/r")
    fireEvent.click(screen.getByText("go"))
    expect(screen.getByText("other page")).toBeTruthy()
    expect(screen.getByTitle("Video call with Thandi Z")).toBe(frame)   // the same element: the call was not remounted
  })
  it("minimise keeps the frame, leave removes it", async () => {
    post.mockResolvedValue({ data: { data: { videoRoomUrl: "https://x.daily.co/r" } } })
    app()
    fireEvent.click(screen.getByText("join"))
    await screen.findByTitle("Video call with Thandi Z")
    fireEvent.click(screen.getByLabelText("Minimise video call"))
    expect(screen.getByTitle("Video call with Thandi Z")).toBeTruthy()
    expect(screen.getByLabelText("Expand video call")).toBeTruthy()
    fireEvent.click(screen.getByText("Leave call"))
    expect(screen.queryByTitle("Video call with Thandi Z")).toBeNull()
  })
  it("joining the same call again does not ask the server twice", async () => {
    post.mockResolvedValue({ data: { data: { videoRoomUrl: "https://x.daily.co/r" } } })
    app()
    fireEvent.click(screen.getByText("join")); await screen.findByTitle("Video call with Thandi Z")
    fireEvent.click(screen.getByText("go")); fireEvent.click(screen.getAllByText("join")[0])
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
  })
  it("refuses an unsafe address and shows the server's message on failure", async () => {
    post.mockResolvedValueOnce({ data: { data: { videoRoomUrl: "javascript:alert(1)" } } })
    app()
    fireEvent.click(screen.getByText("join"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/not valid/)
    expect(screen.queryByTitle(/Video call with/)).toBeNull()
    post.mockRejectedValueOnce({ response: { data: { message: "Video is unavailable right now" } } })
    fireEvent.click(screen.getByText("join"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/unavailable/))
  })
})
