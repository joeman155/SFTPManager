-- Run this in pgAdmin or psql to make a user an admin (role = 10)
-- Replace the email with your Google account email

UPDATE users SET role = 10 WHERE email = 'your-google-email@gmail.com';

-- Verify
SELECT id, first_name, surname, email, role FROM users WHERE email = 'your-google-email@gmail.com';

-- Add email auth columns (run if upgrading from earlier version)
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_type VARCHAR(20) DEFAULT 'GOOGLE';
ALTER TABLE users ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- New columns for this release
ALTER TABLE users ADD COLUMN IF NOT EXISTS services_deactivated BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER DEFAULT 0;
ALTER TABLE sftp_service_account ADD COLUMN IF NOT EXISTS email VARCHAR(255);
ALTER TABLE sftp_service_account ADD COLUMN IF NOT EXISTS permissions VARCHAR(100);
