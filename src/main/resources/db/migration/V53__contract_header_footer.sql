-- Blank zones preserve historical agreements and their original signatures and hashes.
ALTER TABLE profilecontract.template ADD COLUMN header_html TEXT NOT NULL DEFAULT '';
ALTER TABLE profilecontract.template ADD COLUMN footer_html TEXT NOT NULL DEFAULT '';
ALTER TABLE profilecontract.agreement ADD COLUMN header_snapshot TEXT NOT NULL DEFAULT '';
ALTER TABLE profilecontract.agreement ADD COLUMN footer_snapshot TEXT NOT NULL DEFAULT '';
-- Published template and signed snapshot triggers already protect every new column.
