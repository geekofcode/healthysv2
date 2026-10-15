#!/usr/bin/env python3
"""Deterministic offline planning and guarded PostgreSQL historical import.
No DB write occurs without an explicit apply/verify/rollback command.
"""
import argparse
import hashlib
import json
import os
import re
import sys
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from uuid import UUID, uuid5

class MigrationError(ValueError):
    """Controlled validation failure; CLI output intentionally omits payload details."""


def follow_up_default(environment=None):
    env=os.environ if environment is None else environment
    if 'CARE_FOLLOW_UP_SECONDS' in env:
        seconds=int(env['CARE_FOLLOW_UP_SECONDS'])
    else:
        duration=env.get('CARE_FOLLOW_UP_DURATION','PT168H')
        match=re.fullmatch(r'P(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?',duration)
        if not match or not any(match.groups()):
            raise MigrationError('Invalid CARE_FOLLOW_UP_DURATION; integer ISO days/hours/minutes/seconds required')
        days,hours,minutes,seconds=(int(part or 0) for part in match.groups())
        seconds=days*86400+hours*3600+minutes*60+seconds
    if seconds<=0:
        raise MigrationError('Follow-up duration must be positive')
    return seconds


NAMESPACE = UUID('d5f19097-0571-4bed-867e-e4d47b9d3021')
SOURCE_TABLES = (
 'consultation.consultation', 'appointment.appointment', 'professional.professional',
 'patient.patient', 'organization.organization', 'identity.person',
 'consultation.consultation_note', 'consultation.diagnosis',
 'consultation.clinical_observation', 'consultation.treatment_plan',
 'consultation.follow_up', 'consultation.vital_sign', 'registration.encounter',
 'billing.invoice', 'billing.invoice_item', 'billing.payment', 'billing.payment_transaction',
 'document.document', 'document.document_link', 'prescription.prescription', 'prescription.prescription_item',
 'laboratory.lab_order', 'catalog.diagnosis_catalog', 'catalog.medication_catalog', 'audit.audit_log',
)
TARGET_KEYS = {'care.care_episode':'id', 'care.care_visit':'id', 'care.visit_report':'visit_id',
 'care.visit_report_revision':'id', 'billing.care_invoice':'episode_id', 'document.document_link':'id'}


def canonical(value):
    def encode(item):
        if isinstance(item, (datetime, UUID, Decimal)):
            return str(item)
        raise TypeError(type(item).__name__)
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False, default=encode)


def fingerprint(value):
    return hashlib.sha256(canonical(value).encode()).hexdigest()


def ident(kind, source):
    return str(uuid5(NAMESPACE, f'{kind}:{source}'))


def timestamp(value):
    date = datetime.fromisoformat(str(value).replace('Z', '+00:00'))
    if date.tzinfo is None:
        raise MigrationError('Timestamp without timezone')
    return date


def retention(date, years):
    try:
        return date.replace(year=date.year + years).isoformat()
    except ValueError:
        return date.replace(year=date.year + years, day=28).isoformat()


def grouped(snapshot, table, field):
    result = {}
    for row in sorted(snapshot.get(table, []), key=lambda x: str(x.get('id', ''))):
        result.setdefault(str(row.get(field)), []).append(row)
    return result


