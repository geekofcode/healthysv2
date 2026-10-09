import {describe, expect, it} from 'vitest';
import {applicationRoles, canAccessPath} from './roles';
describe('Keycloak application roles', () => {
  it.each([
    ['admin', 'PLATFORM_ADMIN'], ['comptable', 'ACCOUNTANT'], ['gestionnaire', 'HOSPITAL_VIEWER'],
    ['hopital', 'HOSPITAL_ADMIN'], ['laboratoire', 'LAB_TECHNICIAN'], ['medecin', 'DOCTOR'],
    ['nurse', 'NURSE'], ['patient', 'PATIENT'],
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
  it('allows accounting without clinical privileges', () => {
    const roles = applicationRoles(['comptable']);
    expect(canAccessPath(roles, '/billing')).toBe(true);
    expect(canAccessPath(roles, '/consultations/new')).toBe(false);
  });
});
