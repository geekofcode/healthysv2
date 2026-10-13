create table care.physician_queue (
 id uuid primary key,
 organization_id uuid not null references organization.organization(id),
 professional_id uuid not null references professional.professional(id),
 local_date date not null,
 version bigint not null default 0,
 custom_order boolean not null default false,
 unique(organization_id, professional_id, local_date)
);
create table care.queue_entry (
 visit_id uuid primary key references care.care_visit(id),
 queue_id uuid not null references care.physician_queue(id),
 position bigint not null,
 presence varchar(16) not null default 'WAITING' check(presence in ('WAITING','PRESENT','CALLED'))
);
create index queue_entry_order_idx on care.queue_entry(queue_id,position,visit_id);
