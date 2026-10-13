import {describe, expect, it} from 'vitest';
import {applicationRoles, canAccessPath} from './roles';
describe('Keycloak application roles', () => {
  it.each([
    ['admin', 'PLATFORM_ADMIN'], ['comptable', 'ACCOUNTANT'], ['gestionnaire', 'HOSPITAL_VIEWER'],
    ['hopital', 'HOSPITAL_ADMIN'], ['laboratoire', 'LAB_TECHNICIAN'], ['medecin', 'DOCTOR'],
    ['nurse', 'NURSE'], ['patient', 'PATIENT'], ['caissier', 'CASHIER'],
    ['cashier', 'CASHIER'], ['CASHIER', 'CASHIER'], ['hospital_agent', 'HOSPITAL_AGENT'], ['HOSPITAL_AGENT', 'HOSPITAL_AGENT'],
  ])('maps %s explicitly to %s', (realmRole, role) => expect(applicationRoles([realmRole])).toEqual([role]));
  it('ignores technical and unknown roles without assigning pharmacist privileges', () => {
    expect(applicationRoles(['default-roles-healthys', 'offline_access', 'PHARMACIST', 'toString'])).toEqual([]);
  });
  it('allows hospital readers to read but not mutate or access clinical records', () => {
    const roles = applicationRoles(['gestionnaire']);
    expect(canAccessPath(roles, '/organizations/123')).toBe(true);
    expect(canAccessPath(roles, '/professionals/123')).toBe(true);
    for (const path of ['/organizations/new', '/organizations/123/edit', '/professionals/new', '/patients/new', '/billing', '/admin']) expect(canAccessPath(roles, path)).toBe(false);
    expect(roles).not.toContain('HOSPITAL_AGENT');
    expect(roles).not.toContain('HOSPITAL_ADMIN');
  });
  it('shows patient services without organization administration', () => {
    const roles = applicationRoles(['patient']);
    expect(canAccessPath(roles, '/agenda')).toBe(true);
    expect(canAccessPath(roles, '/teleconsultations')).toBe(true);
    expect(canAccessPath(roles, '/organizations')).toBe(false);
    expect(canAccessPath(roles, '/admin')).toBe(false);
  });
  it('restricts professional mutations to platform administrators and patient changes to administrative staff', () => {
    expect(canAccessPath(['HOSPITAL_ADMIN'], '/professionals/123/edit')).toBe(false);
    expect(canAccessPath(['PLATFORM_ADMIN'], '/professionals/123/edit')).toBe(true);
    expect(canAccessPath(['DOCTOR'], '/patients/123/edit')).toBe(false);
    expect(canAccessPath(['HOSPITAL_AGENT'], '/patients/123/edit')).toBe(true);
  });
  it('allows accounting without clinical privileges', () => {
    const roles = applicationRoles(['comptable']);
    expect(canAccessPath(roles, '/billing')).toBe(true);
    expect(canAccessPath(roles, '/consultations/new')).toBe(false);
  });
});

it('allows nurse reception and cashier billing without cashier clinical access', () => {
  expect(canAccessPath(applicationRoles(['nurse']), '/patients/new')).toBe(true);
  expect(canAccessPath(applicationRoles(['nurse']), '/patients/123/edit')).toBe(true);
  expect(canAccessPath(applicationRoles(['hospital_agent']), '/patients')).toBe(true);
  expect(canAccessPath(applicationRoles(['caissier']), '/billing/invoices/123')).toBe(true);
  expect(canAccessPath(applicationRoles(['caissier']), '/patients')).toBe(false);
  expect(canAccessPath(applicationRoles(['caissier']), '/consultations')).toBe(false);
});
