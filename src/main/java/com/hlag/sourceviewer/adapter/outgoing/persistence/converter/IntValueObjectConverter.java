package com.hlag.sourceviewer.adapter.outgoing.persistence.converter;

import com.hlag.sourceviewer.domain.model.identifier.ValueObject;
import org.jooq.impl.AbstractConverter;

import java.util.function.Function;

/**
 * Base converter for wrapper types that hold an {@code int}/{@code Integer} internally.
 *
 * @param <W> the wrapper type (must implement {@link ValueObject}{@code <Integer>})
 */
public abstract class IntValueObjectConverter<W extends ValueObject<Integer>>
        extends AbstractConverter<Integer, W> {

    private final Function<Integer, W> wrapper;

    protected IntValueObjectConverter(Class<W> wrapperType, Function<Integer, W> wrapper) {
        super(Integer.class, wrapperType);
        this.wrapper = wrapper;
    }

    @Override
    public W from(Integer databaseValue) {
        return databaseValue == null ? null : wrapper.apply(databaseValue);
    }

    @Override
    public Integer to(W userObject) {
        return userObject == null ? null : userObject.value();
    }
}
