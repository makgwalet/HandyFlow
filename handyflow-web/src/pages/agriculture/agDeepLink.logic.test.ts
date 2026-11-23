import { describe, it, expect } from "vitest"
import { parseFarmLink } from "./agDeepLink.logic"

const FARM = "11111111-1111-1111-1111-111111111111"
const X = "22222222-2222-2222-2222-222222222222"

describe("parseFarmLink", () => {
  it("needs a farm", () => {
    expect(parseFarmLink("")).toBeNull()
    expect(parseFarmLink("?animal=" + X)).toBeNull()
    expect(parseFarmLink("?farm=not-a-uuid")).toBeNull()
    expect(parseFarmLink("?farm=" + FARM + "x")).toBeNull()
  })
  it("reads a farm on its own", () => {
    expect(parseFarmLink("?farm=" + FARM)).toEqual({ farmId: FARM, tab: null, animalId: null, groupId: null })
  })
  it("reads an animal and a group", () => {
    expect(parseFarmLink(`?farm=${FARM}&animal=${X}`)).toEqual({ farmId: FARM, tab: null, animalId: X, groupId: null })
    expect(parseFarmLink(`?farm=${FARM}&group=${X}`)).toEqual({ farmId: FARM, tab: null, animalId: null, groupId: X })
  })
  it("prefers the animal when both are given", () => {
    const other = "33333333-3333-3333-3333-333333333333"
    expect(parseFarmLink(`?farm=${FARM}&animal=${X}&group=${other}`)).toMatchObject({ animalId: X, groupId: null })
  })
  it("ignores an id that is not a UUID", () => {
    expect(parseFarmLink(`?farm=${FARM}&animal=../../x`)).toEqual({ farmId: FARM, tab: null, animalId: null, groupId: null })
  })
  it("accepts only known tabs", () => {
    expect(parseFarmLink(`?farm=${FARM}&tab=inventory`)?.tab).toBe("inventory")
    expect(parseFarmLink(`?farm=${FARM}&tab=crops`)?.tab).toBe("crops")
    expect(parseFarmLink(`?farm=${FARM}&tab=admin`)?.tab).toBeNull()
  })
  it("is case-insensitive about the UUID and gives it back in lower case", () => {
    expect(parseFarmLink("?farm=" + FARM.toUpperCase())?.farmId).toBe(FARM)
  })
})
