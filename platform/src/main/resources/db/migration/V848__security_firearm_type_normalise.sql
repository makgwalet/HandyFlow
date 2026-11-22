-- Firearm type becomes a controlled list (Handgun / Shotgun / Rifle). Tidy the values that are unambiguous;
-- anything else is left exactly as it was so nobody's register is rewritten by guesswork.
UPDATE security_armoury SET firearm_type = 'Handgun'
 WHERE lower(trim(firearm_type)) IN ('handgun', 'pistol', 'revolver') AND firearm_type <> 'Handgun';
UPDATE security_armoury SET firearm_type = 'Shotgun'
 WHERE lower(trim(firearm_type)) = 'shotgun' AND firearm_type <> 'Shotgun';
UPDATE security_armoury SET firearm_type = 'Rifle'
 WHERE lower(trim(firearm_type)) = 'rifle' AND firearm_type <> 'Rifle';
