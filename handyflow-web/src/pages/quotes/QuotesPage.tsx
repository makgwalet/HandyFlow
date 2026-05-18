// src/pages/quotes/QuotesPage.tsx

import { useQuery } from '@tanstack/react-query'
import { Plus, Eye } from 'lucide-react'
import { Link } from 'react-router-dom'
import { invoicingApi } from '../../api/invoicing.api'
import { Button } from '../../components/ui/Button'
import { Card } from '../../components/ui/Card'
import { Badge } from '../../components/ui/Badge'
import { PageHeader } from '../../components/ui/PageHeader'
import type { BadgeVariant } from '../../components/ui/Badge'

function quoteStatusVariant(status: string): BadgeVariant {
  const map: Record<string, BadgeVariant> = {
    DRAFT: 'default', SENT: 'info', ACCEPTED: 'success',
    REJECTED: 'danger', EXPIRED: 'warning', INVOICED: 'purple',
  }
  return map[status] ?? 'default'
}

export function QuotesPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['quotes'],
    queryFn: () => invoicingApi.getQuotes(),
  })

  return (
    <div>
      <PageHeader
        title="Quotes"
        subtitle="Create and manage your client quotes"
        action={
          <Link to="/quotes/new">
            <Button size="sm">
              <Plus className="w-4 h-4 mr-1" />
              New Quote
            </Button>
          </Link>
        }
      />

      <Card padding={false}>
        {isLoading ? (
          <div className="p-6 text-center text-gray-500">Loading...</div>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-gray-100">
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Quote #</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Title</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Status</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Total</th>
                <th className="text-left px-6 py-3 text-xs font-medium text-gray-500 uppercase">Expires</th>
                <th className="px-6 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {data?.content.map((quote) => (
                <tr key={quote.id} className="hover:bg-gray-50">
                  <td className="px-6 py-4">
                    <span className="font-mono text-sm font-medium text-blue-600">
                      {quote.quoteNumber}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <p className="font-medium text-gray-900">{quote.title}</p>
                  </td>
                  <td className="px-6 py-4">
                    <Badge variant={quoteStatusVariant(quote.status)}>
                      {quote.status}
                    </Badge>
                  </td>
                  <td className="px-6 py-4 text-sm font-medium text-gray-900">
                    R {quote.total.toLocaleString('en-ZA', { minimumFractionDigits: 2 })}
                  </td>
                  <td className="px-6 py-4 text-sm text-gray-500">
                    {quote.expiresAt
                      ? new Date(quote.expiresAt).toLocaleDateString('en-ZA')
                      : '—'}
                  </td>
                  <td className="px-6 py-4">
                    <Link to={`/quotes/${quote.id}`}>
                      <Button variant="ghost" size="sm">
                        <Eye className="w-4 h-4" />
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
              {data?.content.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-6 py-8 text-center text-gray-400 text-sm">
                    No quotes yet. Create your first quote.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        )}
      </Card>
    </div>
  )
}