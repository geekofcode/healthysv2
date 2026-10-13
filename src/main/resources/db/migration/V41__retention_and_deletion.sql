CREATE SCHEMA lifecycle;
CREATE TABLE lifecycle.retention_policy (id boolean PRIMARY KEY DEFAULT true CHECK(id), years integer NOT NULL CHECK(years BETWEEN 1 AND 200));
INSERT INTO lifecycle.retention_policy VALUES(true,30);
CREATE FUNCTION lifecycle.retention_deadline() RETURNS timestamptz LANGUAGE sql STABLE AS $$ SELECT now()+make_interval(years=>years) FROM lifecycle.retention_policy WHERE id $$;
-- Every non-reference domain row receives a deadline. Historical rows are backfilled
-- from their original creation timestamp where available, never from deployment time.
DO $$ DECLARE t record; source_column text; BEGIN
 FOR t IN SELECT table_schema,table_name FROM information_schema.tables WHERE table_type='BASE TABLE'
 AND table_schema IN ('identity','organization','professional','patient','clinical','registration','appointment','consultation','laboratory','prescription','pharmacy','maternal_child','document','communication','teleconsultation','notification','billing','audit','care','licensing')
 AND table_name NOT IN ('document_category','speciality','medication','diagnosis_catalog') LOOP
 IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=t.table_schema AND table_name=t.table_name AND column_name='retention_expires_at') THEN
 SELECT column_name INTO source_column FROM information_schema.columns WHERE table_schema=t.table_schema AND table_name=t.table_name
 AND column_name IN ('created_at','uploaded_at','recorded_at','occurred_at','issued_at','validated_at','changed_at','paid_at','granted_at','entered_at','updated_at')
 ORDER BY CASE column_name WHEN 'created_at' THEN 0 WHEN 'uploaded_at' THEN 1 WHEN 'recorded_at' THEN 2 ELSE 3 END LIMIT 1;
 EXECUTE format('ALTER TABLE %I.%I ADD COLUMN retention_expires_at timestamptz',t.table_schema,t.table_name);
 EXECUTE format('ALTER TABLE %I.%I DISABLE TRIGGER USER',t.table_schema,t.table_name);
 EXECUTE format('UPDATE %I.%I SET retention_expires_at=COALESCE(%s,now())+interval ''30 years''',t.table_schema,t.table_name,CASE WHEN source_column IS NULL THEN 'NULL::timestamptz' ELSE quote_ident(source_column) END);
 EXECUTE format('ALTER TABLE %I.%I ENABLE TRIGGER USER',t.table_schema,t.table_name);
 EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN retention_expires_at SET NOT NULL',t.table_schema,t.table_name);
 END IF;
 EXECUTE format('ALTER TABLE %I.%I ALTER COLUMN retention_expires_at SET DEFAULT lifecycle.retention_deadline()',t.table_schema,t.table_name);
 END LOOP;
END $$;
CREATE TABLE lifecycle.deletion_request (
 id uuid PRIMARY KEY, person_id uuid NOT NULL REFERENCES identity.person(id), idempotency_key varchar(128) NOT NULL,
 status varchar(20) NOT NULL DEFAULT 'QUEUED' CHECK(status IN ('QUEUED','RUNNING','COMPLETED','FAILED')),
 created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz, next_attempt_at timestamptz NOT NULL DEFAULT now(),
 lease_token uuid, lease_until timestamptz, attempts integer NOT NULL DEFAULT 0, failures integer NOT NULL DEFAULT 0,
 removed_count integer NOT NULL DEFAULT 0, anonymized_count integer NOT NULL DEFAULT 0, retained_count integer NOT NULL DEFAULT 0,
 notes text NOT NULL DEFAULT '', error_code varchar(100), retention_expires_at timestamptz NOT NULL DEFAULT lifecycle.retention_deadline(),
 UNIQUE(person_id,idempotency_key)
);
CREATE UNIQUE INDEX one_pending_deletion_per_person ON lifecycle.deletion_request(person_id) WHERE status IN ('QUEUED','RUNNING');
CREATE TABLE lifecycle.purge_event (
 id uuid PRIMARY KEY DEFAULT gen_random_uuid(), request_id uuid REFERENCES lifecycle.deletion_request(id),
 resource_type varchar(100) NOT NULL, resource_id uuid NOT NULL, outcome varchar(30) NOT NULL,
 reason varchar(200) NOT NULL, occurred_at timestamptz NOT NULL DEFAULT now(),
 retention_expires_at timestamptz NOT NULL DEFAULT lifecycle.retention_deadline()
);
CREATE INDEX deletion_request_work ON lifecycle.deletion_request(next_attempt_at) WHERE status IN ('QUEUED','RUNNING');
CREATE TABLE lifecycle.object_purge (
 document_id uuid PRIMARY KEY REFERENCES document.document(id), storage_key text NOT NULL,
 request_id uuid REFERENCES lifecycle.deletion_request(id), status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','DONE')),
 created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
 retention_expires_at timestamptz NOT NULL DEFAULT lifecycle.retention_deadline()
);
-- Once a private blob is reserved for deletion it cannot acquire a new live reference.
CREATE FUNCTION lifecycle.prevent_purging_document_reference() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE document_uuid uuid; BEGIN
 document_uuid := (to_jsonb(NEW)->>TG_ARGV[0])::uuid;
 PERFORM 1 FROM document.document WHERE id=document_uuid FOR KEY SHARE;
 IF EXISTS(SELECT 1 FROM lifecycle.object_purge WHERE document_id=document_uuid) THEN
 RAISE EXCEPTION 'Document is reserved for deletion'; END IF;
 RETURN NEW;
