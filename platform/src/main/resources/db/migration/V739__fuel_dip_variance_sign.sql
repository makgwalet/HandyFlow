-- Dip variance was stored as book - actual (calculated_litres - actual_litres), the wrong way round: a tank short of its book level read as a
-- surplus, and a surplus read as a loss. The dashboard ("below -5 L"), the shortfall alert and the app all take negative as the loss, which is
-- actual - calculated. This recomputes every stored variance that way. It recalculates from the two stored litres, so running it twice changes nothing.
UPDATE fuel_dip_readings
   SET variance_litres = actual_litres - calculated_litres
 WHERE calculated_litres IS NOT NULL;