def build_plan(snapshot, retention_years=30, follow_up_seconds=604800):
    """No invented identifiers, actors, payment amounts, clinical dates or diagnosis."""
    if not 1 <= retention_years <= 200 or follow_up_seconds <= 0:
        raise MigrationError('Invalid retention/follow-up configuration')
    rows, anomalies, reconciliations = [], [], []
    ids = lambda table: {str(r['id']): r for r in snapshot.get(table, [])}
    professionals, patients, organizations, persons = map(ids, (
        'professional.professional', 'patient.patient', 'organization.organization', 'identity.person'))
    appointments = ids('appointment.appointment')
    consultations = snapshot.get('consultation.consultation', [])
    by_appointment = grouped(snapshot, 'consultation.consultation', 'appointment_id')
    invoices = grouped(snapshot, 'billing.invoice', 'encounter_id')
    encounters = grouped(snapshot, 'registration.encounter', 'appointment_id')
    payments = grouped(snapshot, 'billing.payment', 'invoice_id')
    diagnoses_catalog = ids('catalog.diagnosis_catalog')
    parts = {table: grouped(snapshot, 'consultation.' + table, 'consultation_id') for table in
             ('consultation_note', 'diagnosis', 'clinical_observation', 'treatment_plan', 'follow_up', 'vital_sign')}

    def anomaly(table, source, code):
        anomalies.append({'source_table':table, 'source_id':str(source['id']), 'code':code})

    def add(source_table, source, table, target):
        rows.append({'source_table':source_table, 'source_id':str(source['id']),
                     'target_table':table, 'target_id':str(target[TARGET_KEYS[table]]),
                     'target':target, 'source_payload':source})

    def matched_invoice(source, appointment_id):
        encounter_id = source.get('encounter_id')
        if not encounter_id and appointment_id:
            matches = encounters.get(str(appointment_id), [])
            if len(matches) == 1:
                encounter_id = matches[0]['id']
        matches = invoices.get(str(encounter_id), []) if encounter_id else []
        if len(matches) > 1:
            raise MigrationError('AMBIGUOUS_INVOICE')
        if not matches:
            return None, False
        invoice = matches[0]
        if (str(invoice['patient_id']) != str(source['patient_id']) or
            str(invoice['organization_id']) != str(source['organization_id'])):
            raise MigrationError('INVOICE_IDENTITY_MISMATCH')
        total = Decimal(str(invoice['total_amount']))
        paid = Decimal(str(invoice['amount_paid']))
        confirmed = [p for p in payments.get(str(invoice['id']), []) if p['status']=='COMPLETED']
        net = sum((Decimal(str(p['amount'])) for p in confirmed), Decimal(0))
        if paid != net or paid > total or paid < 0 or total < 0:
            raise MigrationError('PAYMENT_LEDGER_MISMATCH')
        if any(p['currency'] != invoice['currency'] for p in confirmed):
            raise MigrationError('PAYMENT_CURRENCY_MISMATCH')
        # Electronic payments remain old, immutable historical receipts; never synthesize cash operations.
        reconciliations.append({'invoice_id':str(invoice['id']), 'source_id':str(source['id']),
          'total':str(total), 'paid':str(paid), 'payments':len(confirmed),
          'historical_methods':sorted({p['payment_method'] for p in confirmed})})
        return invoice, invoice['status'] in ('ISSUED','PAID') and paid == total and total > 0

    def base(table, source, completed=False):
        doctor = professionals.get(str(source.get('professional_id')))
        if not doctor or str(doctor['person_id']) not in persons:
            raise MigrationError('MISSING_PROFESSIONAL_OR_PERSON')
        if str(source['patient_id']) not in patients or str(source['organization_id']) not in organizations:
            raise MigrationError('MISSING_PATIENT_OR_ORGANIZATION')
        reason = str(source.get('reason') or '').strip()
        if not reason:
            raise MigrationError('MISSING_REASON')
        created = timestamp(source['created_at'])
        appointment_id = source.get('appointment_id') if completed else source['id']
        appointment = appointments.get(str(appointment_id)) if appointment_id else None
        if completed and appointment_id and not appointment:
            raise MigrationError('MISSING_APPOINTMENT')
        if appointment:
            if any(str(appointment.get(k)) != str(source.get(k)) for k in
                   ('patient_id', 'organization_id', 'professional_id')):
                raise MigrationError('APPOINTMENT_IDENTITY_MISMATCH')
            created = min(created, timestamp(appointment['created_at']))
            scheduled = timestamp(appointment['scheduled_start'])
            minutes = (timestamp(appointment['scheduled_end'])-scheduled).total_seconds()/60
        else:
            scheduled = timestamp(source['started_at'])
            minutes = 30 # Legacy encounter has no slot; archival display only, never a new appointment.
        if scheduled < created or not 1 <= minutes <= 240 or int(minutes) != minutes:
            raise MigrationError('INVALID_SCHEDULE_OR_SLOT')
        started, ended = None, None
        if completed:
            started, ended = timestamp(source['started_at']), timestamp(source['completed_at'])
            if started < created or ended < started:
                raise MigrationError('INVALID_CLINICAL_TIMELINE')
        episode_id, visit_id = ident('episode', source['id']), ident('visit', source['id'])
        invoice, paid = matched_invoice(source, appointment_id)
        episode = {'id':episode_id, 'patient_id':str(source['patient_id']),
         'organization_id':str(source['organization_id']), 'reason':reason,
         'status':'CLOSED' if completed else ('ACTIVE' if paid else 'DRAFT'),
         'follow_up_seconds':follow_up_seconds,
         'started_at':started.isoformat() if started else None,
         'expires_at':(started+timedelta(seconds=follow_up_seconds)).isoformat() if started else None,
         'created_at':created.isoformat(), 'retention_expires_at':retention(created,retention_years), 'version':0}
        visit = {'id':visit_id, 'episode_id':episode_id, 'professional_id':str(doctor['id']),
         'scheduled_at':scheduled.isoformat(), 'follow_up':False,
         'status':'COMPLETED' if completed else ('AWAITING_SCHEDULING' if paid else 'PENDING_PAYMENT'),
         'started_at':started.isoformat() if started else None,
         'completed_at':ended.isoformat() if ended else None, 'created_at':created.isoformat(),
         'retention_expires_at':retention(created,retention_years),'version':0,'slot_minutes':int(minutes),'reason':reason}
        add(table, source, 'care.care_episode', episode)
        add(table, source, 'care.care_visit', visit)
        if invoice:
            add(table, source, 'billing.care_invoice', {'episode_id':episode_id,
              'invoice_id':str(invoice['id']), 'organization_id':str(source['organization_id']),
              'idempotency_key':'historical:'+str(source['id']), 'fingerprint':fingerprint(invoice)})
        return visit, doctor, ended

    for c in sorted(consultations, key=lambda r:str(r['id'])):
        start = len(rows)
        try:
            if c['status'] != 'COMPLETED' or not c.get('completed_at'):
                raise MigrationError('CONSULTATION_NOT_COMPLETED')
            if c.get('appointment_id') and len(by_appointment[str(c['appointment_id'])]) != 1:
                raise MigrationError('MULTIPLE_CONSULTATIONS_FOR_APPOINTMENT')
            visit, doctor, ended = base('consultation.consultation', c, True)
            cid = str(c['id'])
            completions=[a for a in snapshot.get('audit.audit_log',[]) if a.get('module')=='CONSULTATION'
              and a.get('entity_type')=='Consultation' and str(a.get('entity_id'))==cid and a.get('action')=='COMPLETE']
            if len(completions)!=1 or str(completions[0].get('actor_person_id')) not in persons:
                raise MigrationError('MISSING_OR_AMBIGUOUS_FINALIZATION_AUTHOR')
            finalizer=str(completions[0]['actor_person_id'])
            notes = parts['consultation_note'].get(cid, [])
            observations = parts['clinical_observation'].get(cid, [])
            if any(str(n['author_id']) not in professionals or str(professionals[str(n['author_id'])]['person_id']) not in persons for n in notes):
                raise MigrationError('MISSING_NOTE_AUTHOR')
            if any(n.get('author_id') and str(n['author_id']) not in professionals for n in observations):
                raise MigrationError('MISSING_OBSERVATION_AUTHOR')
            text = []
            for n in notes:
                text.append(f"[note {n['id']}; auteur {n['author_id']}; {n['created_at']}; {n.get('note_type') or ''}]\n{n['content']}")
            for n in observations:
                text.append(f"[observation {n['id']}; auteur {n.get('author_id') or 'non renseigné'}; {n['observed_at']}] {n['type']}: {n.get('value') or ''}\n{n.get('notes') or ''}")
            for n in parts['vital_sign'].get(cid, []):
                text.append('[constantes historiques] '+canonical(n))
            diagnoses = []
            for n in parts['diagnosis'].get(cid, []):
                catalog = diagnoses_catalog.get(str(n.get('diagnosis_catalog_id')), {})
                description = str(n.get('description') or catalog.get('label') or '').strip()
                if not description:
                    raise MigrationError('MISSING_DIAGNOSIS_DESCRIPTION')
                diagnoses.append(f"[{n['id']}; {n.get('diagnosis_type') or ''}; {n['status']}] {description}")
            # Historical absence is preserved, never invented to satisfy current UI validation.
            fields = {'reason':visit['reason'],'observations':'\n\n'.join(text),
             'diagnosis':'\n'.join(diagnoses),
             'treatment':'\n'.join(f"[{n['id']}; {n['status']}] {n['description']}" for n in parts['treatment_plan'].get(cid,[])),
             'follow_up':'\n'.join(f"[{n['id']}; date {n.get('recommended_date') or 'non renseignée'}] {n.get('instructions') or ''}" for n in parts['follow_up'].get(cid,[]))}
            expires = visit['retention_expires_at']
            add('consultation.consultation', c, 'care.visit_report', {'visit_id':visit['id'],
             'version':0,'current_revision':1,'status':'VALIDATED','patient_visible':False,**fields,
             'updated_at':ended.isoformat(),'updated_by':finalizer,'retention_expires_at':expires})
            add('consultation.consultation', c, 'care.visit_report_revision', {'id':ident('report',cid),
             'visit_id':visit['id'],'revision':1,'patient_visible':False,**fields,'validated_at':ended.isoformat(),
             'validated_by':finalizer,'correction_reason':'Import historique : provenance conservée',
             'retention_expires_at':expires})
            for link in snapshot.get('document.document_link',[]):
                if link['resource_type']=='CONSULTATION' and str(link['resource_id'])==cid:
                    doc = ids('document.document').get(str(link['document_id']))
                    if not doc or str(doc.get('owner_patient_id')) != str(c['patient_id']):
                        raise MigrationError('DOCUMENT_PATIENT_MISMATCH')
                    if doc.get('uploaded_by') and str(doc['uploaded_by']) not in persons:
                        raise MigrationError('MISSING_DOCUMENT_AUTHOR')
                    add('document.document_link',link,'document.document_link',
                        {'id':ident('document-link',link['id']), 'document_id':str(link['document_id']),
                         'resource_type':'CARE_VISIT','resource_id':visit['id'],'created_at':str(link['created_at'])})
            for table in ('prescription.prescription','laboratory.lab_order'):
                for original in snapshot.get(table,[]):
                    if str(original.get('consultation_id'))==cid:
                        if str(original['patient_id'])!=str(c['patient_id']):
                            raise MigrationError('CLINICAL_DEPENDENCY_PATIENT_MISMATCH')
                        author=original.get('prescriber_id') or original.get('ordering_professional_id')
                        if not author or str(author) not in professionals:
                            raise MigrationError('MISSING_CLINICAL_DEPENDENCY_AUTHOR')
                        reconciliations.append({'source_table':table,'source_id':str(original['id']),
                          'consultation_id':cid,'visit_id':visit['id'],'preserved_without_reissue':True})
            if c.get('appointment_id'):
                reconciliations.append({'appointment_id':str(c['appointment_id']),'consultation_id':cid,'visit_id':visit['id']})
        except (ValueError, KeyError, TypeError) as err:
            del rows[start:]
            anomaly('consultation.consultation',c,str(err))

    for a in sorted(appointments.values(),key=lambda r:str(r['id'])):
        if str(a['id']) in by_appointment:
            continue # Linked source is one visit; quarantined consultations also quarantine their appointment.
        start = len(rows)
        try:
            if a['status'] not in ('SCHEDULED','CONFIRMED','CANCELLED'):
                raise MigrationError('APPOINTMENT_WITH_UNRESOLVED_CLINICAL_STATUS')
            visit, _, _ = base('appointment.appointment',a)
            if a['status']=='CANCELLED':
                visit['status']='CANCELLED'
        except (ValueError, KeyError, TypeError) as err:
            del rows[start:]
            anomaly('appointment.appointment',a,str(err))
    # One historical invoice cannot be linked to several imported episodes.
    linked = {}
    for row in rows:
        if row['target_table']=='billing.care_invoice':
            linked.setdefault(row['target']['invoice_id'],[]).append(row['source_id'])
    duplicates = {sid for sids in linked.values() if len(sids)>1 for sid in sids}
    if duplicates:
        removed_visits={r['target_id'] for r in rows if r['source_id'] in duplicates and r['target_table']=='care.care_visit'}
        rows = [r for r in rows if r['source_id'] not in duplicates and not
                (r['target_table']=='document.document_link' and r['target']['resource_id'] in removed_visits)]
        anomalies += [{'source_table':'billing.invoice','source_id':sid,'code':'INVOICE_SHARED_BY_MULTIPLE_EPISODES'} for sid in sorted(duplicates)]
    counts = {}
    for row in rows:
        counts[row['target_table']] = counts.get(row['target_table'],0)+1
    return {'format_version':1,'source_fingerprint':fingerprint(snapshot),
            'retention_years':retention_years,'follow_up_seconds':follow_up_seconds,
            'rows':rows,'report':{'source_counts':{t:len(snapshot.get(t,[])) for t in SOURCE_TABLES},
              'target_counts':counts,'anomalies':anomalies,'reconciliations':reconciliations,
              'source_untouched':True,'cash_operations_created':0,'historical_reports_patient_visible':False}}


