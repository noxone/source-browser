package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ValueObject;
import org.jooq.impl.AbstractConverter;

import java.util.function.Function;

/**
 * Base converter for all wrapper types that hold a {@code Long} internally.
 * Encapsulates the conversion logic shared by all Long-based identifiers.
 *
 * @param <W> the wrapper type (must implement {@link ValueObject}{@code <Long>})
 */
public abstract class LongIdentifierConverter<W extends ValueObject<Long>>
        extends AbstractConverter<Long, W> {

    private final Function<Long, W> wrapper;

    protected LongIdentifierConverter(Class<W> wrapperType, Function<Long, W> wrapper) {
        super(Long.class, wrapperType);
        this.wrapper = wrapper;
    }

    @Override
    public W from(Long databaseValue) {
        return databaseValue == null ? null : wrapper.apply(databaseValue);
    }

    @Override
    public Long to(W userObject) {
        return userObject == null ? null : userObject.value();
    }
}
