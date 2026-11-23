import { describe, it, expect } from "vitest"
import { fileNameFromDisposition, messageFromErrorText } from "./agDownload"

describe("file name from the server", () => {
  it("reads a quoted and an unquoted name", () => {
    expect(fileNameFromDisposition('attachment; filename="herd-register-Rietvlei-2026-11-23.csv"', "x.csv")).toBe("herd-register-Rietvlei-2026-11-23.csv")
    expect(fileNameFromDisposition("attachment; filename=doc.pdf", "x.pdf")).toBe("doc.pdf")
  })
  it("reads the encoded form first", () => {
    expect(fileNameFromDisposition("attachment; filename=\"a.csv\"; filename*=UTF-8''caf%C3%A9.csv", "x.csv")).toBe("café.csv")
  })
  it("falls back when there is no header or no name", () => {
    expect(fileNameFromDisposition(undefined, "x.csv")).toBe("x.csv")
    expect(fileNameFromDisposition("attachment", "x.csv")).toBe("x.csv")
  })
})

describe("error text from a failed download", () => {
  it("uses the server's message", () => {
    expect(messageFromErrorText('{"success":false,"message":"A document of identification is only made for a sale or a transfer out"}', "fallback"))
      .toBe("A document of identification is only made for a sale or a transfer out")
  })
  it("falls back for anything else", () => {
    expect(messageFromErrorText("<html>502</html>", "fallback")).toBe("fallback")
    expect(messageFromErrorText('{"message":"  "}', "fallback")).toBe("fallback")
    expect(messageFromErrorText("{}", "fallback")).toBe("fallback")
  })
})