def read_snapshot(connection):
    snapshot = {}
    for table in SOURCE_TABLES:
        # This source table also receives generated CARE_VISIT links during import.
        # Exclude only rows owned by the persistent migration ledger, never all
        # CARE_VISIT links: subsequent user writes must still invalidate the snapshot.
        where = (" WHERE NOT EXISTS (SELECT 1 FROM care.migration_mapping m "
                 "WHERE m.target_table='document.document_link' AND m.target_id=t.id)"
                 if table=='document.document_link' else '')
        snapshot[table] = [json.loads(r[0],parse_float=Decimal) for r in connection.execute(
            'SELECT row_to_json(t)::text FROM '+table+' t'+where+' ORDER BY id::text').fetchall()]
    return snapshot


def locked(connection):
    connection.execute('SELECT pg_advisory_xact_lock(72600742)')
    connection.execute('LOCK TABLE '+','.join(SOURCE_TABLES)+' IN SHARE MODE')
    connection.execute('LOCK TABLE '+','.join(TARGET_KEYS)+' IN EXCLUSIVE MODE')


def target_row(connection, table, target_id):
    return connection.execute('SELECT row_to_json(t)::text FROM '+table+' t WHERE '+TARGET_KEYS[table]+'=%s',
                              (target_id,)).fetchone()