END $$;
DO $$ DECLARE r record; BEGIN
 FOR r IN SELECT ns.nspname,t.relname,a.attname FROM pg_constraint c JOIN pg_class t ON t.oid=c.conrelid
 JOIN pg_namespace ns ON ns.oid=t.relnamespace JOIN pg_attribute a ON a.attrelid=t.oid AND a.attnum=ANY(c.conkey)
 WHERE c.contype='f' AND c.confrelid='document.document'::regclass AND ns.nspname<>'lifecycle' LOOP
 EXECUTE format('CREATE TRIGGER lifecycle_document_guard BEFORE INSERT OR UPDATE ON %I.%I FOR EACH ROW EXECUTE FUNCTION lifecycle.prevent_purging_document_reference(%L)',r.nspname,r.relname,r.attname);
 END LOOP;
END $$;
CREATE TABLE lifecycle.retention_job (
 episode_id uuid PRIMARY KEY, status varchar(20) NOT NULL CHECK(status IN ('PENDING','BLOCKED','COMPLETED','FAILED')),
 attempts integer NOT NULL DEFAULT 0, next_attempt_at timestamptz NOT NULL DEFAULT now(),
 reason varchar(200), removed_count integer NOT NULL DEFAULT 0, created_at timestamptz NOT NULL DEFAULT now(), completed_at timestamptz,
 retention_expires_at timestamptz NOT NULL DEFAULT lifecycle.retention_deadline()
);
ALTER TABLE lifecycle.object_purge ADD COLUMN episode_id uuid;
-- Append-only cash/audit cannot be edited. A guarded retention transaction may only
-- delete a row whose own deadline has elapsed; normal requests never set this flag.
CREATE OR REPLACE FUNCTION audit.reject_log_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN
 IF TG_OP='DELETE' AND current_setting('healthys.retention_purge',true)='on'
 AND OLD.retention_expires_at<=clock_timestamp() THEN RETURN OLD; END IF;
 RAISE EXCEPTION 'Audit and cash records are append-only';
END $$;
CREATE INDEX document_retention_due ON document.document(retention_expires_at,id);
CREATE INDEX object_purge_pending ON lifecycle.object_purge(episode_id,request_id) WHERE status='PENDING';
CREATE INDEX retention_job_due ON lifecycle.retention_job(next_attempt_at) WHERE status IN ('PENDING','BLOCKED','FAILED');
-- Freeze an expired validated component across the MinIO/SQL transaction boundary.
CREATE TABLE lifecycle.retention_table (table_name text PRIMARY KEY,key_column text NOT NULL);
INSERT INTO lifecycle.retention_table VALUES
 ('care.care_episode','id'),('care.care_visit','id'),('care.reception_intake','episode_id'),('care.visit_intake','visit_id'),
 ('care.visit_report','visit_id'),('care.visit_report_revision','id'),('care.visit_prescription','visit_id'),('care.visit_prescription_revision','id'),
 ('care.visit_schedule_history','id'),('care.queue_entry','visit_id'),('care.follow_up_request','visit_id'),('care.ai_job','id'),
 ('care.visit_document','document_id'),('document.document','id'),('document.document_access','id'),('document.document_link','id'),
 ('billing.care_invoice','episode_id'),('billing.invoice','id'),('billing.invoice_item','id'),('billing.payment','id'),
 ('billing.payment_transaction','id'),('billing.cash_operation','id'),('billing.cash_closure_receipt','cash_operation_id'),('billing.cash_closure','id');
