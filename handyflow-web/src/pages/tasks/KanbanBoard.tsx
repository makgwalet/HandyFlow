// src/pages/tasks/KanbanBoard.tsx
import { useMemo, useState } from 'react'
import {
  DndContext, DragOverlay, KeyboardSensor, MouseSensor, TouchSensor, closestCorners, pointerWithin, useSensor, useSensors,
  type Announcements, type CollisionDetection, type DragEndEvent, type DragOverEvent, type DragStartEvent,
} from '@dnd-kit/core'
import { arrayMove, sortableKeyboardCoordinates } from '@dnd-kit/sortable'
import type { Column, Task } from './tasks.types'
import { groupByColumn } from './tasks.logic'
import { KanbanColumn } from './KanbanColumn'
import { TaskCardView } from './TaskCard'

interface Props {
  columns: Column[]
  /** Already filtered. */
  tasks: Task[]
  canManage: boolean
  canAdmin: boolean
  onOpen: (task: Task) => void
  /** Move to a column at a position (0 = top). Used by drag and drop and by the card's Move-to menu. */
  onMove: (taskId: string, columnId: string, index: number) => void
  onComplete: (task: Task) => void
  onQuickAdd: (columnId: string, title: string) => Promise<unknown>
  onEditLists: () => void
}

type Items = Record<string, string[]>

// Pointer position first (accurate for tall columns), nearest corners as the fallback (keyboard, gaps).
const collisionDetection: CollisionDetection = args => {
  const hits = pointerWithin(args)
  return hits.length > 0 ? hits : closestCorners(args)
}

export function KanbanBoard({ columns, tasks, canManage, canAdmin, onOpen, onMove, onComplete, onQuickAdd, onEditLists }: Props) {
  const grouped = useMemo(() => groupByColumn(tasks, columns), [tasks, columns])
  const baseItems: Items = useMemo(
    () => Object.fromEntries(Object.entries(grouped).map(([id, list]) => [id, list.map(t => t.id)])), [grouped])
  const taskById = useMemo(() => new Map(tasks.map(t => [t.id, t])), [tasks])
  const columnById = useMemo(() => new Map(columns.map(c => [c.id, c])), [columns])

  // While a card is being dragged, `dragItems` is the live arrangement; otherwise the board follows the data.
  const [dragItems, setDragItems] = useState<Items | null>(null)
  const [activeId, setActiveId] = useState<string | null>(null)
  const items = dragItems ?? baseItems
  const activeTask = activeId ? taskById.get(activeId) ?? null : null

  const sensors = useSensors(
    useSensor(MouseSensor, { activationConstraint: { distance: 6 } }),          // a click still opens the card
    useSensor(TouchSensor, { activationConstraint: { delay: 220, tolerance: 8 } }), // long-press, so scrolling still works
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
      // Space lifts and drops; Enter is left free so it can open the card
      keyboardCodes: { start: ['Space'], cancel: ['Escape'], end: ['Space'] },
    }),
  )

  const findContainer = (list: Items, id: string): string | undefined =>
    id in list ? id : Object.keys(list).find(col => list[col].includes(id))

  const titleOf = (id: string | number) => taskById.get(String(id))?.title ?? 'task'
  const placeOf = (id: string | number) => {
    const key = String(id)
    const col = columnById.get(key) ?? columnById.get(findContainer(items, key) ?? '')
    return col ? col.name : 'the board'
  }
  const announcements: Announcements = {
    onDragStart: ({ active }) => `Picked up ${titleOf(active.id)}. Use the arrow keys to move it, Space to drop, Escape to cancel.`,
    onDragOver: ({ active, over }) => (over ? `${titleOf(active.id)} is over ${placeOf(over.id)}.` : undefined),
    onDragEnd: ({ active, over }) => (over ? `${titleOf(active.id)} was dropped in ${placeOf(over.id)}.` : `${titleOf(active.id)} was dropped.`),
    onDragCancel: ({ active }) => `Moving ${titleOf(active.id)} was cancelled.`,
  }

  const handleDragStart = (e: DragStartEvent) => { setActiveId(String(e.active.id)); setDragItems(baseItems) }

  const handleDragOver = ({ active, over }: DragOverEvent) => {
    if (!over) return
    const activeKey = String(active.id), overKey = String(over.id)
    setDragItems(prev => {
      const cur = prev ?? baseItems
      const from = findContainer(cur, activeKey), to = findContainer(cur, overKey)
      if (!from || !to || from === to) return cur
      const target = cur[to]
      const overIndex = target.indexOf(overKey)
      let index: number
      if (overKey in cur) index = target.length                     // over an (empty or whole) column: append
      else {
        const translated = active.rect.current.translated
        const below = !!translated && translated.top > over.rect.top + over.rect.height / 2
        index = overIndex + (below ? 1 : 0)
      }
      return { ...cur, [from]: cur[from].filter(i => i !== activeKey), [to]: [...target.slice(0, index), activeKey, ...target.slice(index)] }
    })
  }

  const handleDragEnd = ({ active, over }: DragEndEvent) => {
    const working = dragItems ?? baseItems
    setActiveId(null); setDragItems(null)
    if (!over) return
    const activeKey = String(active.id), overKey = String(over.id)
    const toColumn = findContainer(working, activeKey)
    if (!toColumn) return

    let order = working[toColumn]
    const oldIndex = order.indexOf(activeKey), newIndex = order.indexOf(overKey)
    if (newIndex >= 0 && oldIndex !== newIndex) order = arrayMove(order, oldIndex, newIndex)   // reorder within a column
    const index = order.indexOf(activeKey)

    const original = taskById.get(activeKey)
    if (original && original.columnId === toColumn && baseItems[toColumn].indexOf(activeKey) === index) return  // dropped where it was
    onMove(activeKey, toColumn, index)
  }

  const handleDragCancel = () => { setActiveId(null); setDragItems(null) }

  return (
    <DndContext sensors={sensors} collisionDetection={collisionDetection} accessibility={{ announcements }}
      onDragStart={handleDragStart} onDragOver={handleDragOver} onDragEnd={handleDragEnd} onDragCancel={handleDragCancel}>
      <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start', overflowX: 'auto', paddingBottom: 14, minHeight: 240 }}>
        {columns.map(col => (
          <KanbanColumn key={col.id} column={col} columns={columns} canManage={canManage} canAdmin={canAdmin}
            tasks={(items[col.id] ?? []).map(id => taskById.get(id)).filter((t): t is Task => !!t)}
            onOpen={onOpen} onMoveTo={(t, columnId) => onMove(t.id, columnId, (baseItems[columnId] ?? []).length)}
            onComplete={onComplete} onQuickAdd={onQuickAdd} onEditLists={onEditLists} />
        ))}
      </div>
      <DragOverlay dropAnimation={{ duration: 180, easing: 'ease' }}>
        {activeTask ? <TaskCardView task={activeTask} columns={columns} canManage={canManage} menu={false}
          onOpen={() => {}} onMoveTo={() => {}} onComplete={() => {}} lifted /> : null}
      </DragOverlay>
    </DndContext>
  )
}
