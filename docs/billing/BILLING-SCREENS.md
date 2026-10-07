# Billing screens: quotes, invoices, recurring billing, retainers, credit notes

Patch 0054. The single Invoicing page (three tabs in one 1,200-line file) is now separate routed screens that share
rules, components and dialogs. Nothing about the data or the endpoints changed, except one addition: `POST /api/v1/invoicing/quotes/{id}/reject`
(the quote screen already had a Reject button, and the service method existed, but the route did not, so the button failed).

## Routes

| Route | Screen | Notes |
|---|---|---|
| `/quotes` | Quotes list | Tiles, search, status pills, sortable columns, paging |
| `/quotes/:id` | Quote detail | Timeline including client views; send, accept, reject, convert |
| `/invoices` | Invoices list | Outstanding, overdue, collected, drafts; status and type filters |
| `/invoices/:id` | Invoice detail (new) | Payment progress, retainer hours, credit notes, timeline, linked quote or schedule |
| `/recurring` | Recurring list | Monthly recurring revenue, runs in 7 days, variable-hours contracts |
| `/recurring/:id` | Schedule detail (new) | Template lines or contract terms, invoices it created |
| `/retainers` | Retainers list (new) | Hours used against the commitment, running low, overage |
| `/credit-notes` | Credit notes list (new) | All credit notes, PDF, link to the invoice |

The create forms (`/quotes/new`, `/invoices/retainer/new`, `/recurring/new`, `/recurring/variable-hours/new`) are unchanged.
Creating a retainer now returns to `/retainers`.

## Rules worth knowing

- **Outstanding is what is still owing** (total less paid), not the sum of invoice totals. A part-paid invoice counts only its balance.
- **Recurring revenue** counts active schedules only, converted to a monthly figure: weekly x 52/12, daily x 365/12, custom by its interval. Variable-hours schedules have no fixed amount, so they are counted separately.
- **Retainer usage**: "running low" is 80% of committed hours or more; "overage" is past the commitment.
- **Payments** cannot exceed the balance; **credit notes** need a reason and an amount; **cycle hours** bill the contract minimum when fewer hours were worked.
- A sent quote shows "Expires in n days" in its last week and "Lapsed" after.
- **Permissions**: reading needs INVOICE_READ. Sending a quote needs INVOICE_SEND. Creating, accepting, rejecting, converting, issuing, recording payments, logging hours, credit notes, pausing and resuming need INVOICE_CREATE. Cancelling a schedule needs INVOICE_DELETE. Buttons the user cannot use are hidden.
- **Lists load the newest 200** records and filter, search, sort and page in the browser. If there are more, a notice says so. Moving filtering to the server is a follow-up.

## Check it

- [ ] Quotes: the tiles agree with the list; "Sent, not opened" counts sent quotes with no client view; clicking a tile filters the list.
- [ ] Invoices: Outstanding equals the sum of balances of Issued, Part paid and Overdue invoices; an overdue row says "n days overdue".
- [ ] Record a payment larger than the balance: it is refused with the balance shown. A valid one updates the row and the detail page.
- [ ] Open a retainer invoice at 80%+ usage: the warning appears on the detail page and on `/retainers`.
- [ ] Open a schedule: invoices it created are listed and link to their invoice pages.
- [ ] Open a sent quote and choose Mark rejected: the status changes to Rejected (this failed before).
- [ ] Convert an accepted quote: you land on the new invoice's page.
- [ ] Narrow the window to phone width: tiles and panels wrap, tables scroll sideways inside their panel, nothing makes the page scroll sideways.

## Not done

- Server-side filtering and paging for very large tenants (the 200-record cap above).
- A payments history on the invoice page (the server returns only the amount paid, not individual payments).
- Reworking the four create forms.
