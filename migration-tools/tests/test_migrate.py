import copy
import contextlib
import io
from unittest.mock import patch
import importlib.util
import unittest
from pathlib import Path
from uuid import UUID

spec=importlib.util.spec_from_file_location('migration',Path(__file__).parents[1]/'healthys_migrate.py')
m=importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)

def uid(n):
    return str(UUID(int=n))

def fixture():
    patient,org,doctor,person,cid,aid,invoice,encounter=[uid(n) for n in range(1,9)]
    return {
     'patient.patient':[{'id':patient}], 'organization.organization':[{'id':org}],
     'professional.professional':[{'id':doctor,'person_id':person}], 'identity.person':[{'id':person}],
     'consultation.consultation':[{'id':cid,'patient_id':patient,'organization_id':org,'professional_id':doctor,
       'appointment_id':aid,'encounter_id':encounter,'reason':'Douleur','status':'COMPLETED',
       'created_at':'2025-01-01T10:00:00+00:00','started_at':'2025-01-03T10:00:00+00:00',
       'completed_at':'2025-01-03T10:30:00+00:00'}],
     'appointment.appointment':[{'id':aid,'patient_id':patient,'organization_id':org,'professional_id':doctor,
       'created_at':'2025-01-01T10:00:00+00:00','scheduled_start':'2025-01-03T10:00:00+00:00',
       'scheduled_end':'2025-01-03T10:30:00+00:00','reason':'Douleur','status':'COMPLETED'}],
     'registration.encounter':[{'id':encounter,'appointment_id':aid}],
     'billing.invoice':[{'id':invoice,'patient_id':patient,'organization_id':org,'encounter_id':encounter,
       'total_amount':'100.00','amount_paid':'100.00','currency':'XAF','status':'PAID'}],
     'billing.payment':[{'id':uid(9),'invoice_id':invoice,'status':'COMPLETED','amount':'100.00','currency':'XAF','payment_method':'CARD'}],
     'consultation.consultation_note':[{'id':uid(10),'consultation_id':cid,'author_id':doctor,
        'created_at':'2025-01-03T10:10:00+00:00','note_type':'EXAM','content':'Pas de fièvre.'}],
     'consultation.diagnosis':[{'id':uid(11),'consultation_id':cid,'diagnosis_catalog_id':uid(12),
       'status':'ACTIVE','description':None,'diagnosis_type':'PRIMARY'}],
     'catalog.diagnosis_catalog':[{'id':uid(12),'label':'Céphalée'}],
     'consultation.treatment_plan':[{'id':uid(13),'consultation_id':cid,'status':'ACTIVE','description':'Repos'}],
     'consultation.follow_up':[{'id':uid(14),'consultation_id':cid,'recommended_date':'2025-01-08','instructions':'Revoir si persistance'}],
     'document.document':[{'id':uid(15),'owner_patient_id':patient,'uploaded_by':person,'storage_key':'old/private'}],
     'document.document_link':[{'id':uid(16),'document_id':uid(15),'resource_type':'CONSULTATION',
       'resource_id':cid,'created_at':'2025-01-03T10:30:00+00:00'}],
     'audit.audit_log':[{'id':uid(18),'module':'CONSULTATION','entity_type':'Consultation','entity_id':cid,'action':'COMPLETE','actor_person_id':person}],
     'prescription.prescription':[{'id':uid(17),'consultation_id':cid,'patient_id':patient,'prescriber_id':doctor}],
    }

