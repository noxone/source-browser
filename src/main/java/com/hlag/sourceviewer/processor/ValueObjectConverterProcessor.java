package com.hlag.sourceviewer.processor;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates a JPA {@code AttributeConverter} for every record that implements
 * {@code ValueObject<T>}.
 *
 * <p>For a record {@code BranchName(String value) implements ValueObject<String>}
 * the processor emits {@code BranchNameConverter} in the converter package with
 * {@code @Converter(autoApply = true)}, so no hand-written converter files are
 * needed.</p>
 *
 * <p>The processor is compiled separately in the {@code generate-sources} phase
 * (to {@code target/processor-classes}) before the main compilation runs.</p>
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class ValueObjectConverterProcessor extends AbstractProcessor {

    private static final String VALUE_OBJECT_IFACE =
            "com.hlag.sourceviewer.domain.model.identifier.ValueObject";
    private static final String CONVERTER_PACKAGE =
            "com.hlag.sourceviewer.domain.model.converter";

    private final Set<String> generated = new HashSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) return false;

        TypeElement valueObjectType =
                processingEnv.getElementUtils().getTypeElement(VALUE_OBJECT_IFACE);
        if (valueObjectType == null) return false;

        for (Element element : roundEnv.getRootElements()) {
            if (element.getKind() != ElementKind.RECORD) continue;
            TypeElement recordType = (TypeElement) element;

            for (TypeMirror iface : recordType.getInterfaces()) {
                if (!(iface instanceof DeclaredType dt)) continue;
                if (!((TypeElement) dt.asElement()).getQualifiedName()
                        .contentEquals(VALUE_OBJECT_IFACE)) continue;

                List<? extends TypeMirror> args = dt.getTypeArguments();
                if (args.isEmpty()) continue;
                if (!(args.get(0) instanceof DeclaredType dbDeclared)) continue;

                String voName = recordType.getSimpleName().toString();
                if (!generated.add(voName)) continue;

                generateConverter(recordType, (TypeElement) dbDeclared.asElement());
            }
        }
        return false;
    }

    private void generateConverter(TypeElement voType, TypeElement dbTypeElement) {
        String voSimple    = voType.getSimpleName().toString();
        String voQualified = voType.getQualifiedName().toString();
        String dbSimple    = dbTypeElement.getSimpleName().toString();
        String converterFqn = CONVERTER_PACKAGE + "." + voSimple + "Converter";

        Filer filer = processingEnv.getFiler();
        try {
            JavaFileObject file = filer.createSourceFile(converterFqn, voType);
            try (PrintWriter w = new PrintWriter(file.openWriter())) {
                w.println("package " + CONVERTER_PACKAGE + ";");
                w.println();
                w.println("import " + voQualified + ";");
                w.println("import jakarta.persistence.AttributeConverter;");
                w.println("import jakarta.persistence.Converter;");
                w.println();
                w.println("@Converter(autoApply = true)");
                w.println("public class " + voSimple + "Converter");
                w.println("        implements AttributeConverter<" + voSimple + ", " + dbSimple + "> {");
                w.println();
                w.println("    @Override");
                w.println("    public " + dbSimple + " convertToDatabaseColumn(" + voSimple + " attribute) {");
                w.println("        return attribute == null ? null : attribute.value();");
                w.println("    }");
                w.println();
                w.println("    @Override");
                w.println("    public " + voSimple + " convertToEntityAttribute(" + dbSimple + " dbData) {");
                w.println("        return dbData == null ? null : new " + voSimple + "(dbData);");
                w.println("    }");
                w.println("}");
            }
        } catch (FilerException ignored) {
            // already generated in a previous incremental round — skip
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Failed to generate converter for " + voSimple + ": " + e.getMessage(),
                    voType);
        }
    }
}
