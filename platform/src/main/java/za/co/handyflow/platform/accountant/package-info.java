/**
 * Accountant practice management module.
 *
 * Architecture layers:
 *   L1 — Practice shell:    AccountantProfile, FirmSettings
 *   L2 — Client portfolio:  AccClient, ClientNotes, ClientContacts, OnboardingItems
 *   L3 — Accounting core:   COA, Journals, Periods, BankRecon, FixedAssets
 *   L4 — SARS compliance:   TaxDeadlines, PublicHolidays, TCS records
 *   L5 — Workpapers:        WorkpaperFolders/Files, EngagementLetters, FICA, DocumentRequests
 *   L6 — Billing:           TimeEntries, FeeNotes, PaymentsReceived, BillingRates
 *   L7 — Portfolio ops:     Cross-client views via service aggregation, no separate tables
 *
 * Package: za.co.handyflow.platform.accountant
 */
@NonNullApi
package za.co.handyflow.platform.accountant;

import org.springframework.lang.NonNullApi;