def apply(connection, plan, expected, writes_paused):
    if not writes_paused:
        raise MigrationError('Apply requires --writes-paused; stop application writers first')
    locked(connection)
    snapshot = read_snapshot(connection)
    if fingerprint(snapshot) != expected or plan['source_fingerprint'] != expected:
        raise MigrationError('Source snapshot changed since dry-run')
    rebuilt = build_plan(snapshot,plan['retention_years'],plan['follow_up_seconds'])
    if canonical(rebuilt) != canonical(plan):
        raise MigrationError('Reviewed plan differs from deterministic source transformation')
    if plan['report']['anomalies']:
        raise MigrationError('Resolve every anomaly before apply; partial import cannot qualify for cutover')
    run_id = ident('run',expected)
    old = connection.execute('SELECT status FROM care.migration_run WHERE id=%s',(run_id,)).fetchone()
    if old:
        if old[0] in ('APPLIED','VERIFIED'):
            validate_targets(connection,run_id)
            return run_id
        raise MigrationError('Rolled-back fingerprint cannot be reapplied without a new reviewed snapshot')
    connection.execute("INSERT INTO care.migration_run(id,source_fingerprint,status,source_snapshot,report,retention_expires_at) VALUES(%s,%s,'PLANNED',%s::jsonb,%s::jsonb,%s)",
        (run_id,expected,canonical(snapshot),canonical(plan['report']),retention(datetime.now(timezone.utc),plan['retention_years'])))
    for row in plan['rows']:
        table,target=row['target_table'],row['target']
        if target_row(connection,table,row['target_id']):
            raise MigrationError('Target collision: '+table+'/'+row['target_id'])
        cols=list(target)
        connection.execute('INSERT INTO '+table+' ('+','.join(cols)+') VALUES ('+','.join(['%s']*len(cols))+')',
                           tuple(target[c] for c in cols))
        stored=json.loads(target_row(connection,table,row['target_id'])[0])
        connection.execute('INSERT INTO care.migration_mapping(run_id,source_table,source_id,target_table,target_id,target_fingerprint,source_payload) VALUES(%s,%s,%s,%s,%s,%s,%s::jsonb)',
          (run_id,row['source_table'],row['source_id'],table,row['target_id'],fingerprint(stored),canonical(row['source_payload'])))
    connection.execute("UPDATE care.migration_run SET status='APPLIED',applied_at=now() WHERE id=%s",(run_id,))
    return run_id


