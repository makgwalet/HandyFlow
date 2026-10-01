import { describe, it, expect } from 'vitest'
import { STEPS, addDays, allowedActions, canLog, cropName, cycleKpis, cycleLabel, distinctUnits, harvestUnitProblem, isHarvestOverdue, isTerminal,
  statusCounts, stepIndex, stockProblem, suggestExpectedHarvest, suggestInputCost, toCsv, todayISO } from '../pages/agriculture/agCrops.logic'
import { canConvertUnit, canonicalUnit, convertUnit, isMassUnit, sumHarvests } from '../pages/agriculture/agUnits'
import type { CropCycle, CropType, CycleStatus } from '../pages/agriculture/agCrops.types'

const cycle = (o: Partial<CropCycle> = {}): CropCycle => ({ id: 'c', farmId: 'f', productionAreaId: 'a', enterpriseId: null, seasonId: null, cropTypeId: 't1', variety: null, cycleName: null,
  areaPlantedHectares: 10, plantingDate: null, expectedHarvestDate: null, seedInventoryItemId: null, seedQuantity: null, seedSource: null, status: 'PLANNED', notes: null, createdAt: '', updatedAt: '', ...o })
const TYPES: CropType[] = [{ id: 't1', name: 'Maize', category: 'CEREAL', typicalGrowingDays: 150, defaultUnitOfMeasure: 't', status: 'ACTIVE', createdAt: '', updatedAt: '' }]
const ALL: CycleStatus[] = ['PLANNED', 'PLANTED', 'GROWING', 'HARVESTING', 'HARVESTED', 'FAILED', 'ABANDONED']

describe('lifecycle rules (mirror AgCropCycle; fail/abandon are stricter than the server)', () => {
  it('offers exactly the transitions the domain allows', () => {
    const on = (s: CycleStatus) => Object.entries(allowedActions(s)).filter(([k, v]) => v && k !== 'edit').map(([k]) => k).sort()
    expect(on('PLANNED')).toEqual(['abandon', 'recordPlanting'])
    expect(on('PLANTED')).toEqual(['abandon', 'fail', 'markGrowing', 'startHarvest'])
    expect(on('GROWING')).toEqual(['abandon', 'fail', 'startHarvest'])
    expect(on('HARVESTING')).toEqual(['abandon', 'completeHarvest', 'fail'])
    for (const s of ['HARVESTED', 'FAILED', 'ABANDONED'] as const) expect(on(s)).toEqual([])
  })
  it('the server accepts fail/abandon in ANY status; the UI never offers them on a finished cycle', () => {
    expect(allowedActions('HARVESTED').fail || allowedActions('HARVESTED').abandon).toBe(false)
  })
  it('stepper index; exits are not steps; "COMPLETED" is not a status', () => {
    expect(STEPS.map(stepIndex)).toEqual([0, 1, 2, 3, 4]); expect(stepIndex('FAILED')).toBe(-1); expect(STEPS).not.toContain('COMPLETED' as never)
    expect(ALL.filter(isTerminal)).toEqual(['HARVESTED', 'FAILED', 'ABANDONED'])
  })
  it('what can be logged when', () => {
    expect(canLog('input', 'PLANNED')).toBe(true); expect(canLog('input', 'FAILED')).toBe(false)
    expect(canLog('harvest', 'PLANNED')).toBe(false); expect(canLog('harvest', 'GROWING')).toBe(true); expect(canLog('harvest', 'HARVESTED')).toBe(true); expect(canLog('scouting', 'ABANDONED')).toBe(false)
  })
})

describe('dates', () => {
  it('addDays is timezone-proof and handles month, year and leap boundaries', () => {
    expect(addDays('2026-01-31', 1)).toBe('2026-02-01'); expect(addDays('2026-12-31', 1)).toBe('2027-01-01')
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29'); expect(addDays('2026-03-01', -1)).toBe('2026-02-28')
  })
  it('expected harvest = planting + typical growing days; unknown inputs give null', () => {
    expect(suggestExpectedHarvest('2026-10-15', 150)).toBe('2027-03-14')
    expect(suggestExpectedHarvest('', 150)).toBeNull(); expect(suggestExpectedHarvest('2026-10-15', null)).toBeNull(); expect(suggestExpectedHarvest('2026-10-15', 0)).toBeNull()
  })
  it('harvest is overdue only while PLANTED/GROWING and past the expected date', () => {
    const c = (status: CycleStatus, d: string | null) => isHarvestOverdue({ status, expectedHarvestDate: d }, '2026-10-01')
    expect(c('GROWING', '2026-09-30')).toBe(true); expect(c('GROWING', '2026-10-01')).toBe(false)
    expect(c('HARVESTING', '2026-09-01')).toBe(false); expect(c('HARVESTED', '2026-09-01')).toBe(false); expect(c('GROWING', null)).toBe(false)
  })
  it('today is the Johannesburg day', () => expect(todayISO(new Date('2026-09-30T23:30:00Z'))).toBe('2026-10-01'))
})

describe('labels', () => {
  it('cycleName is optional on the server, so fall back to crop and variety', () => {
    expect(cycleLabel(cycle({ cycleName: 'Field 3 maize' }), TYPES)).toBe('Field 3 maize')
    expect(cycleLabel(cycle({ variety: 'PAN 6767' }), TYPES)).toBe('Maize, PAN 6767')
    expect(cycleLabel(cycle(), TYPES)).toBe('Maize'); expect(cropName(cycle({ cropTypeId: 'gone' }), TYPES)).toBe('Unknown crop')
  })
})

