import { cn } from '@/lib/utils'
import type { ReactNode } from 'react'

interface TableProps {
  children: ReactNode
  className?: string
}

export function Table({ children, className }: TableProps) {
  return (
    <div className="overflow-x-auto">
      <table className={cn('w-full text-left text-sm', className)}>{children}</table>
    </div>
  )
}

export function TableHead({ children, className }: { children: ReactNode; className?: string }) {
  return <thead className={cn('bg-slate-50 text-xs font-semibold uppercase text-medical-muted', className)}>{children}</thead>
}

export function TableBody({ children, className }: { children: ReactNode; className?: string }) {
  return <tbody className={cn('divide-y divide-medical-border', className)}>{children}</tbody>
}

export function TableRow({ children, className, onClick }: { children: ReactNode; className?: string; onClick?: () => void }) {
  return (
    <tr
      onClick={onClick}
      className={cn('transition-colors hover:bg-slate-50', onClick && 'cursor-pointer', className)}
    >
      {children}
    </tr>
  )
}

export function TableCell({ children, className }: { children: ReactNode; className?: string }) {
  return <td className={cn('px-4 py-3 text-medical-text', className)}>{children}</td>
}

export function TableHeader({ children, className }: { children: ReactNode; className?: string }) {
  return <th className={cn('px-4 py-3', className)}>{children}</th>
}
