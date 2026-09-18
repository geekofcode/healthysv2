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
  status: string;
  addresses: PersonAddress[];
  contacts: PersonContact[];
  emergencyContacts: EmergencyContact[];
};

export function getMe(): Promise<Person> {
  return apiRequest<Person>('/persons/me');
}
