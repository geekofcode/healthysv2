# Prescription and pharmacy module boundaries

## Prescription

The `prescription` module owns the ordonnance lifecycle:

- prescription and prescription-item aggregates;
- creation, lookup, search, cancellation and expiration;
- prescribed and dispensed quantities;
- patient-access checks and prescription audit entries;
- the public `PrescriptionDispensing` contract.

Its database tables live in the `prescription` PostgreSQL schema.

## Pharmacy

The `pharmacy` module owns execution of an ordonnance:

- dispense and dispense-item records;
- pharmacist assignment checks;
- medication lots and stock;
- stock replenishment and consumption;
- dispense audit entries.

Its database tables remain in the `pharmacy` PostgreSQL schema.

## Dependency direction

`pharmacy` depends on the public `PrescriptionDispensing` contract. The prescription module never accesses
pharmacy repositories or JPA entities. A dispense runs in one transaction: the prescription row and stock rows
are locked, quantities are validated, the prescription is updated, stock is consumed, and the dispense record is
created. Any failure rolls back the complete operation.

The HTTP contract remains under `/api/v1`. Prescription details and dispense history are retrieved separately by
the web client and composed into the same screen.
