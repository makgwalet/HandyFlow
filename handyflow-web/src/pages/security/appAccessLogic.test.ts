import { describe, it, expect } from 'vitest'
import { chunkIds, csvField, mergeBatches, outcomeLabel, pinSheetCsv, pinValid, summaryText } from './appAccessLogic'

describe('chunkIds', () => {
  it('splits into batches of 100 and drops duplicates and blanks', () => {
    const ids = Array.from({ length: 250 }, (_, i) => `g${i}`)
    const c = chunkIds([...ids, 'g1', ''])
    expect(c.map(x => x.length)).toEqual([100, 100, 50])
  })
  it('nothing in, nothing out', () => expect(chunkIds([])).toEqual([]))
})
describe('mergeBatches / summaryText', () => {
  it('adds up counts and keeps every row', () => {
    const m = mergeBatches([
      { provisioned: 2, skipped: 1, failed: 0, results: [{ guardId: 'a', outcome: 'PROVISIONED' }, { guardId: 'b', outcome: 'PROVISIONED' }, { guardId: 'c', outcome: 'SKIPPED_NOT_ACTIVE' }] },
      { provisioned: 1, skipped: 0, failed: 1, results: [{ guardId: 'd', outcome: 'PROVISIONED' }, { guardId: 'e', outcome: 'FAILED' }] }])
    expect([m.provisioned, m.skipped, m.failed, m.results.length]).toEqual([3, 1, 1, 5])
    expect(summaryText(m)).toBe('3 given access, 1 skipped, 1 failed.')
    expect(summaryText({ provisioned: 4, skipped: 0, failed: 0, results: [] })).toBe('4 given access.')
  })
})
describe('csv', () => {
  it('quotes commas and quotes, defuses formulas', () => {
    expect(csvField('a,b')).toBe('"a,b"')
    expect(csvField('say "hi"')).toBe('"say ""hi"""')
    expect(csvField('=HYPERLINK("x")')).toBe('"\'=HYPERLINK(""x"")"')
    expect(csvField('+27 82')).toBe("'+27 82")
    expect(csvField(null)).toBe('')
  })
  it('the PIN sheet lists only guards who received a PIN', () => {
    const csv = pinSheetCsv([
      { guardId: 'a', fullName: 'Thabo, M', employeeCode: 'G1', phone: '0821234567', outcome: 'PROVISIONED', temporaryPin: '482915' },
      { guardId: 'b', fullName: 'Neo', outcome: 'SKIPPED_ALREADY_ENROLLED', temporaryPin: null },
      { guardId: 'c', fullName: 'Sam', outcome: 'FAILED' }])
    expect(csv).toBe('Name,Employee code,Phone,Temporary PIN\r\n"Thabo, M",G1,0821234567,482915\r\n')
  })
})
describe('labels and pin rule', () => {
  it('labels outcomes', () => { expect(outcomeLabel('PROVISIONED')).toBe('Access given'); expect(outcomeLabel('SKIPPED_NOT_ACTIVE')).toBe('Not active'); expect(outcomeLabel('X')).toBe('X') })
  it('blank or six digits', () => { expect(pinValid('')).toBe(true); expect(pinValid('123456')).toBe(true); expect(pinValid('12345')).toBe(false); expect(pinValid('12345a')).toBe(false) })
})