def validate_targets(connection,run_id):
    mappings=connection.execute('SELECT target_table,target_id,target_fingerprint FROM care.migration_mapping WHERE run_id=%s',(run_id,)).fetchall()
    for table,target_id,expected in mappings:
        if table not in TARGET_KEYS:
            raise MigrationError('Unexpected target table')
        stored=target_row(connection,table,target_id)
        if not stored or fingerprint(json.loads(stored[0])) != expected:
            raise MigrationError('Imported target changed: '+table+'/'+str(target_id))
    return mappings


def verify(connection,run_id,operator,evidence):
    locked(connection)
    run=connection.execute('SELECT source_fingerprint,status,report::text FROM care.migration_run WHERE id=%s FOR UPDATE',(run_id,)).fetchone()
    if not run or run[1] not in ('APPLIED','VERIFIED'):
        raise MigrationError('Run is not applied')
    if fingerprint(read_snapshot(connection)) != run[0]:
        raise MigrationError('Legacy data changed since apply')
    validate_targets(connection,run_id)
    if json.loads(run[2])['anomalies']:
        raise MigrationError('Migration has unresolved anomalies')
    if not evidence.get('journey',{}).get('validated') or not evidence.get('restoration',{}).get('validated'):
        raise MigrationError('Successful journey and restoration evidence required')
    required=('backup_manifest_sha256','restoration_test_sha256','cash_e2e_sha256','migration_review_sha256')
    if not operator.strip() or any(len(str(evidence.get(k,''))) != 64 or any(c not in '0123456789abcdef' for c in str(evidence.get(k,''))) for k in required):
        raise MigrationError('Named operator and SHA256 of backup/restoration/cash E2E/reconciliation evidence required')
    connection.execute("UPDATE care.migration_run SET status='VERIFIED',verified_at=now(),verified_by=%s,evidence=%s::jsonb WHERE id=%s",(operator,canonical(evidence),run_id))


