package org.novasos.healthysv2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import org.novasos.healthysv2.shared.mapping.CentralMapperConfig;
import org.novasos.healthysv2.shared.mapping.EntityMapper;

class MapStructConventionsTests {

    private final ConventionMapper mapper =
            Mappers.getMapper(ConventionMapper.class);

    @Test
    void mapsEntitiesAndDtosUsingTheCentralConfiguration() {
        SampleEntity entity = new SampleEntity();
        entity.setName("Patient");

        SampleDto dto = mapper.toDto(entity);
        SampleEntity mappedBack = mapper.toEntity(dto);

        assertThat(dto.name()).isEqualTo("Patient");
        assertThat(mappedBack.getName()).isEqualTo("Patient");
    }

    @Test
    void ignoresNullPropertiesDuringPartialUpdates() {
        SampleEntity target = new SampleEntity();
        target.setName("Existing value");

        mapper.update(new SampleDto(null), target);

        assertThat(target.getName()).isEqualTo("Existing value");
    }

    @Mapper(config = CentralMapperConfig.class)
    interface ConventionMapper
            extends EntityMapper<SampleDto, SampleEntity> {
    }

    record SampleDto(String name) {
    }

    public static class SampleEntity {

        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
