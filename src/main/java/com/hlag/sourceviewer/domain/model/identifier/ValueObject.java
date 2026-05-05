package com.hlag.sourceviewer.domain.model.identifier;

/**
 * Marker interface for all value object wrappers.
 *
 * <p>Every wrapper type (record with a single field {@code value})
 * implements this interface. The Jackson module
 * {@code com.hlag.sourceviewer.adapter.outgoing.jackson.WrapperModule}
 * automatically detects implementations and serializes them as
 * a plain value instead of a nested object.</p>
 *
 * @param <T> the type of the enclosed value
 */
public interface ValueObject<T> {

    /**
     * Returns the enclosed value.
     */
    T value();
}
