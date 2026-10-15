const realmRoleMapping: Record<string, string> = {
  admin: 'PLATFORM_ADMIN', comptable: 'ACCOUNTANT', gestionnaire: 'HOSPITAL_VIEWER',
  hopital: 'HOSPITAL_ADMIN', medecin: 'DOCTOR',
  nurse: 'NURSE', patient: 'PATIENT',
  caissier: 'CASHIER', cashier: 'CASHIER', CASHIER: 'CASHIER',
  hospital_agent: 'HOSPITAL_AGENT', HOSPITAL_AGENT: 'HOSPITAL_AGENT',
};
/** Only explicitly configured realm roles grant application privileges. */
export function applicationRoles(realmRoles: readonly string[]): string[] {
  return [...new Set(realmRoles.flatMap(role =>
    Object.hasOwn(realmRoleMapping, role) ? [realmRoleMapping[role]!] : [],
  ))];
}
const clinical = ['HOSPITAL_ADMIN', 'DOCTOR', 'NURSE'];
const staff = clinical;
const routeRoles: Record<string, readonly string[]> = {
  organizations: ['PLATFORM_ADMIN', ...staff, 'HOSPITAL_VIEWER'], professionals: [...staff, 'HOSPITAL_VIEWER'],
  patients: [...clinical, 'HOSPITAL_AGENT'], care: [...clinical,'HOSPITAL_AGENT'], agenda: [...clinical, 'HOSPITAL_AGENT', 'PATIENT'], consultations: [...clinical,'PATIENT'],
  documents: [...clinical, 'PATIENT'],
  billing: [ 'HOSPITAL_ADMIN', 'NURSE', 'HOSPITAL_AGENT', 'CASHIER', 'ACCOUNTANT', 'PATIENT'], admin: ['PLATFORM_ADMIN'],
};
/** Shared by route guards and navigation; backend authorization remains authoritative. */
export function canAccessPath(roles: readonly string[], path: string): boolean {
  const segments = path.split('/').filter(Boolean);
  const section = segments[0];
  if (roles.includes('PLATFORM_ADMIN')&&['notifications','professional-onboarding','professional-invitation'].includes(section??''))return false;
  if (!section || ['profiles','me', 'registration', 'notifications', 'professional-onboarding', 'professional-invitation'].includes(section)) return true;
  if (roles.includes('PLATFORM_ADMIN') && ['professionals','patients','care','agenda','consultations','documents','billing','employees'].includes(section)) return false;
  if (section === 'admin' && ['professional-requests','audit-security'].includes(segments[1]??'')) return false;
  if (section === 'employees') return roles.some(role=>['HOSPITAL_ADMIN','HOSPITAL_AGENT','HOSPITAL_VIEWER','DOCTOR','NURSE','CASHIER','ACCOUNTANT'].includes(role));
  if(['professionals','patients','care','agenda','consultations','documents','billing'].includes(section)&&roles.some(role=>['HOSPITAL_ADMIN','HOSPITAL_AGENT','HOSPITAL_VIEWER','DOCTOR','NURSE','CASHIER','ACCOUNTANT'].includes(role)))return true;
  if (section === 'professionals' && (segments[1] === 'new' || segments[2] === 'edit')) return roles.includes('HOSPITAL_ADMIN');
  if (section === 'organizations' && segments[2] === 'license') return roles.some(role => ['PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE','HOSPITAL_AGENT','CASHIER','ACCOUNTANT'].includes(role));
  if (section === 'organizations' && segments[1] === 'new') return roles.includes('PLATFORM_ADMIN');
  if (section === 'organizations' &&
      (segments[1] === 'new' || segments[2] === 'edit')) {
    return roles.some(role => ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN'].includes(role));
  }
  if (section === 'patients' && (segments[1] === 'new' || segments[2] === 'edit')) {
    return roles.some(role => ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'HOSPITAL_AGENT', 'NURSE'].includes(role));
  }
  if (['patients','care','agenda','consultations','documents','billing'].includes(section) && roles.some(role=>['HOSPITAL_ADMIN','HOSPITAL_AGENT','HOSPITAL_VIEWER','DOCTOR','NURSE','CASHIER','ACCOUNTANT'].includes(role))) return true;
  const allowed = routeRoles[section];
  return !!allowed && roles.some(role => allowed.includes(role));
}

/** Only the selected, active server-owned profile contributes application privileges. */
export function rolesForProfile(profile?:{type:string;active:boolean}):string[]{
 const known=['PATIENT','PLATFORM_ADMIN','HOSPITAL_ADMIN','HOSPITAL_AGENT','HOSPITAL_VIEWER','DOCTOR','NURSE','CASHIER','ACCOUNTANT'];
 return profile?.active&&known.includes(profile.type)?[profile.type]:[];
}
