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
const clinical = ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'DOCTOR', 'NURSE'];
const staff = clinical;
const routeRoles: Record<string, readonly string[]> = {
  organizations: [...staff, 'HOSPITAL_VIEWER'], professionals: [...staff, 'HOSPITAL_VIEWER'],
  patients: [...clinical, 'HOSPITAL_AGENT'], care: [...clinical,'HOSPITAL_AGENT'], agenda: [...clinical, 'HOSPITAL_AGENT', 'PATIENT'], consultations: clinical,
  documents: [...clinical, 'PATIENT'],
  billing: ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'NURSE', 'HOSPITAL_AGENT', 'CASHIER', 'ACCOUNTANT', 'PATIENT'], admin: ['PLATFORM_ADMIN'],
};
/** Shared by route guards and navigation; backend authorization remains authoritative. */
export function canAccessPath(roles: readonly string[], path: string): boolean {
  const segments = path.split('/').filter(Boolean);
  const section = segments[0];
  if (!section || ['me', 'registration', 'notifications', 'professional-onboarding', 'professional-invitation'].includes(section)) return true;
  if (section === 'professionals' && (segments[1] === 'new' || segments[2] === 'edit')) return roles.includes('PLATFORM_ADMIN');
  if (section === 'organizations' && segments[2] === 'license') return roles.some(role => ['PLATFORM_ADMIN','HOSPITAL_ADMIN','DOCTOR','NURSE','HOSPITAL_AGENT','CASHIER','ACCOUNTANT'].includes(role));
  if (section === 'organizations' && segments[1] === 'new') return roles.includes('PLATFORM_ADMIN');
  if (section === 'organizations' &&
      (segments[1] === 'new' || segments[2] === 'edit')) {
    return roles.some(role => ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN'].includes(role));
  }
  if (section === 'patients' && (segments[1] === 'new' || segments[2] === 'edit')) {
    return roles.some(role => ['PLATFORM_ADMIN', 'HOSPITAL_ADMIN', 'HOSPITAL_AGENT', 'NURSE'].includes(role));
  }
  const allowed = routeRoles[section];
  return !!allowed && roles.some(role => allowed.includes(role));
}

/** Memberships originate from the authenticated backend; only the selected organization applies. */
export function effectiveApplicationRoles(baseRoles:readonly string[],memberships:readonly {organizationId:string;role:string}[],organizationId?:string):string[]{
 const allowed=['DOCTOR','NURSE','HOSPITAL_AGENT','CASHIER','ACCOUNTANT','HOSPITAL_ADMIN'];
 return [...new Set([...baseRoles,...memberships.filter(m=>m.organizationId===organizationId&&allowed.includes(m.role)).map(m=>m.role)])];
}
