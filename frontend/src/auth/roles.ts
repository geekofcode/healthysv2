const realmRoleMapping: Record<string, string> = {
  admin: 'PLATFORM_ADMIN', comptable: 'ACCOUNTANT', gestionnaire: 'HOSPITAL_VIEWER',
  hopital: 'HOSPITAL_ADMIN', laboratoire: 'LAB_TECHNICIAN', medecin: 'DOCTOR',
  nurse: 'NURSE', patient: 'PATIENT',
};
/** Only explicitly configured realm roles grant application privileges. */
export function applicationRoles(realmRoles: readonly string[]): string[] {
  return [...new Set(realmRoles.flatMap(role =>
    Object.hasOwn(realmRoleMapping, role) ? [realmRoleMapping[role]!] : [],
  ))];
}
const clinical = ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'DOCTOR', 'NURSE'];
const staff = [...clinical, 'LAB_TECHNICIAN'];
const routeRoles: Record<string, readonly string[]> = {
  organizations: [...staff, 'HOSPITAL_VIEWER'], professionals: [...staff, 'HOSPITAL_VIEWER'],
  patients: staff, agenda: [...clinical, 'PATIENT'], consultations: clinical,
  laboratory: staff, 'maternal-child': [...clinical, 'PATIENT'], documents: [...clinical, 'PATIENT'],
  chat: [...staff, 'PATIENT'], teleconsultations: [...clinical, 'PATIENT'],
  pharmacy: ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'DOCTOR'],
  billing: ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'ACCOUNTANT', 'PATIENT'], admin: ['PLATFORM_ADMIN'],
};
/** Shared by route guards and navigation; backend authorization remains authoritative. */
export function canAccessPath(roles: readonly string[], path: string): boolean {
  const segments = path.split('/').filter(Boolean);
  const section = segments[0];
  if (!section || ['me', 'registration', 'notifications', 'professional-onboarding', 'professional-invitation'].includes(section)) return true;
  if (section === 'professionals' && (segments[1] === 'new' || segments[2] === 'edit')) return roles.includes('PLATFORM_ADMIN');
  if (section === 'organizations' &&
      (segments[1] === 'new' || segments[2] === 'edit')) {
    return roles.some(role => ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN'].includes(role));
  }
  if (section === 'patients' && (segments[1] === 'new' || segments[2] === 'edit')) {
    return roles.some(role => ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'HOSPITAL_AGENT'].includes(role));
  }
  const allowed = routeRoles[section];
  return !!allowed && roles.some(role => allowed.includes(role));
}
