#!/usr/bin/env python3
"""Local backup and real isolated PostgreSQL/MinIO restoration verification.
Credentials are read from environment and never printed. Requires pg_dump/pg_restore.
"""
import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path


class BackupError(ValueError):
    """Controlled operator validation error, safe CLI hides payload details."""


def digest_file(path):
    h=hashlib.sha256()
    with open(path,'rb') as source:
        for part in iter(lambda:source.read(1024*1024),b''):
            h.update(part)
    return h.hexdigest()


def canonical(value):
    return json.dumps(value,sort_keys=True,separators=(',',':'),default=str)


def write(path,data):
    fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
    os.fchmod(fd,0o600)
    with os.fdopen(fd,'w') as out:
        out.write(canonical(data)+'\n')


def pg_env(url):
    from psycopg.conninfo import conninfo_to_dict
    config=conninfo_to_dict(url)
    env=os.environ.copy()
    for key in ('host','port','dbname','user','password','sslmode'):
        if key in config:
            env['PG'+('DATABASE' if key=='dbname' else key.upper())]=config[key]
    return env


def table_manifest(connection):
    from psycopg import sql
    result={}
    tables=connection.execute("SELECT schemaname,tablename FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema') ORDER BY schemaname,tablename").fetchall()
    for schema,table in tables:
        query=sql.SQL('SELECT row_to_json(t)::text FROM {}.{} t').format(sql.Identifier(schema),sql.Identifier(table))
        rows=[canonical(json.loads(row[0])) for row in connection.execute(query).fetchall()]
        h=hashlib.sha256()
        for row in sorted(rows):
            h.update(row.encode());h.update(b'\n')
        result[schema+'.'+table]={'count':len(rows),'sha256':h.hexdigest()}
    return result


def s3_client():
    import boto3
    return boto3.client('s3',endpoint_url=os.environ['BACKUP_S3_ENDPOINT'],
      aws_access_key_id=os.environ['BACKUP_S3_ACCESS_KEY'],aws_secret_access_key=os.environ['BACKUP_S3_SECRET_KEY'])


