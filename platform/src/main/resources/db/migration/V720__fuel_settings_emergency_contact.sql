-- N8: the control-room contact a driver rings in an emergency, shown on the mobile app's home screen.
ALTER TABLE fuel_settings ADD COLUMN emergency_contact_name VARCHAR(100);
ALTER TABLE fuel_settings ADD COLUMN emergency_contact_phone VARCHAR(30);
