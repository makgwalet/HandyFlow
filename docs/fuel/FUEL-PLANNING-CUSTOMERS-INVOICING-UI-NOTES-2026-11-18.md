# Fuel UI: Planning, Customers and Invoicing on the new panels — notes, 2026-11-18

Web only, no backend. Fuel and navigation tests (94 files, 686 tests) and `tsc` pass.

## What changed
- **Invoicing:** KPI strip (waiting to invoice with loads and litres, customers to bill, failed attempts); each customer's loads sit in a navy-header panel with the "Create draft invoice" button in the header; a proper empty state.
- **Planning:** KPI strip (loads planned and litres, needing allocation, on the road, tankers in use); the day's loads sit in a "Loads for the day" panel; empty state.
- **Customers:** KPI strip (accounts, accounts on hold, delivery sites, credit extended); empty state.
- All existing text, buttons and behaviour are unchanged, so the existing tests pass untouched. Counts come from the data each tab already loads.

## Not changed
The tanker register and route panels in Planning, and the account and site tables in Customers, keep their current tables (the scoped Fuel stylesheet already polishes them).

## Still to look at
A visual check on live data. I have only checked these in tests.
