package com.hlag.sourceviewer.adapter.outgoing.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.jackson.ObjectMapperCustomizer;
import jakarta.inject.Singleton;

/**
 * Registriert das {@link WrapperModule} global im Quarkus-{@link ObjectMapper}.
 *
 * <p>Since Quarkus automatically discovers and applies all {@link ObjectMapperCustomizer}
 * implementations, this class is sufficient to activate the module project-wide
 * for all REST endpoints and JSON serializations.</p>
 */
@Singleton
public class WrapperModuleCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper objectMapper) {
        objectMapper.registerModule(new WrapperModule());
    }
}
