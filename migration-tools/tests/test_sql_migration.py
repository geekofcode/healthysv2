"""Optional SQL integration on an isolated, migrated PostgreSQL test database only."""
import importlib.util
import os
import unittest
from pathlib import Path
from uuid import uuid4

spec=importlib.util.spec_from_file_location('migration_sql',Path(__file__).parents[1]/'healthys_migrate.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

@unittest.skipUnless(os.environ.get('TEST_MIGRATION_DATABASE_URL'),'isolated PostgreSQL test URL not configured')
class SqlMigrationTest(unittest.TestCase):
    def setUp(self):
        import psycopg
        self.db=psycopg.connect(os.environ['TEST_MIGRATION_DATABASE_URL'])
        self.db.execute("SET lock_timeout='5s'")
        self.db.execute("SET statement_timeout='20s'")
        self.ids={name:uuid4() for name in ('doctor_person','patient_person','patient','org','doctor','appointment','consultation','encounter','invoice','document')}
        i=self.ids
        for key in ('doctor_person','patient_person'):
            self.db.execute("INSERT INTO identity.person(id,person_number,first_name,last_name) VALUES(%s,%s,'Synthetic','Test')",(i[key],str(i[key])))
        self.db.execute("INSERT INTO patient.patient(id,person_id,patient_number) VALUES(%s,%s,%s)",(i['patient'],i['patient_person'],str(i['patient'])))
        self.db.execute("INSERT INTO organization.organization(id,organization_number,name) VALUES(%s,%s,'Synthetic clinic')",(i['org'],str(i['org'])))
        self.db.execute("INSERT INTO professional.professional(id,person_id,professional_number,professional_type) VALUES(%s,%s,%s,'DOCTOR')",(i['doctor'],i['doctor_person'],str(i['doctor'])))
        self.db.execute("INSERT INTO appointment.appointment(id,appointment_number,patient_id,organization_id,professional_id,type,scheduled_start,scheduled_end,status,created_at) VALUES(%s,%s,%s,%s,%s,'CONSULTATION','2025-01-03T10:00Z','2025-01-03T10:30Z','COMPLETED','2025-01-01T10:00Z')",(i['appointment'],str(i['appointment']),i['patient'],i['org'],i['doctor']))
        self.db.execute("INSERT INTO registration.encounter(id,encounter_number,patient_id,organization_id,appointment_id,type) VALUES(%s,%s,%s,%s,%s,'CONSULTATION')",(i['encounter'],str(i['encounter']),i['patient'],i['org'],i['appointment']))
        self.db.execute("INSERT INTO consultation.consultation(id,consultation_number,patient_id,organization_id,professional_id,appointment_id,encounter_id,type,status,reason,started_at,completed_at,created_at) VALUES(%s,%s,%s,%s,%s,%s,%s,'GENERAL','COMPLETED','Synthetic reason','2025-01-03T10:00Z','2025-01-03T10:30Z','2025-01-01T10:00Z')",(i['consultation'],str(i['consultation']),i['patient'],i['org'],i['doctor'],i['appointment'],i['encounter']))
        self.db.execute("INSERT INTO audit.audit_log(module,entity_type,entity_id,action,actor_person_id) VALUES('CONSULTATION','Consultation',%s,'COMPLETE',%s)",(i['consultation'],i['doctor_person']))
        self.db.execute("INSERT INTO consultation.consultation_note(consultation_id,note_type,content,author_id) VALUES(%s,'EXAM','Synthetic private note',%s)",(i['consultation'],i['doctor']))
        self.db.execute("INSERT INTO billing.invoice(id,invoice_number,patient_id,organization_id,encounter_id,currency,subtotal,total_amount,amount_paid,status) VALUES(%s,%s,%s,%s,%s,'CAD',100,100,100,'PAID')",(i['invoice'],str(i['invoice']),i['patient'],i['org'],i['encounter']))
        self.db.execute("INSERT INTO billing.payment(payment_number,invoice_id,amount,currency,payment_method,status) VALUES(%s,%s,100,'CAD','CARD','COMPLETED')",(str(uuid4()),i['invoice']))
        self.db.execute("INSERT INTO document.document(id,document_number,owner_patient_id,file_name,storage_key,storage_provider,uploaded_by) VALUES(%s,%s,%s,'synthetic.pdf',%s,'MINIO',%s)",(i['document'],str(i['document']),i['patient'],str(i['document']),i['doctor_person']))
        self.db.execute("INSERT INTO document.document_link(document_id,resource_type,resource_id) VALUES(%s,'CONSULTATION',%s)",(i['document'],i['consultation']))
    def tearDown(self):
        self.db.rollback();self.db.close()
    def plan(self):
        snapshot=m.read_snapshot(self.db);plan=m.build_plan(snapshot)
        self.assertEqual(plan['report']['anomalies'],[])
        return plan
    def test_import_preserves_finances_privacy_document_sources_and_replays_then_rolls_back(self):
        plan=self.plan();before=self.db.execute('SELECT count(*) FROM care.care_episode').fetchone()[0]
        self.assertEqual(self.db.execute('SELECT count(*) FROM care.care_episode').fetchone()[0],before)
        run=m.apply(self.db,plan,plan['source_fingerprint'],True)
        self.assertEqual(m.fingerprint(m.read_snapshot(self.db)),plan['source_fingerprint'])
        self.assertEqual(m.apply(self.db,plan,plan['source_fingerprint'],True),run)
        self.assertEqual(self.db.execute('SELECT count(*) FROM billing.cash_operation').fetchone()[0],0)
        self.assertFalse(self.db.execute('SELECT patient_visible FROM care.visit_report_revision WHERE validated_by=%s',(self.ids['doctor_person'],)).fetchone()[0])
        self.assertEqual(self.db.execute('SELECT count(*) FROM document.document_link WHERE document_id=%s',(self.ids['document'],)).fetchone()[0],2)
        m.validate_targets(self.db,run)
        m.rollback(self.db,run,True,'synthetic-operator')
        self.assertEqual(self.db.execute('SELECT count(*) FROM care.care_episode').fetchone()[0],before)
        self.assertEqual(self.db.execute('SELECT payment_method FROM billing.payment WHERE invoice_id=%s',(self.ids['invoice'],)).fetchone()[0],'CARD')
        self.assertEqual(self.db.execute('SELECT count(*) FROM document.document_link WHERE document_id=%s',(self.ids['document'],)).fetchone()[0],1)
    def test_new_target_clinical_data_prevents_rollback_without_partial_deletion(self):
        plan=self.plan();run=m.apply(self.db,plan,plan['source_fingerprint'],True)
        visit=self.db.execute("SELECT target_id FROM care.migration_mapping WHERE run_id=%s AND target_table='care.care_visit'",(run,)).fetchone()[0]
        self.db.execute('UPDATE care.care_visit SET reason=%s WHERE id=%s',('New manual information',visit))
        with self.assertRaises(ValueError):m.rollback(self.db,run,True,'synthetic-operator')
        self.assertEqual(self.db.execute('SELECT reason FROM care.care_visit WHERE id=%s',(visit,)).fetchone()[0],'New manual information')

if __name__=='__main__':unittest.main()
