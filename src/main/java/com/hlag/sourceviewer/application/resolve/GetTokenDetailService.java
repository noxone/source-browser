package com.hlag.sourceviewer.application.resolve;

import com.hlag.sourceviewer.domain.model.identifier.FileIdentifier;
import com.hlag.sourceviewer.domain.model.identifier.QualifiedName;
import com.hlag.sourceviewer.domain.model.identifier.SymbolKind;
import com.hlag.sourceviewer.domain.model.source.Symbol;
import com.hlag.sourceviewer.domain.model.source.TypeHierarchyEntry;
import com.hlag.sourceviewer.domain.port.incoming.GetTokenDetailUseCase;
import com.hlag.sourceviewer.domain.port.outgoing.RepositoryStore;
import com.hlag.sourceviewer.domain.port.outgoing.SourceFileRepository;
import com.hlag.sourceviewer.domain.port.outgoing.SymbolRepository;
import com.hlag.sourceviewer.domain.port.outgoing.JsonSerializer;
import com.hlag.sourceviewer.domain.port.outgoing.TokenDetailRepository;
import com.hlag.sourceviewer.domain.port.outgoing.TypeHierarchyRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class GetTokenDetailService implements GetTokenDetailUseCase {

    private final TokenDetailRepository tokenDetailRepository;
    private final TypeHierarchyRepository typeHierarchyRepository;
    private final SymbolRepository symbolRepository;
    private final SourceFileRepository sourceFileRepository;
    private final RepositoryStore repositoryStore;
    private final JsonSerializer jsonMapper;

    @Inject
    public GetTokenDetailService(
            TokenDetailRepository tokenDetailRepository,
            TypeHierarchyRepository typeHierarchyRepository,
            SymbolRepository symbolRepository,
            SourceFileRepository sourceFileRepository,
            RepositoryStore repositoryStore,
            JsonSerializer jsonMapper) {
        this.tokenDetailRepository = tokenDetailRepository;
        this.typeHierarchyRepository = typeHierarchyRepository;
        this.symbolRepository = symbolRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.repositoryStore = repositoryStore;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Optional<Map<String, Object>> getDetail(FileIdentifier fileId, int line, int col) {
        return tokenDetailRepository.findByFileAndPosition(fileId, line, col).map(td -> {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("detailType", td.detailType());

            response.putAll(jsonMapper.parseToMap(td.detail()));

            String detailType = td.detailType();
            if ("METHOD_CALL".equals(detailType) || "METHOD_DECL".equals(detailType)) {
                String declaringClass = (String) response.get("declaringClass");
                String name = (String) response.get("name");
                if (declaringClass != null && name != null) {
                    response.put("overloads", findOverloads(declaringClass, name));
                    response.put("implementations", findImplementations(declaringClass, name));
                }
            } else if ("TYPE_DECL".equals(detailType)) {
                String qualifiedName = (String) response.get("qualifiedName");
                if (qualifiedName != null) {
                    enrichTypeHierarchy(qualifiedName, response);
                    response.put("knownSubtypes", buildSubtypeList(qualifiedName));
                }
            } else if ("TYPE_REF".equals(detailType)) {
                String qualifiedName = (String) response.get("qualifiedName");
                if (qualifiedName != null) {
                    enrichTypeHierarchy(qualifiedName, response);
                    populateTypeLocation(qualifiedName, response);
                }
            } else if ("VARIABLE".equals(detailType)) {
                String typeFqn = (String) response.get("typeFqn");
                if (typeFqn != null) {
                    enrichWithTypeLocation(typeFqn, response);
                }
            }

            return response;
        });
    }

    private void enrichWithTypeLocation(String typeFqn, Map<String, Object> response) {
        symbolRepository.findByQualifiedName(new QualifiedName(typeFqn)).ifPresent(sym -> {
            response.put("typeFileId", sym.fileIdentifier().value());
            sym.lineStart().ifPresent(l -> response.put("typeLineStart", l.value()));
            sourceFileRepository.findByIdentifier(sym.fileIdentifier()).ifPresent(sf -> {
                response.put("typeFilePath", sf.path().value());
                repositoryStore.findByIdentifier(sf.repositoryIdentifier())
                        .ifPresent(r -> response.put("typeRepositoryName", r.name().value()));
            });
        });
    }

    private void enrichTypeHierarchy(String typeFqn, Map<String, Object> response) {
        List<TypeHierarchyEntry> supertypes = typeHierarchyRepository.findSupertypes(typeFqn);
        String superclassFqn = null;
        List<Map<String, Object>> interfaces = new ArrayList<>();
        for (TypeHierarchyEntry entry : supertypes) {
            if ("EXTENDS".equals(entry.relationshipKind())) {
                superclassFqn = entry.supertypeFqn();
            } else if ("IMPLEMENTS".equals(entry.relationshipKind())) {
                Map<String, Object> iface = new LinkedHashMap<>();
                iface.put("qualifiedName", entry.supertypeFqn());
                populateTypeLocation(entry.supertypeFqn(), iface);
                interfaces.add(iface);
            }
        }
        if (superclassFqn != null) {
            response.put("superclassFqn", superclassFqn);
            symbolRepository.findByQualifiedName(new QualifiedName(superclassFqn)).ifPresent(sym -> {
                response.put("superclassFileId", sym.fileIdentifier().value());
                sym.lineStart().ifPresent(l -> response.put("superclassLineStart", l.value()));
                sourceFileRepository.findByIdentifier(sym.fileIdentifier()).ifPresent(sf -> {
                    response.put("superclassFilePath", sf.path().value());
                    repositoryStore.findByIdentifier(sf.repositoryIdentifier())
                            .ifPresent(r -> response.put("superclassRepositoryName", r.name().value()));
                });
            });
        }
        if (!interfaces.isEmpty()) response.put("implementedInterfaces", interfaces);
    }

    private void populateTypeLocation(String fqn, Map<String, Object> target) {
        symbolRepository.findByQualifiedName(new QualifiedName(fqn)).ifPresent(sym -> {
            target.put("fileId", sym.fileIdentifier().value());
            sym.lineStart().ifPresent(l -> target.put("lineStart", l.value()));
            sourceFileRepository.findByIdentifier(sym.fileIdentifier()).ifPresent(sf -> {
                target.put("filePath", sf.path().value());
                repositoryStore.findByIdentifier(sf.repositoryIdentifier())
                        .ifPresent(r -> target.put("repositoryName", r.name().value()));
            });
        });
    }

    /** Returns overloads as structured objects with parameter types and file location. */
    private List<Map<String, Object>> findOverloads(String declaringClass, String methodName) {
        String prefix = declaringClass + "." + methodName;
        List<Symbol> allSymbols = symbolRepository.findByQualifiedNamePrefix(prefix);

        // Index parameter symbols by method FQN
        Map<String, List<Symbol>> paramsByMethod = new java.util.HashMap<>();
        for (Symbol sym : allSymbols) {
            if (sym.kind() == SymbolKind.PARAMETER) {
                String fqn = sym.qualifiedName().value();
                int lastDot = fqn.lastIndexOf('.');
                if (lastDot > 0) {
                    paramsByMethod.computeIfAbsent(fqn.substring(0, lastDot), k -> new ArrayList<>()).add(sym);
                }
            }
        }

        return allSymbols.stream()
                .filter(s -> s.kind() == SymbolKind.METHOD || s.kind() == SymbolKind.CONSTRUCTOR)
                .map(m -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    List<Symbol> params = paramsByMethod.getOrDefault(m.qualifiedName().value(), List.of());
                    String paramStr = params.stream()
                            .map(p -> p.signature().map(s -> s.value()).orElse("?") + " " + p.name().value())
                            .collect(java.util.stream.Collectors.joining(", "));
                    entry.put("signature", methodName + "(" + paramStr + ")");
                    m.lineStart().ifPresent(l -> entry.put("lineStart", l.value()));
                    sourceFileRepository.findByIdentifier(m.fileIdentifier()).ifPresent(sf -> {
                        entry.put("filePath", sf.path().value());
                        repositoryStore.findByIdentifier(sf.repositoryIdentifier())
                                .ifPresent(r -> entry.put("repositoryName", r.name().value()));
                    });
                    return entry;
                })
                .toList();
    }

    private List<Map<String, Object>> findImplementations(String declaringClass, String methodName) {
        List<TypeHierarchyEntry> subtypes = typeHierarchyRepository.findSubtypes(declaringClass);
        List<Map<String, Object>> result = new ArrayList<>();
        for (TypeHierarchyEntry subtype : subtypes) {
            String prefix = subtype.subtypeFqn() + "." + methodName;
            symbolRepository.findByQualifiedNamePrefix(prefix).stream()
                    .filter(s -> s.kind() == SymbolKind.METHOD || s.kind() == SymbolKind.CONSTRUCTOR)
                    .forEach(method -> result.add(buildImplEntry(method)));
        }
        return result;
    }

    private Map<String, Object> buildImplEntry(Symbol method) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("qualifiedName", method.qualifiedName().value());
        entry.put("fileId", method.fileIdentifier().value());
        method.lineStart().ifPresent(l -> entry.put("lineStart", l.value()));
        sourceFileRepository.findByIdentifier(method.fileIdentifier()).ifPresent(sf -> {
            entry.put("filePath", sf.path().value());
            repositoryStore.findByIdentifier(sf.repositoryIdentifier())
                    .ifPresent(r -> entry.put("repositoryName", r.name().value()));
        });
        return entry;
    }

    private List<Map<String, Object>> buildSubtypeList(String typeFqn) {
        return typeHierarchyRepository.findSubtypes(typeFqn).stream().map(entry -> {
            Map<String, Object> dto = new LinkedHashMap<>();
            dto.put("qualifiedName", entry.subtypeFqn());
            dto.put("relationshipKind", entry.relationshipKind());
            populateTypeLocation(entry.subtypeFqn(), dto);
            return dto;
        }).toList();
    }
}
