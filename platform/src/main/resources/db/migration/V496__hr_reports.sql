-- HR Slice 11: reports.
--
-- Four read-only reports built from the records the other HR modules already keep: workforce (headcount, turnover, demographics),
-- time and leave (leave, attendance, overtime), payroll cost and statutory totals, and employee relations and compliance.
-- No tables. One permission, given to roles that already hold HR_MANAGE:
--   HR_REPORT_READ  open the reports
-- Holding it is not enough on its own to see everything: gender, race and disability counts need HR_SENSITIVE_READ, payroll cost needs a
-- compensation right, and the employee relations figures need an employee relations right.

INSERT INTO permissions (id, name, description) VALUES
    (gen_random_uuid(), 'HR_REPORT_READ', 'Open the HR reports (workforce, time and leave, payroll cost, employee relations and compliance)')
ON CONFLICT (name) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT rp.role_id, p.id
FROM role_permissions rp
JOIN permissions held ON held.id = rp.permission_id AND held.name = 'HR_MANAGE'
CROSS JOIN permissions p
WHERE p.name = 'HR_REPORT_READ'
ON CONFLICT DO NOTHING;
