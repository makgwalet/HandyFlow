# Fuel access limits, round 2: tank-linked lists and reports

Status: Java written, not compiled or run. No migration, no web change.

## Now filtered for a depot-limited user
| View | Rule |
|---|---|
| Receipts, dispatches, deliveries (list, with or without a status) | Only those on tanks in their depots. Paging is correct because the filter is in the query. |
| Tank utilisation forecast | Only their tanks. |
| Margin report, usage report (PDF) | Only deliveries and dispatches from their tanks, and totals for those. |
| Group report | Only their tanks in every figure (deliveries, stock, forecourt sales, open variances), per legal entity. The group total is the total of what they may see. |
| Forecourt shifts list | A full page of their own shifts: reads further back (up to 20 pages) until there are enough. |
Users with no depot limit, impersonation sessions and calls with no identified user are unchanged.

## Not filtered, and why
- **Orders, enquiries, customers, pricing, routes, purchase orders, supplier invoices:** they are not tied to a tank or depot in the data, so there is nothing to filter by. A deliveries list is filtered; the order behind a delivery is not.
- **Compliance items:** some are linked to a depot, some are company-wide. Filtering only the depot-linked ones would make the coverage figures disagree with the list, so I left them. Say if you want depot-linked items hidden.
- **Landed cost report, route cost reports, variances list, supplier statements:** not changed in this round.
- **Customer portal:** outside this interceptor (already tracked).

## Behaviour to know
- A delivery or dispatch with no tank is hidden from depot-limited users.
- A tank removed since a delivery was made is not in a limited user's set, so that history is hidden for them.
- The shifts list can read up to 20 pages back to fill a page; for a user whose depots have few shifts it returns what exists.

## Tests
`FuelServiceStage0Test` (+2), `FuelGroupReportServiceTest` (+1), `FuelDepotLimitFilteringTest` (+1).
