-- Public holidays 2027 and 2028 (Public Holidays Act 36 of 1994: a holiday on a Sunday moves to the Monday; none for Saturdays).
-- Before this, only 2026 was seeded, so EMP201/EMP501 due dates in 2027 did not roll back for holidays.
-- 2027 matches the gov.za list; 2028 is computed from the Act and Easter (16 April 2028) and should be checked against
-- the official list when it is published. 4 November 2026 is the once-off local government election holiday.
INSERT INTO acc_public_holidays (holiday_date, name, year) VALUES
    ('2026-11-04', 'Local government election day', 2026),
    ('2027-01-01', 'New Year''s Day', 2027),
    ('2027-03-21', 'Human Rights Day', 2027),
    ('2027-03-22', 'Human Rights Day observed', 2027),
    ('2027-03-26', 'Good Friday', 2027),
    ('2027-03-29', 'Family Day', 2027),
    ('2027-04-27', 'Freedom Day', 2027),
    ('2027-05-01', 'Workers'' Day', 2027),
    ('2027-06-16', 'Youth Day', 2027),
    ('2027-08-09', 'National Women''s Day', 2027),
    ('2027-09-24', 'Heritage Day', 2027),
    ('2027-12-16', 'Day of Reconciliation', 2027),
    ('2027-12-25', 'Christmas Day', 2027),
    ('2027-12-26', 'Day of Goodwill', 2027),
    ('2027-12-27', 'Day of Goodwill observed', 2027),
    ('2028-01-01', 'New Year''s Day', 2028),
    ('2028-03-21', 'Human Rights Day', 2028),
    ('2028-04-14', 'Good Friday', 2028),
    ('2028-04-17', 'Family Day', 2028),
    ('2028-04-27', 'Freedom Day', 2028),
    ('2028-05-01', 'Workers'' Day', 2028),
    ('2028-06-16', 'Youth Day', 2028),
    ('2028-08-09', 'National Women''s Day', 2028),
    ('2028-09-24', 'Heritage Day', 2028),
    ('2028-09-25', 'Heritage Day observed', 2028),
    ('2028-12-16', 'Day of Reconciliation', 2028),
    ('2028-12-25', 'Christmas Day', 2028),
    ('2028-12-26', 'Day of Goodwill', 2028)
ON CONFLICT (holiday_date) DO NOTHING;
