import { describe, it, expect } from "vitest"
import { MAX_RECIPIENTS, canAdd, canSaveContact, validContact, validPhone, confirmText, errorText, sendBlock, sentText, validEmail, type Recipient } from "./reportEmailLogic"

const r = (email: string, i = 0): Recipient => ({ id: String(i), name: "N", email })

describe("reportEmailLogic", () => {
  it("accepts one address and refuses anything else", () => {
    expect(validEmail(" a.b@client.co.za ")).toBe(true)
    for (const bad of ["", "a", "a@b", "a b@c.com", "a@b.com,c@d.com", "a@b.com;c@d.com", "<a@b.com>", "a@b..", "a@@b.com", "x".repeat(250) + "@b.com", "a@b.com\nx@y.com"])
      expect(validEmail(bad), bad).toBe(false)
  })
  it("adds only a named, valid, new address while there is room", () => {
    expect(canAdd("Jo", "jo@c.com", [])).toBe(true)
    expect(canAdd("  ", "jo@c.com", [])).toBe(false)
    expect(canAdd("x".repeat(101), "jo@c.com", [])).toBe(false)
    expect(canAdd("Jo", "bad", [])).toBe(false)
    expect(canAdd("Jo", "JO@c.com", [r("jo@c.com")])).toBe(false)
    const full = Array.from({ length: MAX_RECIPIENTS }, (_, i) => r(`a${i}@c.com`, i))
    expect(canAdd("Jo", "new@c.com", full)).toBe(false)
    expect(canAdd("Jo", "new@c.com", full.slice(1))).toBe(true)
  })
  it("says why sending is blocked, in order of importance", () => {
    const ok = { reportLoaded: true, inProgress: false, contactSet: true, recipients: 1, busy: false }
    expect(sendBlock(ok)).toBeNull()
    expect(sendBlock({ ...ok, reportLoaded: false })).toBe("The report has not loaded.")
    expect(sendBlock({ ...ok, inProgress: true })).toBe("Only a finished month can be emailed.")
    expect(sendBlock({ ...ok, contactSet: false })).toBe("Set the contact address for report questions first.")
    expect(sendBlock({ ...ok, contactSet: false, recipients: 0 })).toBe("Set the contact address for report questions first.")
    expect(sendBlock({ ...ok, recipients: 0 })).toBe("Add a recipient first.")
    expect(sendBlock({ ...ok, busy: true })).toBe("Sending…")
    expect(sendBlock({ ...ok, inProgress: true, recipients: 0 })).toBe("Only a finished month can be emailed.")
  })
  it("words the confirmation and the result without promising delivery", () => {
    expect(confirmText("October 2026", 1)).toBe("Email the October 2026 report to 1 recipient?")
    expect(confirmText("October 2026", 3)).toBe("Email the October 2026 report to 3 recipients?")
    expect(sentText(2, "October 2026")).toBe("Queued for 2 recipients (October 2026). Delivery is not confirmed.")
    expect(sentText(1, "October 2026")).toContain("1 recipient (")
    expect(sentText(1, "October 2026")).not.toMatch(/delivered\b(?!\s+is)/i)
  })
  it("shows the server's reason, or a fallback when there is none or it is odd", () => {
    expect(errorText({ response: { data: { message: "This report was just sent. Try again in a few minutes." } } }, "fb")).toContain("just sent")
    expect(errorText({}, "fb")).toBe("fb")
    expect(errorText(null, "fb")).toBe("fb")
    expect(errorText({ response: { data: { message: "" } } }, "fb")).toBe("fb")
    expect(errorText({ response: { data: { message: { a: 1 } } } }, "fb")).toBe("fb")
    expect(errorText({ response: { data: { message: "x".repeat(300) } } }, "fb")).toBe("fb")
  })
  it("reports refused addresses without hiding the queued ones", () => {
    expect(sentText(2, "October 2026", 0)).not.toContain("refused")
    expect(sentText(2, "October 2026", 1)).toBe("Queued for 2 recipients (October 2026). Delivery is not confirmed. 1 address was refused by the mail service; see the audit log.")
    expect(sentText(1, "October 2026", 3)).toContain("3 addresses were refused")
  })
  it("accepts a contact only with one valid address and a plain phone number", () => {
    expect(validPhone("")).toBe(true)
    expect(validPhone("+27 11 555 0100")).toBe(true)
    for (const bad of ["abc", "1234", "<script>", "+27 11 555 0100 ext x"]) expect(validPhone(bad), bad).toBe(false)
    expect(canSaveContact("", "help@sec.co.za", "")).toBe(true)
    expect(canSaveContact("Ops", "help@sec.co.za", "011 555 0100")).toBe(true)
    expect(canSaveContact("Ops", "bad", "")).toBe(false)
    expect(canSaveContact("x".repeat(101), "help@sec.co.za", "")).toBe(false)
    expect(canSaveContact("Ops", "help@sec.co.za", "abc")).toBe(false)
  })
  it("treats null, empty or malformed settings as no contact", () => {
    expect(validContact(null)).toBe(false)
    expect(validContact({})).toBe(false)
    expect(validContact({ email: "" })).toBe(false)
    expect(validContact({ email: 5 })).toBe(false)
    expect(validContact({ name: null, email: "a@b.co", phone: null })).toBe(true)
  })
})