def backup(directory,writes_paused):
    if not writes_paused:
        raise BackupError('Backup requires --writers-paused (application, AI and purge workers)')
    import psycopg
    directory.mkdir(mode=0o700,parents=True,exist_ok=False)
    dump=directory/'database.dump'
    # A shared exported snapshot guarantees dump and verification manifest see the same source data.
    with psycopg.connect(os.environ['MIGRATION_DATABASE_URL']) as db:
        db.execute('SET TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY')
        snapshot=db.execute('SELECT pg_export_snapshot()').fetchone()[0]
        subprocess.run(['pg_dump','--format=custom','--no-owner','--no-acl','--snapshot='+snapshot,
                        '--file='+str(dump)],env=pg_env(os.environ['MIGRATION_DATABASE_URL']),check=True,capture_output=True)
        tables=table_manifest(db)
    os.chmod(dump,0o600)
    bucket=os.environ['BACKUP_S3_BUCKET'];client=s3_client()
    if client.get_bucket_versioning(Bucket=bucket).get('Status')!='Enabled':
        raise BackupError('Enable source MinIO versioning before backup; unversioned backup cannot prove rollback')
    objects=[]
    blob_dir=directory/'objects';blob_dir.mkdir(mode=0o700)
    for page in client.get_paginator('list_object_versions').paginate(Bucket=bucket):
        for version in page.get('Versions',[]):
            key,vid=version['Key'],version['VersionId']
            filename=hashlib.sha256((key+'\0'+vid).encode()).hexdigest()
            response=client.get_object(Bucket=bucket,Key=key,VersionId=vid)
            file=blob_dir/filename;h=hashlib.sha256()
            fd=os.open(file,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
            with os.fdopen(fd,'wb') as output:
                for part in response['Body'].iter_chunks(chunk_size=1024*1024):
                    output.write(part);h.update(part)
            response['Body'].close()
            objects.append({'key':key,'version_id':vid,'latest':version['IsLatest'],
              'last_modified':version['LastModified'].isoformat(),'sha256':h.hexdigest(),
              'size':version['Size'],'content_type':response.get('ContentType'),'file':filename,'deleted':False})
        for marker in page.get('DeleteMarkers',[]):
            objects.append({'key':marker['Key'],'version_id':marker['VersionId'],'latest':marker['IsLatest'],
              'last_modified':marker['LastModified'].isoformat(),'deleted':True})
    manifest={'format_version':1,'database_sha256':digest_file(dump),'tables':tables,'source_bucket':bucket,
              'objects':sorted(objects,key=lambda r:(r['key'],r['last_modified'],r['latest'],r['version_id']))}
    write(directory/'manifest.json',manifest)
    print('backup_manifest_sha256='+digest_file(directory/'manifest.json'))


def restore_check(directory,isolated):
    if not isolated:
        raise BackupError('Restore requires --isolated-target-confirmed')
    import psycopg
    from psycopg.conninfo import conninfo_to_dict
    source=conninfo_to_dict(os.environ['MIGRATION_DATABASE_URL'])
    target_url=os.environ['RESTORE_DATABASE_URL'];target=conninfo_to_dict(target_url)
    if (source.get('host'),source.get('port'),source.get('dbname')) == (target.get('host'),target.get('port'),target.get('dbname')):
        raise BackupError('Restore database must differ from source')
    manifest=json.loads((directory/'manifest.json').read_text())
    if digest_file(directory/'database.dump')!=manifest['database_sha256']:
        raise BackupError('Database backup checksum mismatch')
    with psycopg.connect(target_url) as db:
        if db.execute("SELECT count(*) FROM pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema')").fetchone()[0]:
            raise BackupError('Restore database must be empty; never clean an existing database automatically')
    subprocess.run(['pg_restore','--exit-on-error','--single-transaction','--no-owner','--no-acl',
                    '--dbname='+target['dbname'],str(directory/'database.dump')],env=pg_env(target_url),check=True,capture_output=True)
    with psycopg.connect(target_url) as db:
        if table_manifest(db)!=manifest['tables']:
            raise BackupError('Restored PostgreSQL row counts/fingerprints differ')
    client=s3_client();bucket=os.environ['RESTORE_S3_BUCKET']
    if bucket==manifest['source_bucket']:
        raise BackupError('Restore bucket must differ from source')
    if client.get_bucket_versioning(Bucket=bucket).get('Status')!='Enabled':
        raise BackupError('Restore bucket versioning must be enabled')
    existing=client.list_object_versions(Bucket=bucket,MaxKeys=1)
    if existing.get('Versions') or existing.get('DeleteMarkers'):
        raise BackupError('Restore bucket must be empty')
    restored=[]
    for obj in manifest['objects']:
        if obj['deleted']:
            response=client.delete_object(Bucket=bucket,Key=obj['key'])
            restored.append({'key':obj['key'],'source_version':obj['version_id'],'restored_version':response.get('VersionId'),'deleted':True})
            continue
        file=directory/'objects'/obj['file']
        if digest_file(file)!=obj['sha256'] or file.stat().st_size!=obj['size']:
            raise BackupError('Object backup checksum mismatch')
        with file.open('rb') as data:
            response=client.put_object(Bucket=bucket,Key=obj['key'],Body=data,ContentType=obj.get('content_type') or 'application/octet-stream')
        version=response.get('VersionId')
        read=client.get_object(Bucket=bucket,Key=obj['key'],VersionId=version)
        h=hashlib.sha256()
        for part in read['Body'].iter_chunks(chunk_size=1024*1024):h.update(part)
        read['Body'].close()
        if h.hexdigest()!=obj['sha256']:
            raise BackupError('Restored MinIO version bytes differ')
        restored.append({'key':obj['key'],'source_version':obj['version_id'],'restored_version':version,'sha256':h.hexdigest(),'deleted':False})
    # Verify the latest object/deletion view as well as every version's bytes.
    for obj in manifest['objects']:
        if not obj['latest']:continue
        try:
            current=client.get_object(Bucket=bucket,Key=obj['key'])
        except client.exceptions.ClientError as error:
            if obj['deleted'] and error.response['Error']['Code'] in ('NoSuchKey','404'):continue
            raise
        if obj['deleted']:
            current['Body'].close();raise BackupError('Latest delete marker was not restored')
        h=hashlib.sha256()
        for part in current['Body'].iter_chunks(chunk_size=1024*1024):h.update(part)
        current['Body'].close()
        if h.hexdigest()!=obj['sha256']:raise BackupError('Latest restored object differs')
    proof={'validated':True,'backup_manifest_sha256':digest_file(directory/'manifest.json'),
           'postgresql_tables_verified':len(manifest['tables']),'minio_versions':restored}
    write(directory/'restoration-proof.json',proof)
    print('restoration_test_sha256='+digest_file(directory/'restoration-proof.json'))


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command',choices=('backup','restore-check'))
    parser.add_argument('--directory',required=True)
    parser.add_argument('--isolated-target-confirmed',action='store_true')
    parser.add_argument('--writers-paused',action='store_true')
    args=parser.parse_args();path=Path(args.directory)
    if args.command=='backup':backup(path,args.writers_paused)
    else:restore_check(path,args.isolated_target_confirmed)

def run_cli():
    try:
        main()
        return 0
    except BackupError:
        print('BACKUP_RESTORE_VALIDATION_FAILED: check isolated targets, private manifest and writer suspension.',file=sys.stderr)
    except (OSError, KeyError, ValueError):
        print('BACKUP_RESTORE_CONFIGURATION_OR_INPUT_FAILED: check operator tools, private inputs and environment.',file=sys.stderr)
    except Exception:
        print('BACKUP_RESTORE_OPERATION_FAILED: inspect the controlled operator session; no sensitive diagnostics are emitted.',file=sys.stderr)
    return 1


if __name__=='__main__':
    sys.exit(run_cli())
