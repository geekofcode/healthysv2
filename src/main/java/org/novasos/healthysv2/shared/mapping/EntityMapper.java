package org.novasos.healthysv2.shared.mapping;

import java.util.List;

import org.mapstruct.MappingTarget;

public interface EntityMapper<D, E> {

    D toDto(E entity);

    List<D> toDto(List<E> entities);

    E toEntity(D dto);

    void update(D dto, @MappingTarget E entity);
}
