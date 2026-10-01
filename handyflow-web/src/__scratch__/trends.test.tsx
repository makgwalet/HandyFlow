import React from 'react'
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, within, waitFor, fireEvent, cleanup } from '@testing-library/react'
import { MemoryRouter, useLocation } from 'react-router-dom'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

const api = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('../api/client', () => ({ apiClient: api }))
vi.mock('recharts', async () => {
  const m = await vi.importActual<typeof import('recharts')>('recharts')
  // jsdom has no layout, so ResponsiveContainer would render nothing: give the chart a fixed size
  return { ...m, ResponsiveContainer: ({ children }: { children: React.ReactElement<{ width?: number; height?: number }> }) => React.cloneElement(children, { width: 600, height: 250 }) }
})

import AgTrendsTab from '../pages/agriculture/AgTrendsTab'
import { costChartData, deltaText, deltaTone, formatComparison, hasCosts, hasHarvest, hasLivestockEvents, livestockChartData, monthLabel, tonnesChartData } from '../pages/agriculture/agTrends.logic'
import type { AgTrends, Comparison } from '../pages/agriculture/agTrends.api'

afterEach(() => cleanup())
const nb = (s: string | null) => (s ?? '').replace(/[\u00a0\u202f]/g, ' ')
const c = (key: string, current: number, previous: number, pct: number | null, unit = 'R'): Comparison =>
  ({ key, label: key, unit, current, previous, changePercent: pct, currentFrom: '2026-09-16', currentTo: '2026-10-15', previousFrom: '2026-08-17', previousTo: '2026-09-15' })

describe('trend helpers', () => {
  it('month labels do not depend on the browser locale', () => {
    expect(monthLabel('2026-10')).toBe('Oct 26'); expect(monthLabel('2025-01')).toBe('Jan 25'); expect(monthLabel('2027-12')).toBe('Dec 27')
  })
  it('direction is judged per measure: costs and deaths rising is bad, harvest and births rising is good', () => {
    expect(deltaTone(c('TOTAL_COST', 120, 100, 20))).toBe('bad'); expect(deltaTone(c('TOTAL_COST', 80, 100, -20))).toBe('good')
    expect(deltaTone(c('HARVEST_TONNES', 12, 10, 20, 't'))).toBe('good'); expect(deltaTone(c('HARVEST_TONNES', 8, 10, -20, 't'))).toBe('bad')
    expect(deltaTone(c('DEATHS', 5, 1, 400, 'head'))).toBe('bad'); expect(deltaTone(c('DEATHS', 0, 3, -100, 'head'))).toBe('good')
    expect(deltaTone(c('BIRTHS', 6, 3, 100, 'head'))).toBe('good'); expect(deltaTone(c('BIRTHS', 1, 3, -66.7, 'head'))).toBe('bad')
    expect(deltaTone(c('HIGH_SEVERITY_SCOUTING', 3, 1, 200, 'findings'))).toBe('bad')
    expect(deltaTone(c('CROP_COST', 90, 100, -10))).toBe('good'); expect(deltaTone(c('LIVESTOCK_COST', 110, 100, 10))).toBe('bad')
  })
  it('no change is flat, nothing to compare against has no tone, and an unknown measure is never judged', () => {
    expect(deltaTone(c('TOTAL_COST', 100, 100, 0))).toBe('flat'); expect(deltaTone(c('TOTAL_COST', 0, 0, null))).toBe('none')
    expect(deltaTone(c('BIRTHS', 4, 0, null, 'head'))).toBe('none'); expect(deltaTone(c('SOMETHING_NEW', 5, 1, 400))).toBe('flat')
  })
  it('delta wording: signed percentages with a real minus, and plain words when there is no percentage', () => {
    expect(deltaText(c('X', 120, 100, 20))).toBe('+20%'); expect(deltaText(c('X', 112.5, 100, 12.5))).toBe('+12.5%')
    expect(deltaText(c('X', 75, 100, -25))).toBe('\u221225%'); expect(deltaText(c('X', 4, 0, null))).toBe('No earlier data')
    expect(deltaText(c('X', 0, 0, null))).toBe('No data yet'); expect(deltaText(c('X', 7, 7, 0))).toBe('No change')
  })
  it('values are formatted in their own unit', () => {
    expect(nb(formatComparison({ unit: 'R' }, 12345.5))).toMatch(/12 345,50/); expect(nb(formatComparison({ unit: 't' }, 5.8))).toBe('5,8 t')
    expect(formatComparison({ unit: 'head' }, 12)).toBe('12 head'); expect(formatComparison({ unit: 'findings' }, 3)).toBe('3')
  })
})

