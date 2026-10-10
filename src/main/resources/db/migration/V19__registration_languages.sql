INSERT INTO shared.language (code, label) VALUES ('fr', 'Français'), ('en', 'English')
ON CONFLICT (code) DO NOTHING;
