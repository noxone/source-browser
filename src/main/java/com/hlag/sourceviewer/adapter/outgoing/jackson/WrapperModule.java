package com.hlag.sourceviewer.adapter.outgoing.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.hlag.sourceviewer.domain.model.identifier.ValueObject;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;

/**
 * Jackson module that transparently serializes and deserializes all
 * {@link ValueObject} implementations.
 *
 * <p>During serialization, only the inner value (from {@link ValueObject#value()})
 * is written, not the surrounding object. During deserialization, the
 * inner value is read and wrapped via the single record constructor.</p>
 *
 * <p>This module is registered globally via {@link WrapperModuleCustomizer}
 * in the Quarkus Jackson ObjectMapper.</p>
 *
 * <p>Requirement: Wrapper types are Java {@code record}s with exactly one
 * field named {@code value} and implement {@link ValueObject}.</p>
 */
public class WrapperModule extends SimpleModule {

    public WrapperModule() {
        super("ValueObjectWrapperModule");
    }

    @Override
    public void setupModule(SetupContext context) {
        super.setupModule(context);
        context.addBeanSerializerModifier(new ValueObjectSerializerModifier());
        context.addBeanDeserializerModifier(new ValueObjectDeserializerModifier());
    }

    // ── Serialization ─────────────────────────────────────────────────────────

    private static class ValueObjectSerializerModifier
            extends com.fasterxml.jackson.databind.ser.BeanSerializerModifier {

        @Override
        public JsonSerializer<?> modifySerializer(
                SerializationConfig config,
                BeanDescription description,
                JsonSerializer<?> serializer) {

            if (ValueObject.class.isAssignableFrom(description.getBeanClass())) {
                return new ValueObjectSerializer<>();
            }
            return serializer;
        }
    }

    private static class ValueObjectSerializer<T extends ValueObject<?>>
            extends StdSerializer<T> {

        protected ValueObjectSerializer() {
            super(ValueObject.class, false);
        }

        @Override
        public void serialize(T valueObject, JsonGenerator generator,
                              SerializerProvider provider) throws IOException {
            provider.defaultSerializeValue(valueObject.value(), generator);
        }
    }

    // ── Deserialization ───────────────────────────────────────────────────────

    private static class ValueObjectDeserializerModifier
            extends com.fasterxml.jackson.databind.deser.BeanDeserializerModifier {

        @Override
        public JsonDeserializer<?> modifyDeserializer(
                DeserializationConfig config,
                BeanDescription description,
                JsonDeserializer<?> deserializer) {

            Class<?> beanClass = description.getBeanClass();
            if (ValueObject.class.isAssignableFrom(beanClass) && beanClass.isRecord()) {
                return new ValueObjectDeserializer<>(beanClass);
            }
            return deserializer;
        }
    }

    @SuppressWarnings("unchecked")
    private static class ValueObjectDeserializer<T> extends StdDeserializer<T> {

        private final Class<T> wrapperClass;

        protected ValueObjectDeserializer(Class<T> wrapperClass) {
            super(wrapperClass);
            this.wrapperClass = wrapperClass;
        }

        @Override
        public T deserialize(JsonParser parser, DeserializationContext context)
                throws IOException {

            RecordComponent[] components = wrapperClass.getRecordComponents();
            if (components == null || components.length != 1) {
                throw new IllegalStateException(
                        "ValueObject wrapper must have exactly one record field: "
                                + wrapperClass.getName());
            }

            Class<?> valueType = components[0].getType();
            Object innerValue = context.readValue(parser, valueType);

            try {
                Constructor<T> constructor = wrapperClass.getDeclaredConstructor(valueType);
                return constructor.newInstance(innerValue);
            } catch (ReflectiveOperationException exception) {
                throw new IOException(
                        "Could not instantiate " + wrapperClass.getName(), exception);
            }
        }
    }
}
