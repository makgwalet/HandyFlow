-- Agriculture chunk 6: foreign keys and CHECK constraints the first migrations left out.
-- Every constraint is NOT VALID: it is enforced on new and changed rows from now on, but old rows are not checked, so a
-- tenant with a stray legacy row can still migrate. Run VALIDATE CONSTRAINT later, once you have looked for bad rows.

-- ── references inside the module ─────────────────────────────────────────
ALTER TABLE ag_production_areas ADD CONSTRAINT fk_ag_production_areas_farm FOREIGN KEY (farm_id) REFERENCES ag_farms (id) NOT VALID;
ALTER TABLE ag_enterprises      ADD CONSTRAINT fk_ag_enterprises_farm      FOREIGN KEY (farm_id) REFERENCES ag_farms (id) NOT VALID;
ALTER TABLE ag_inventory_items  ADD CONSTRAINT fk_ag_inventory_items_farm  FOREIGN KEY (farm_id) REFERENCES ag_farms (id) NOT VALID;
ALTER TABLE ag_stock_movements  ADD CONSTRAINT fk_ag_stock_movements_item  FOREIGN KEY (inventory_item_id) REFERENCES ag_inventory_items (id) NOT VALID;

ALTER TABLE ag_animals ADD CONSTRAINT fk_ag_animals_farm       FOREIGN KEY (farm_id)            REFERENCES ag_farms (id) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT fk_ag_animals_area       FOREIGN KEY (production_area_id) REFERENCES ag_production_areas (id) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT fk_ag_animals_enterprise FOREIGN KEY (enterprise_id)      REFERENCES ag_enterprises (id) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT fk_ag_animals_species    FOREIGN KEY (species_id)         REFERENCES ag_species (id) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT fk_ag_animals_sire       FOREIGN KEY (sire_id)            REFERENCES ag_animals (id) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT fk_ag_animals_dam        FOREIGN KEY (dam_id)             REFERENCES ag_animals (id) NOT VALID;

ALTER TABLE ag_groups ADD CONSTRAINT fk_ag_groups_farm       FOREIGN KEY (farm_id)            REFERENCES ag_farms (id) NOT VALID;
ALTER TABLE ag_groups ADD CONSTRAINT fk_ag_groups_area       FOREIGN KEY (production_area_id) REFERENCES ag_production_areas (id) NOT VALID;
ALTER TABLE ag_groups ADD CONSTRAINT fk_ag_groups_enterprise FOREIGN KEY (enterprise_id)      REFERENCES ag_enterprises (id) NOT VALID;
ALTER TABLE ag_groups ADD CONSTRAINT fk_ag_groups_species    FOREIGN KEY (species_id)         REFERENCES ag_species (id) NOT VALID;

-- ── allowed values and sizes ─────────────────────────────────────────────
ALTER TABLE ag_animals ADD CONSTRAINT chk_ag_animals_sex         CHECK (sex IN ('MALE', 'FEMALE', 'CASTRATED')) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT chk_ag_animals_status      CHECK (status IN ('ACTIVE', 'SOLD', 'DECEASED', 'CULLED', 'TRANSFERRED_OUT')) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT chk_ag_animals_acquisition CHECK (acquisition_type IN ('BORN_ON_FARM', 'PURCHASED', 'TRANSFERRED_IN')) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT chk_ag_animals_amounts     CHECK ((acquisition_cost IS NULL OR acquisition_cost >= 0) AND (current_weight_kg IS NULL OR current_weight_kg >= 0)) NOT VALID;
ALTER TABLE ag_animals ADD CONSTRAINT chk_ag_animals_parents     CHECK ((sire_id IS NULL OR sire_id <> id) AND (dam_id IS NULL OR dam_id <> id) AND (sire_id IS NULL OR dam_id IS NULL OR sire_id <> dam_id)) NOT VALID;

ALTER TABLE ag_groups ADD CONSTRAINT chk_ag_groups_status CHECK (status IN ('ACTIVE', 'CLOSED')) NOT VALID;
ALTER TABLE ag_groups ADD CONSTRAINT chk_ag_groups_counts CHECK (initial_count > 0 AND current_count >= 0) NOT VALID;

ALTER TABLE ag_inventory_items ADD CONSTRAINT chk_ag_inventory_items_status  CHECK (status IN ('ACTIVE', 'INACTIVE')) NOT VALID;
ALTER TABLE ag_inventory_items ADD CONSTRAINT chk_ag_inventory_items_amounts CHECK (current_quantity >= 0 AND (unit_cost IS NULL OR unit_cost >= 0)) NOT VALID;

-- ADJUSTMENT is the old, direction-less type; new corrections are written as ADJUSTMENT_UP or ADJUSTMENT_DOWN.
ALTER TABLE ag_stock_movements ADD CONSTRAINT chk_ag_stock_movements_type     CHECK (movement_type IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT', 'ADJUSTMENT_UP', 'ADJUSTMENT_DOWN', 'WASTAGE')) NOT VALID;
ALTER TABLE ag_stock_movements ADD CONSTRAINT chk_ag_stock_movements_quantity CHECK (quantity > 0) NOT VALID;

ALTER TABLE ag_movement_records ADD CONSTRAINT chk_ag_movement_records_type CHECK (movement_type IN ('AREA_TRANSFER', 'FARM_TRANSFER', 'SALE', 'TRANSFER_OUT')) NOT VALID;

ALTER TABLE ag_farms            ADD CONSTRAINT chk_ag_farms_status            CHECK (status IN ('ACTIVE', 'INACTIVE')) NOT VALID;
ALTER TABLE ag_production_areas ADD CONSTRAINT chk_ag_production_areas_status CHECK (status IN ('ACTIVE', 'RESTING', 'QUARANTINE', 'INACTIVE')) NOT VALID;
