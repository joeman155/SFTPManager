-- Run this in pgAdmin or psql to make a user an admin (role = 10)
-- Replace the email with your Google account email

UPDATE users SET role = 10 WHERE email = 'your-google-email@gmail.com';

-- Verify
SELECT id, first_name, surname, email, role FROM users WHERE email = 'your-google-email@gmail.com';