CREATE TABLE lifecycle.retention_reservation (
 resource_table text NOT NULL REFERENCES lifecycle.retention_table(table_name), resource_id uuid NOT NULL,
 episode_id uuid NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 PRIMARY KEY(resource_table,resource_id)
);
CREATE INDEX retention_reservation_episode ON lifecycle.retention_reservation(episode_id);
CREATE FUNCTION lifecycle.freeze_retention_component() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE own_key text; own_uuid uuid; r record; referenced_uuid uuid; row_data jsonb; BEGIN
 IF TG_OP='DELETE' AND current_setting('healthys.retention_purge',true)='on' THEN RETURN OLD; END IF;
 row_data:=CASE WHEN TG_OP='DELETE' THEN to_jsonb(OLD) ELSE to_jsonb(NEW) END;
 SELECT key_column INTO own_key FROM lifecycle.retention_table WHERE table_name=TG_TABLE_SCHEMA||'.'||TG_TABLE_NAME;
 IF own_key IS NOT NULL THEN
 own_uuid:=(row_data->>own_key)::uuid;
 IF EXISTS(SELECT 1 FROM lifecycle.retention_reservation WHERE resource_table=TG_TABLE_SCHEMA||'.'||TG_TABLE_NAME AND resource_id=own_uuid) THEN
 RAISE EXCEPTION 'Expired retention component is frozen'; END IF;
 IF TG_OP='UPDATE' AND EXISTS(SELECT 1 FROM lifecycle.retention_reservation WHERE resource_table=TG_TABLE_SCHEMA||'.'||TG_TABLE_NAME AND resource_id=(to_jsonb(OLD)->>own_key)::uuid) THEN
 RAISE EXCEPTION 'Expired retention component is frozen'; END IF;
 END IF;
 IF TG_OP<>'DELETE' THEN
 FOR r IN SELECT parent_ns.nspname AS parent_schema,parent.relname AS parent_table,child_column.attname AS child_column,parent_column.attname AS parent_column
 FROM pg_constraint c JOIN pg_class parent ON parent.oid=c.confrelid JOIN pg_namespace parent_ns ON parent_ns.oid=parent.relnamespace
 JOIN pg_attribute child_column ON child_column.attrelid=c.conrelid AND child_column.attnum=c.conkey[1]
 JOIN pg_attribute parent_column ON parent_column.attrelid=c.confrelid AND parent_column.attnum=c.confkey[1]
 JOIN lifecycle.retention_table registered ON registered.table_name=parent_ns.nspname||'.'||parent.relname
 WHERE c.contype='f' AND c.conrelid=TG_RELID AND cardinality(c.conkey)=1 LOOP
 referenced_uuid:=(row_data->>r.child_column)::uuid;
 IF referenced_uuid IS NOT NULL THEN
 -- Serialize against graph validation/reservation and refresh visibility after waiting.
 EXECUTE format('SELECT 1 FROM %I.%I WHERE %I=$1 FOR KEY SHARE',r.parent_schema,r.parent_table,r.parent_column) USING referenced_uuid;
 IF EXISTS(SELECT 1 FROM lifecycle.retention_reservation WHERE resource_table=r.parent_schema||'.'||r.parent_table AND resource_id=referenced_uuid) THEN
 RAISE EXCEPTION 'Referenced retention component is frozen'; END IF;
 END IF;
 END LOOP;
 END IF;
 IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
END $$;
DO $$ DECLARE t record; BEGIN
 FOR t IN SELECT ns.nspname,c.relname FROM pg_class c JOIN pg_namespace ns ON ns.oid=c.relnamespace
 WHERE c.relkind='r' AND ns.nspname IN ('care','billing','document','consultation','laboratory','prescription','pharmacy','communication','patient','registration','appointment') LOOP
 EXECUTE format('CREATE TRIGGER lifecycle_retention_freeze BEFORE INSERT OR UPDATE OR DELETE ON %I.%I FOR EACH ROW EXECUTE FUNCTION lifecycle.freeze_retention_component()',t.nspname,t.relname);
 END LOOP;
END $$;
