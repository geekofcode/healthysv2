import {apiRequest} from './client';

export type PersonAddress = {
  id: string;
  addressId: string;
  addressType: string;
  primary: boolean;
};

export type PersonContact = {
  id: string;
  type: string;
  value: string;
  primary: boolean;
  verified: boolean;
};

export type EmergencyContact = {
  id: string;
  firstName: string;
  lastName?: string;
  relationship?: string;
  phone: string;
  email?: string;
};

export type Person = {
  id: string;
  personNumber: string;
  keycloakUserId: string;
  firstName: string;
  middleName?: string;
  lastName: string;
  gender?: string;
  birthDate?: string;
  preferredLanguageId?: string;
  status: string;
  addresses: PersonAddress[];
  contacts: PersonContact[];
  emergencyContacts: EmergencyContact[];
};

export function getMe(): Promise<Person> {
  return apiRequest<Person>('/persons/me');
}

export type PatientProvisionResponse = {
  patientId: string;
  personId: string;
  patientNumber: string;
  status: string;
  created: boolean;
};

export function provisionPatient(): Promise<PatientProvisionResponse> {
  return apiRequest<PatientProvisionResponse>('/patients/me/provision', {
    method: 'POST',
  });
}

export type ProfileAddress={line1:string;line2?:string;city:string;province?:string;postalCode?:string;countryId?:string};
export type ProfileInput={firstName:string;middleName?:string;lastName:string;gender?:string;birthDate?:string;preferredLanguageId?:string;contacts:Omit<PersonContact,'id'>[];emergencyContacts:Omit<EmergencyContact,'id'>[];homeAddress?:ProfileAddress};
export type MyProfile={person:Person;homeAddress?:ProfileAddress;languages:{id:string;code:string;label:string}[];countries:{id:string;iso2:string;name:string}[]};
export const getMyProfile=()=>apiRequest<MyProfile>('/persons/me/profile');
export const updateMyProfile=(input:ProfileInput)=>apiRequest<MyProfile>('/persons/me',{method:'PUT',body:JSON.stringify(input)});

export const searchPersons=(query:string,page=0)=>apiRequest<import('./organizations').Page<Person>>(`/persons?query=${encodeURIComponent(query)}&page=${page}&size=20`);

export const createPerson=(input:{firstName:string;lastName:string;birthDate?:string;gender?:string})=>apiRequest<Person>('/persons',{method:'POST',body:JSON.stringify(input)});
