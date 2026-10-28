import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
vi.mock("axios", () => ({ default: { create: () => ({ get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) }) } }))

import { JobApplyPage } from "./JobApplyPage"

const job = { id: "j1", title: "Site Clerk", department: null, location: "Pretoria", jobType: "FULL_TIME", experienceLevel: "MID", description: "Filing", requirements: null,
  benefits: null, salaryMin: null, salaryMax: null, showSalary: false, companyName: "Acme" }

function show() {
  return render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter initialEntries={["/careers/acme/site-clerk"]}>
        <Routes><Route path="/careers/:tenantSlug/:jobSlug" element={<JobApplyPage />} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>)
}
function fill() {
  const boxes = screen.getAllByRole("textbox") as HTMLInputElement[]
  fireEvent.change(boxes[0], { target: { value: "Thandi" } })
  fireEvent.change(boxes[1], { target: { value: "Mokoena" } })
  fireEvent.change(boxes[2], { target: { value: "t@x.co.za" } })
}

beforeEach(() => { get.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: job } }) })
afterEach(cleanup)

describe("careers apply form consent", () => {
  it("will not send the application until the applicant agrees", async () => {
    show()
    await screen.findByText("Submit application")
    fill()
    fireEvent.click(screen.getByText("Submit application"))
    expect((await screen.findByText(/agree to the use of your details/i, { selector: "div" })).textContent).toMatch(/before you apply/)
    expect(post).not.toHaveBeenCalled()
  })

  it("sends the agreement with the application", async () => {
    post.mockResolvedValue({ data: { data: { applicationId: "a1", jobTitle: "Site Clerk", companyName: "Acme", applicantName: "Thandi Mokoena", stageLabel: "Applied" } } })
    show()
    await screen.findByText("Submit application")
    fill()
    fireEvent.click(screen.getByRole("checkbox"))
    fireEvent.click(screen.getByText("Submit application"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect((post.mock.calls[0][1] as { consent: boolean }).consent).toBe(true)
  })

  it("shows the company's retention months in the consent text", async () => {
    get.mockResolvedValue({ data: { data: { ...job, retentionMonths: 18 } } })
    show()
    expect(await screen.findByText(/removed 18 months after my last activity/)).toBeTruthy()
  })

  it("falls back to the general wording when the company's period is not given", async () => {
    show()
    expect(await screen.findByText(/removed after the company's retention period/)).toBeTruthy()
  })

  it("links the company's privacy notice beside the consent text, opening in a new tab", async () => {
    get.mockResolvedValue({ data: { data: { ...job, retentionMonths: 18, privacyNoticeUrl: "https://acme.co.za/privacy" } } })
    show()
    const link = await screen.findByText("Read our privacy notice")
    expect(link.getAttribute("href")).toBe("https://acme.co.za/privacy")
    expect(link.getAttribute("target")).toBe("_blank")
    expect(link.getAttribute("rel")).toMatch(/noopener/)
  })

  it("shows no link when there is none, or when it is not a web address", async () => {
    get.mockResolvedValue({ data: { data: { ...job, privacyNoticeUrl: "javascript:alert(1)" } } })
    show()
    await screen.findByText(/removed after the company's retention period/)
    expect(screen.queryByText("Read our privacy notice")).toBeNull()
  })
})