describe('KPIs', () => {
  const list = [cycle({ status: 'GROWING', areaPlantedHectares: 126 }), cycle({ status: 'PLANTED', areaPlantedHectares: 80.5 }), cycle({ status: 'HARVESTING', areaPlantedHectares: 22 }),
    cycle({ status: 'PLANNED', areaPlantedHectares: 64 }), cycle({ status: 'FAILED', areaPlantedHectares: 40 }), cycle({ status: 'HARVESTED', areaPlantedHectares: 30 })]
  it('area in production counts only PLANTED, GROWING and HARVESTING', () => {
    expect(cycleKpis(list)).toEqual({ total: 6, active: 3, planned: 1, harvesting: 1, areaInProduction: 228.5 })
  })
  it('status counts omit empty statuses', () => expect(statusCounts(list).map(x => x.status)).toEqual(['PLANNED', 'PLANTED', 'GROWING', 'HARVESTING', 'HARVESTED', 'FAILED']))
})

describe('checks the backend does not make', () => {
  it('input cost is prefilled from unit cost x quantity (the server stores whatever is sent)', () => {
    expect(suggestInputCost(200, 42.5)).toBe(8500); expect(suggestInputCost(3, 0.333)).toBe(1)
    expect(suggestInputCost(0, 10)).toBeNull(); expect(suggestInputCost(5, null)).toBeNull(); expect(suggestInputCost(NaN, 5)).toBeNull()
  })
  it('over-issuing is caught before the 409', () => {
    const item = { itemName: 'Maize seed', currentQuantity: 30, unitOfMeasure: 'kg' }
    expect(stockProblem(50, item)).toBe('Only 30 kg of Maize seed in stock.'); expect(stockProblem(30, item)).toBeNull(); expect(stockProblem(5, undefined)).toBeNull()
  })
  it('the harvest unit must convert to the crop unit: kg and t mix, kg and bags do not (the server rejects it)', () => {
    expect(harvestUnitProblem('t', 't', [])).toBeNull(); expect(harvestUnitProblem(' T ', 't', [])).toBeNull()
    expect(harvestUnitProblem('kg', 't', [])).toBeNull(); expect(harvestUnitProblem('Tonnes', 'kg', [])).toBeNull()
    expect(harvestUnitProblem('bags', 't', [])).toMatch(/can't be converted to t.*Use a mass unit such as kg or t/)
    expect(harvestUnitProblem('kg', 'bales', [])).toMatch(/Use bales\./)
    expect(harvestUnitProblem('kg', null, [{ unitOfMeasure: 'bags' }])).toMatch(/converted to bags/)    // no crop unit: fall back to the cycle's own
    expect(harvestUnitProblem('kg', null, [])).toBeNull(); expect(harvestUnitProblem('', 't', [])).toBeNull()
    expect(distinctUnits([{ unitOfMeasure: 't' }, { unitOfMeasure: 't ' }, { unitOfMeasure: 'kg' }])).toEqual(['t', 'kg'])
  })
})

describe('toCsv', () => {
  it('escapes commas, quotes and newlines; null becomes empty', () => {
    expect(toCsv([['a', 'b,c', 'say "hi"', null, 3], ['x\ny', undefined]])).toBe('a,"b,c","say ""hi""",,3\r\n"x\ny",')
  })
})


describe('unit conversion mirrors the backend AgUnits', () => {
  it('aliases fold to one canonical unit', () => {
    expect(['Tonnes', ' T ', 'tons'].map(canonicalUnit)).toEqual(['t', 't', 't']); expect(canonicalUnit('KG')).toBe('kg'); expect(canonicalUnit(' Bags ')).toBe('bags'); expect(canonicalUnit(null)).toBe('')
    expect(isMassUnit('lbs')).toBe(true); expect(isMassUnit('bags')).toBe(false)
  })
  it('converts mass both ways and rounds to six decimals like the server', () => {
    expect(convertUnit(800, 'kg', 't')).toBe(0.8); expect(convertUnit(5, 't', 'kg')).toBe(5000); expect(convertUnit(1500, 'g', 'kg')).toBe(1.5)
    expect(convertUnit(1, 'lb', 'kg')).toBe(0.453592); expect(convertUnit(1, 'g', 't')).toBe(0.000001)
    expect(convertUnit(12.5, 'bags', 'Bags')).toBe(12.5)
  })
  it('refuses incompatible or missing units', () => {
    expect(convertUnit(10, 'bags', 't')).toBeNull(); expect(convertUnit(10, '', 't')).toBeNull(); expect(convertUnit(10, 'kg', null)).toBeNull()
    expect(canConvertUnit('bags', 't')).toBe(false); expect(canConvertUnit('bags', 'bags')).toBe(true); expect(canConvertUnit('', '')).toBe(false)
  })
  it('sums yield in the crop unit; unconvertible units are left out and listed', () => {
    expect(sumHarvests('t', [{ unitOfMeasure: 't', quantityHarvested: 5 }, { unitOfMeasure: 'kg', quantityHarvested: 800 }])).toEqual({ total: 5.8, unconvertedUnits: [] })
    expect(sumHarvests('t', [{ unitOfMeasure: 't', quantityHarvested: 5 }, { unitOfMeasure: 'bags', quantityHarvested: 40 }, { unitOfMeasure: 'Bags', quantityHarvested: 10 }]))
      .toEqual({ total: 5, unconvertedUnits: expect.arrayContaining(['bags']) })
    expect(sumHarvests(null, [{ unitOfMeasure: 'kg', quantityHarvested: 3 }, { unitOfMeasure: 't', quantityHarvested: 4 }]).unconvertedUnits).toHaveLength(1)
    expect(sumHarvests('t', []).total).toBe(0)
  })
})
