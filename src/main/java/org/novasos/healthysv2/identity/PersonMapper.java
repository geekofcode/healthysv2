package org.novasos.healthysv2.identity;

import org.mapstruct.Mapper;

import org.novasos.healthysv2.identity.api.EmergencyContactResponse;
import org.novasos.healthysv2.identity.api.PersonAddressResponse;
import org.novasos.healthysv2.identity.api.PersonContactResponse;
import org.novasos.healthysv2.identity.api.PersonResponse;
import org.novasos.healthysv2.shared.mapping.CentralMapperConfig;

@Mapper(config = CentralMapperConfig.class)
interface PersonMapper {

    PersonResponse toResponse(Person person);

    PersonAddressResponse toResponse(PersonAddress address);

    PersonContactResponse toResponse(PersonContact contact);

    EmergencyContactResponse toResponse(EmergencyContact contact);
}
