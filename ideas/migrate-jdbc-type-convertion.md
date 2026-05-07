# Option 3 – Runtime registration via Hibernate `TypeContributor`

Option 2 (the current approach) generates `AttributeConverter` source files at compile
time via an APT processor. Option 3 eliminates all code generation and registers the
same converters dynamically at application startup instead, using Hibernate 6's
`TypeContributor` SPI.

## What changes

| | Option 2 (current) | Option 3 |
|---|---|---|
| Converter source | generated into `target/generated-sources` | none |
| Processor module | `sourceviewer-processor/` | deleted |
| Build overhead | extra Maven module | none |
| Reflection at runtime | none | yes (classpath scan) |
| Quarkus native image | transparent | requires reflection config |

## Migration steps

### 1. Implement `ValueObjectTypeContributor`

Create a class in the main module that implements
`org.hibernate.type.TypeContributor`. Its `contribute()` method scans the
classpath for every `ValueObject<T>` record and registers a corresponding
`AttributeConverter` instance with Hibernate's `TypeDefinitionRegistryBuilder`.

```java
package com.hlag.sourceviewer.domain.model.converter;

import com.hlag.sourceviewer.domain.model.identifier.ValueObject;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.type.descriptor.converter.spi.JpaAttributeConverter;

public class ValueObjectTypeContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions contributions,
                           ServiceRegistry serviceRegistry) {
        // For each ValueObject<T> record found on the classpath:
        //   1. Resolve the T type parameter via reflection
        //   2. Build an AttributeConverter<ValueObject, T> instance using
        //      MethodHandles (constructor lookup for the record)
        //   3. Call contributions.contributeAttributeConverter(converter)
        //      or register via BasicTypeRegistry
    }
}
```

The core of the converter logic is the same as the generated code:
- `convertToDatabaseColumn` → `attribute == null ? null : attribute.value()`
- `convertToEntityAttribute` → `dbData == null ? null : constructor.invoke(dbData)`

Use `Class.forName` or a library like [ClassGraph](https://github.com/classgraph/classgraph)
to enumerate all subtypes of `ValueObject` on the classpath.

### 2. Register the contributor via service loader

Create:

```
src/main/resources/META-INF/services/org.hibernate.type.TypeContributor
```

with content:

```
com.hlag.sourceviewer.domain.model.converter.ValueObjectTypeContributor
```

### 3. Configure Quarkus native image reflection (if needed)

If you build a native image, add a `reflect-config.json` entry (or use
`@RegisterForReflection`) for every `ValueObject` record so GraalVM keeps their
constructors:

```java
@RegisterForReflection(targets = {
    BranchName.class, CommitSha.class, /* ... */
})
class NativeReflectionConfig {}
```

Alternatively, annotate `ValueObject` itself with `@RegisterForReflection` if
Quarkus's indexing picks it up — check the Quarkus native guide for the exact
mechanism in the version you use.

### 4. Remove the processor module

Once the `TypeContributor` is working:

- Delete the `processor/` directory.
- Remove `<module>processor</module>` from the root `pom.xml`.
- Remove the `sourceviewer-processor` `<dependency>` and `<annotationProcessorPath>`
  entries from `pom.xml`.

### Trade-offs to keep in mind

- **Classpath scanning is slow** to write correctly and fragile in modular JVMs.
  Consider maintaining an explicit list of `ValueObject` subclasses instead of
  a dynamic scan, especially since the set of types is small and stable.
- **Native images** need explicit reflection registration; Option 2 needs none.
- **Debuggability**: generated source files are visible in `target/` and IDE-navigable;
  runtime-registered converters are invisible to tooling until runtime.