const months = [{ key: '2026-08', start: '2026-08-01', end: '2026-08-31', partial: false }, { key: '2026-09', start: '2026-09-01', end: '2026-09-30', partial: false }, { key: '2026-10', start: '2026-10-01', end: '2026-10-15', partial: true }]
const trends = (o: Partial<AgTrends> = {}): AgTrends => ({
  asOf: '2026-10-15', farmId: null, months,
  costs: [{ month: '2026-08', seed: 0, inputs: 0, feed: 10, health: 0, animalPurchases: 1000, total: 1010 }, { month: '2026-09', seed: 100, inputs: 250.5, feed: 20, health: 0, animalPurchases: 0, total: 370.5 }, { month: '2026-10', seed: 0, inputs: 40, feed: 0, health: 5, animalPurchases: 0, total: 45 }],
  production: { tonnes: [{ month: '2026-08', tonnes: 0 }, { month: '2026-09', tonnes: 5.8 }, { month: '2026-10', tonnes: 1.5 }], excludedRecords: 1,
    byCrop: [{ cropTypeId: 'b', cropName: 'Dry beans', unit: 'bags', values: [0, 0, 50], total: 50, excludedRecords: 0 }, { cropTypeId: 'm', cropName: 'Maize', unit: 't', values: [0, 5.8, 0.8], total: 6.6, excludedRecords: 2 }] },
  livestock: [{ month: '2026-08', births: 0, deaths: 0, estimatedLoss: 0 }, { month: '2026-09', births: 3, deaths: 6, estimatedLoss: 2500.5 }, { month: '2026-10', births: 1, deaths: 2, estimatedLoss: 100 }],
  comparisons: [c('TOTAL_COST', 150, 30, 400), c('CROP_COST', 100, 10, 900), c('LIVESTOCK_COST', 50, 20, 150), c('HARVEST_TONNES', 6, 3, 100, 't'), c('BIRTHS', 2, 0, null, 'head'), c('DEATHS', 1, 4, -75, 'head'), c('HIGH_SEVERITY_SCOUTING', 1, 2, -50, 'findings')],
  limitations: ['Herd size over time is not recorded, so livestock trends show births and deaths, not the number of animals.', 'Revenue, labour cost and equipment cost are not recorded, so there is no margin trend.'],
  ...o,
})

describe('trend chart data', () => {
  it('lines up with the months and marks the current one with an asterisk', () => {
    const t = trends()
    expect(costChartData(t).map(r => r.name)).toEqual(['Aug 26', 'Sep 26', 'Oct 26*'])
    expect(costChartData(t)[1]).toMatchObject({ Seed: 100, Inputs: 250.5, Feed: 20, Health: 0, 'Animal purchases': 0 })
    expect(tonnesChartData(t).map(r => r.Tonnes)).toEqual([0, 5.8, 1.5]); expect(livestockChartData(t)[1]).toMatchObject({ Births: 3, Deaths: 6 })
  })
  it('knows when a chart has nothing to show', () => {
    const empty = trends({ costs: trends().costs.map(x => ({ ...x, total: 0 })), production: { tonnes: [{ month: '2026-10', tonnes: 0 }], byCrop: [], excludedRecords: 0 }, livestock: [{ month: '2026-10', births: 0, deaths: 0, estimatedLoss: 0 }] })
    expect([hasCosts(empty), hasHarvest(empty), hasLivestockEvents(empty)]).toEqual([false, false, false]); expect([hasCosts(trends()), hasHarvest(trends()), hasLivestockEvents(trends())]).toEqual([true, true, true])
  })
})

const FARMS = [{ id: 'f1', name: 'Green Valley', status: 'ACTIVE' }, { id: 'f2', name: 'Riverside', status: 'ACTIVE' }]
let current: AgTrends
let failTrends = false
const Where = () => { const l = useLocation(); return <div data-testid="where">{l.pathname}{l.search}</div> }
const wrap = (url = '/agriculture/trends') => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter initialEntries={[url]}><AgTrendsTab /><Where /></MemoryRouter></QueryClientProvider>)
beforeEach(() => {
  vi.clearAllMocks(); current = trends(); failTrends = false
  api.get.mockImplementation(async (url: string) => {
    if (url.endsWith('/trends')) { if (failTrends) throw new Error('boom'); return { data: current } }
    return { data: { content: FARMS, totalElements: 2 } }
  })
})
const trendCalls = () => api.get.mock.calls.filter(x => String(x[0]).endsWith('/trends')).map(x => x[1].params)