def rollback(connection,run_id,writes_paused,operator=""):
    if writes_paused and not operator.strip():
        raise MigrationError('Rollback requires --operator for traceability')
    if not writes_paused:
        raise MigrationError('Rollback requires --writes-paused')
    locked(connection)
    state=connection.execute('SELECT mode FROM care.cutover_state WHERE singleton=true FOR UPDATE').fetchone()
    if state and state[0]!='LEGACY':
        raise MigrationError('Cutover is active; first stop writers and return gate to LEGACY through approved procedure')
    run=connection.execute('SELECT status FROM care.migration_run WHERE id=%s FOR UPDATE',(run_id,)).fetchone()
    if not run:
        raise MigrationError('Unknown run')
    if run[0]=='ROLLED_BACK':
        return
    mappings=validate_targets(connection,run_id)
    # Document links are polymorphic: check additional clinical links explicitly before deleting a visit.
    imported_links={str(r[1]) for r in mappings if r[0]=='document.document_link'}
    for table,target_id,_ in mappings:
        if table=='care.care_visit':
            links=connection.execute("SELECT id FROM document.document_link WHERE resource_type='CARE_VISIT' AND resource_id=%s",(target_id,)).fetchall()
            if any(str(row[0]) not in imported_links for row in links):
                raise MigrationError('New document links exist for imported visit')
    # Foreign keys reject added follow-ups/reports/cash links; one transaction means no partial rollback.
    order={'document.document_link':0,'care.visit_report_revision':1,'care.visit_report':2,
           'billing.care_invoice':3,'care.care_visit':4,'care.care_episode':5}
    for table,target_id,_ in sorted(mappings,key=lambda r:order[r[0]]):
        connection.execute('DELETE FROM '+table+' WHERE '+TARGET_KEYS[table]+'=%s',(target_id,))
    connection.execute('DELETE FROM care.migration_mapping WHERE run_id=%s',(run_id,))
    connection.execute("UPDATE care.migration_run SET status='ROLLED_BACK',evidence=evidence||jsonb_build_object('rollback',jsonb_build_object('operator',%s::text,'at',now())) WHERE id=%s",(operator,run_id))


