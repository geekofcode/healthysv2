-- Existing accepted agreements remain byte-for-byte TEXT snapshots with their original hash.
ALTER TABLE profilecontract.template ADD COLUMN body_format VARCHAR(4) NOT NULL DEFAULT 'TEXT' CHECK(body_format IN ('TEXT','HTML'));
ALTER TABLE profilecontract.agreement ADD COLUMN body_format VARCHAR(4) NOT NULL DEFAULT 'TEXT' CHECK(body_format IN ('TEXT','HTML'));
-- The agreement immutability trigger already compares every column except delivery metadata.
-- Published templates are equally immutable, including this new format discriminator.