describe('Trends page', () => {
  it('starts with all farms and twelve months, in one call', async () => {
    wrap(); await screen.findByLabelText('TOTAL_COST')
    expect(trendCalls()).toEqual([{ farmId: undefined, months: 12 }])
    expect((screen.getByLabelText('Farm') as HTMLSelectElement).value).toBe('')
    expect(within(screen.getByLabelText('Farm')).getByText('All farms')).toBeTruthy()
  })
  it('shows each 30-day comparison with its direction and the earlier figure', async () => {
    wrap(); const total = within(await screen.findByLabelText('TOTAL_COST'))
    expect(total.getByText('+400%')).toBeTruthy(); expect(nb(total.getByText(/was R/).textContent)).toContain('30,00')
    expect(within(screen.getByLabelText('DEATHS')).getByText('\u221275%').getAttribute('data-tone')).toBe('good')       // fewer deaths is good news
    expect(within(screen.getByLabelText('TOTAL_COST')).getByText('+400%').getAttribute('data-tone')).toBe('bad')        // higher cost is bad news
    expect(within(screen.getByLabelText('BIRTHS')).getByText('No earlier data')).toBeTruthy()
    expect(screen.getByText(/2026-09-16 to 2026-10-15/)).toBeTruthy()
  })
  it('choosing a farm updates the address and asks for that farm; choosing All farms clears it', async () => {
    wrap(); await screen.findByLabelText('TOTAL_COST')
    fireEvent.change(screen.getByLabelText('Farm'), { target: { value: 'f2' } })
    await waitFor(() => expect(trendCalls().at(-1)).toEqual({ farmId: 'f2', months: 12 })); expect(screen.getByTestId('where').textContent).toBe('/agriculture/trends?farm=f2')
    fireEvent.change(screen.getByLabelText('Farm'), { target: { value: '' } })
    await waitFor(() => expect(trendCalls().at(-1)).toEqual({ farmId: undefined, months: 12 })); expect(screen.getByTestId('where').textContent).toBe('/agriculture/trends')
  })
  it('an address with a farm opens on that farm; an unknown farm falls back to all', async () => {
    wrap('/agriculture/trends?farm=f1'); await waitFor(() => expect((screen.getByLabelText('Farm') as HTMLSelectElement).value).toBe('f1')); expect(trendCalls().at(-1)).toEqual({ farmId: 'f1', months: 12 })
    cleanup(); vi.clearAllMocks(); wrap('/agriculture/trends?farm=nope'); await screen.findByLabelText('TOTAL_COST')
    expect(trendCalls().every(p => p.farmId === undefined)).toBe(true)
  })
  it('the period can be changed', async () => {
    wrap(); await screen.findByLabelText('TOTAL_COST'); fireEvent.change(screen.getByLabelText('Period'), { target: { value: '24' } })
    await waitFor(() => expect(trendCalls().at(-1)).toEqual({ farmId: undefined, months: 24 }))
    expect(within(screen.getByLabelText('Period')).getAllByRole('option').map(o => o.textContent)).toEqual(['Last 6 months', 'Last 12 months', 'Last 24 months'])
  })
  it('draws the three charts when there is data', async () => {
    wrap(); await screen.findByLabelText('Harvested (tonnes)')
    for (const name of ['Harvested (tonnes)', 'Costs by month', 'Births and deaths']) expect(screen.getByLabelText(name).querySelector('.recharts-wrapper'), name).toBeTruthy()
    expect(screen.queryByText(/recorded in this period/)).toBeNull()
  })
  it('each chart explains itself when there is nothing to draw', async () => {
    current = trends({ costs: trends().costs.map(x => ({ ...x, total: 0 })), production: { tonnes: [{ month: '2026-10', tonnes: 0 }], byCrop: [], excludedRecords: 0 }, livestock: [{ month: '2026-10', births: 0, deaths: 0, estimatedLoss: 0 }] })
    wrap(); expect((await screen.findAllByText('No harvests recorded in this period.')).length).toBe(2)         // the tonnes chart and the crop table
    expect(screen.getByText('No costs recorded in this period.')).toBeTruthy(); expect(screen.getByText('No births or deaths recorded in this period.')).toBeTruthy()
    expect(document.querySelector('.recharts-wrapper')).toBeNull()
  })
  it('explains the asterisk on the current month', async () => {
    wrap(); expect(await screen.findByText(/\* The current month, so far \(Oct 26 to date\)/)).toBeTruthy()
  })
  it('harvest by crop: each in its own unit, with a warning where records were left out', async () => {
    wrap(); const table = within(await screen.findByLabelText('Harvest by crop'))
    const rows = table.getAllByRole('row').slice(1).map(r => within(r).getAllByRole('cell').map(x => nb(x.textContent)))
    expect(rows[0].slice(0, 4)).toEqual(['Dry beans', 'bags', '50', '50']); expect(rows[1].slice(0, 4)).toEqual(['Maize', 't', '6,6', '0,8'])
    expect(rows[1][4]).toContain("2 harvest(s) in a unit that can't convert are not counted"); expect(rows[0][4]).toBe('')
    expect(table.getByText(/never added together across units/)).toBeTruthy()
  })
  it('shows the limitations the server reports, so nobody reads herd size or margin into it', async () => {
    wrap(); const box = within(await screen.findByLabelText('What is not shown'))
    expect(box.getByText(/Herd size over time is not recorded/)).toBeTruthy(); expect(box.getByText(/no margin trend/)).toBeTruthy()
  })
  it('loading state, then a failure offers a retry that asks again', async () => {
    failTrends = true; wrap(); expect(screen.getByText('Loading trends…')).toBeTruthy()
    expect((await screen.findByRole('alert')).textContent).toContain("couldn't load the trends")
    failTrends = false; fireEvent.click(screen.getByRole('button', { name: 'Try again' })); expect(await screen.findByLabelText('TOTAL_COST')).toBeTruthy()
  })
})
