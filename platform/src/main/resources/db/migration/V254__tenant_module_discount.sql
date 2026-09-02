-- FIX: Discount Engine wiring, Piece A. Nullable — most TenantModule
-- rows never have a discount at all, and every row created before this
-- migration correctly has no discount recorded (they genuinely never
-- had one resolved). discount_source records WHERE the discount came
-- from ("PARTNERSHIP", "VOLUME", "CODE:XYZ") for the visible invoice
-- line-item description Piece B adds ("Partner: XYZ Reseller — 15%
-- off"), not just the raw percentage.
--
-- NUMBERING NOTE: V253 (Gate Access & Registry) was the last confirmed-
-- applied migration this session. This is numbered V254 as the current
-- best estimate — check the real flyway_schema_history state before
-- running, same caution as every migration this session.
ALTER TABLE tenant_modules ADD COLUMN discount_pct NUMERIC(5,2);
ALTER TABLE tenant_modules ADD COLUMN discount_source VARCHAR(50);