class PlannerTest(unittest.TestCase):
    def test_one_consultation_and_appointment_become_one_visit_and_preserve_all_sources(self):
        s=fixture(); original=copy.deepcopy(s); p=m.build_plan(s)
        self.assertEqual(s,original)
        self.assertEqual(p['report']['anomalies'],[])
        self.assertEqual(p['report']['target_counts']['care.care_visit'],1)
        self.assertEqual(p['report']['cash_operations_created'],0)
        report=next(x['target'] for x in p['rows'] if x['target_table']=='care.visit_report_revision')
        self.assertIn('Pas de fièvre',report['observations']); self.assertIn(uid(3),report['observations'])
        self.assertIn('Céphalée',report['diagnosis']); self.assertIn('Repos',report['treatment'])
        self.assertIn('Revoir',report['follow_up']); self.assertEqual(report['validated_by'],uid(4));self.assertFalse(report['patient_visible'])
        self.assertEqual(p['report']['reconciliations'][0]['historical_methods'],['CARD'])
        self.assertTrue(any(r.get('preserved_without_reissue') for r in p['report']['reconciliations']))
    def test_finalizer_is_real_audit_actor_not_guessed_doctor(self):
        s=fixture();s['identity.person'].append({'id':uid(19)});s['audit.audit_log'][0]['actor_person_id']=uid(19)
        p=m.build_plan(s)
        revision=next(r['target'] for r in p['rows'] if r['target_table']=='care.visit_report_revision')
        self.assertEqual(revision['validated_by'],uid(19))
    def test_missing_finalizer_is_quarantined(self):
        s=fixture();s['audit.audit_log']=[]
        self.assertEqual(m.build_plan(s)['rows'],[])
    def test_repeatable_ids_and_fingerprint(self):
        self.assertEqual(m.build_plan(fixture()),m.build_plan(fixture()))
        s=fixture();s['consultation.consultation_note'][0]['content']='Changed'
        self.assertNotEqual(m.build_plan(s)['source_fingerprint'],m.build_plan(fixture())['source_fingerprint'])
    def test_unpaid_appointment_does_not_bypass_cash(self):
        s=fixture();s['consultation.consultation']=[];s['appointment.appointment'][0]['status']='SCHEDULED'
        s['billing.invoice'][0]['amount_paid']='0';s['billing.invoice'][0]['status']='ISSUED';s['billing.payment']=[]
        p=m.build_plan(s);self.assertEqual(p['report']['anomalies'],[])
        visit=next(x['target'] for x in p['rows'] if x['target_table']=='care.care_visit')
        self.assertEqual(visit['status'],'PENDING_PAYMENT');self.assertIsNone(visit['started_at'])
    def test_paid_appointment_requires_reprogramming(self):
        s=fixture();s['consultation.consultation']=[];s['appointment.appointment'][0]['status']='SCHEDULED'
        p=m.build_plan(s)
        self.assertEqual(next(x['target']['status'] for x in p['rows'] if x['target_table']=='care.care_visit'),'AWAITING_SCHEDULING')
    def test_bad_actor_quarantines_linked_appointment_not_invented(self):
        s=fixture();s['professional.professional']=[];p=m.build_plan(s)
        self.assertEqual(p['rows'],[]);self.assertEqual(len(p['report']['anomalies']),1)
    def test_in_progress_quarantined(self):
        s=fixture();s['consultation.consultation'][0]['status']='IN_PROGRESS';p=m.build_plan(s)
        self.assertEqual(p['rows'],[]);self.assertEqual(p['report']['anomalies'][0]['code'],'CONSULTATION_NOT_COMPLETED')
    def test_ambiguous_invoice_quarantines_all_targets(self):
        s=fixture();s['billing.invoice'].append({**s['billing.invoice'][0],'id':uid(21)})
        self.assertEqual(m.build_plan(s)['rows'],[])
    def test_mismatched_ledger_does_not_fabricate_payment(self):
        s=fixture();s['billing.payment'][0]['amount']='99'
        p=m.build_plan(s);self.assertEqual(p['rows'],[])
        self.assertEqual(p['report']['anomalies'][0]['code'],'PAYMENT_LEDGER_MISMATCH')
    def test_document_patient_mismatch_quarantines(self):
        s=fixture();s['document.document'][0]['owner_patient_id']=uid(99)
        self.assertEqual(m.build_plan(s)['rows'],[])
    def test_timeline_not_guessed(self):
        s=fixture();s['consultation.consultation'][0]['completed_at']='2024-01-01T00:00:00+00:00'
        self.assertEqual(m.build_plan(s)['rows'],[])
    def test_retention_exact_config(self):
        p=m.build_plan(fixture(),40)
        self.assertTrue(p['rows'][0]['target']['retention_expires_at'].startswith('2065-'))
        self.assertEqual(m.retention(m.timestamp('2024-02-29T00:00:00+00:00'),30),'2054-02-28T00:00:00+00:00')
    def test_source_links_exclude_only_rows_in_persistent_migration_mapping(self):
        class Rows:
            def fetchall(self):return []
        class Connection:
            def __init__(self):self.statements=[]
            def execute(self,query):self.statements.append(query);return Rows()
        connection=Connection();m.read_snapshot(connection)
        source_links=next(q for q in connection.statements if 'FROM document.document_link t' in q)
        self.assertIn('NOT EXISTS',source_links)
        self.assertIn("m.target_table='document.document_link'",source_links)
        self.assertIn('m.target_id=t.id',source_links)
        self.assertNotIn("resource_type='CARE_VISIT'",source_links)
        other=next(q for q in connection.statements if 'FROM document.document t ' in q)
        self.assertNotIn('migration_mapping',other)
    def test_duration_matches_core_and_explicit_seconds_override(self):
        self.assertEqual(m.follow_up_default({}),604800)
        self.assertEqual(m.follow_up_default({'CARE_FOLLOW_UP_DURATION':'P7D'}),604800)
        self.assertEqual(m.follow_up_default({'CARE_FOLLOW_UP_DURATION':'PT168H'}),604800)
        self.assertEqual(m.follow_up_default({'CARE_FOLLOW_UP_DURATION':'P1DT2H3M4S'}),93784)
        self.assertEqual(m.follow_up_default({'CARE_FOLLOW_UP_SECONDS':'48','CARE_FOLLOW_UP_DURATION':'broken'}),48)
        with self.assertRaises(ValueError):m.follow_up_default({'CARE_FOLLOW_UP_DURATION':'P'})
        with self.assertRaises(ValueError):m.follow_up_default({'CARE_FOLLOW_UP_SECONDS':'0'})
    def test_cli_exception_never_exposes_sql_dsn_or_health_data(self):
        for error in (RuntimeError('postgres://secret:password@host diagnosis=private'),
                      m.MigrationError('Private patient name'),ValueError('Secret token')):
            stderr=io.StringIO()
            with patch.object(m,'main',side_effect=error),contextlib.redirect_stderr(stderr):
                self.assertEqual(m.run_cli(),1)
            self.assertNotIn(str(error),stderr.getvalue())
            self.assertNotIn('Traceback',stderr.getvalue())
    def test_existing_private_plan_is_chmodded(self):
        import tempfile
        with tempfile.TemporaryDirectory() as temp:
            path=Path(temp)/'plan.json';path.write_text('old');path.chmod(0o644)
            m.write_private(path,{'safe':True})
            self.assertEqual(path.stat().st_mode & 0o777,0o600)
    def test_apply_requires_pause_before_connection(self):
        with self.assertRaisesRegex(ValueError,'writes-paused'):
            m.apply(None,m.build_plan(fixture()),'x',False)
    def test_rollback_requires_pause(self):
        with self.assertRaisesRegex(ValueError,'writes-paused'):
            m.rollback(None,'x',False)

if __name__=='__main__': unittest.main()