def write_private(path,value):
    # Plans contain health data, never print them in application logs or CI artifacts.
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    os.fchmod(fd,0o600)
    with os.fdopen(fd,'w') as out:
        out.write(canonical(value)+'\n')


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('command',nargs='?',default='dry-run',choices=('dry-run','apply','verify','rollback'))
    p.add_argument('--snapshot',help='Offline snapshot JSON; dry-run only')
    p.add_argument('--plan',default='migration-plan.json')
    p.add_argument('--expected-fingerprint')
    p.add_argument('--run-id')
    p.add_argument('--writes-paused',action='store_true')
    p.add_argument('--operator',default='')
    p.add_argument('--evidence')
    p.add_argument('--retention-years',type=int,default=int(os.environ.get('DATA_RETENTION_YEARS','30')))
    p.add_argument('--follow-up-seconds',type=int,default=follow_up_default())
    args=p.parse_args()
    if args.snapshot:
        if args.command!='dry-run':
            p.error('--snapshot is dry-run only')
        plan=build_plan(json.loads(Path(args.snapshot).read_text()),args.retention_years,args.follow_up_seconds)
        write_private(args.plan,plan)
    else:
        import psycopg
        with psycopg.connect(os.environ['MIGRATION_DATABASE_URL']) as connection:
            connection.execute("SET TRANSACTION ISOLATION LEVEL REPEATABLE READ")
            if args.command=='dry-run':
                connection.execute('SET TRANSACTION READ ONLY')
                plan=build_plan(read_snapshot(connection),args.retention_years,args.follow_up_seconds)
                write_private(args.plan,plan)
            elif args.command=='apply':
                plan=json.loads(Path(args.plan).read_text())
                print('run_id='+apply(connection,plan,args.expected_fingerprint,args.writes_paused))
            elif args.command=='verify':
                verify(connection,args.run_id,args.operator,json.loads(Path(args.evidence).read_text()))
            else:
                rollback(connection,args.run_id,args.writes_paused,args.operator)
    if args.command=='dry-run':
        print(canonical({'source_fingerprint':plan['source_fingerprint'], 'counts':plan['report']['target_counts'],
                         'anomaly_count':len(plan['report']['anomalies'])}))

def run_cli():
    try:
        main()
        return 0
    except MigrationError:
        print('MIGRATION_VALIDATION_FAILED: inspect the private reviewed plan and operator prerequisites.',file=sys.stderr)
    except (ValueError, KeyError, OSError):
        print('MIGRATION_CONFIGURATION_OR_INPUT_FAILED: check private input files and environment.',file=sys.stderr)
    except Exception:
        print('MIGRATION_OPERATION_FAILED: check connectivity and database constraints in the controlled operator session.',file=sys.stderr)
    return 1


if __name__=='__main__':
    sys.exit(run_cli())
