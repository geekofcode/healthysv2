-- The historical tables remain readable, but become immutable to business commands after cutover.
-- DELETE stays available to the controlled, dependency-aware retention workflow.
CREATE FUNCTION care.reject_retired_legacy_write() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF EXISTS(SELECT 1 FROM care.cutover_state WHERE singleton AND mode='CARE_ONLY') THEN
  RAISE EXCEPTION 'Historical care pathway is read-only after verified cutover' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END;
$$;
DO $$ DECLARE item record; BEGIN
 FOR item IN SELECT schemaname,tablename FROM pg_tables WHERE schemaname IN ('appointment','consultation') LOOP
  EXECUTE format('CREATE TRIGGER retired_business_writes BEFORE INSERT OR UPDATE ON %I.%I FOR EACH ROW EXECUTE FUNCTION care.reject_retired_legacy_write()',item.schemaname,item.tablename);
 END LOOP;
END $$;
