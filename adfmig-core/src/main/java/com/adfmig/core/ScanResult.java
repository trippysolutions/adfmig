package com.adfmig.core;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * The outcome of scanning one ADF application root.
 *
 * @param root         absolute path that was scanned
 * @param artifacts    every artifact discovered, in discovery order
 * @param filesVisited total files walked, including ones that were not ADF artifacts
 * @param unparseable  files that looked like XML but could not be read, with the reason
 * @param durationMs   wall-clock duration of the scan
 */
public record ScanResult(
        String root,
        List<AdfArtifact> artifacts,
        int filesVisited,
        Map<String, String> unparseable,
        long durationMs) {

    /** Artifact counts by type, ordered by descending count. */
    public Map<AdfArtifactType, Integer> countsByType() {
        Map<AdfArtifactType, Integer> raw = new LinkedHashMap<>();
        for (AdfArtifact a : artifacts) {
            raw.merge(a.type(), 1, Integer::sum);
        }
        return raw.entrySet().stream()
                .sorted(Map.Entry.<AdfArtifactType, Integer>comparingByValue().reversed()
                        .thenComparing(e -> e.getKey().name()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    /** Artifact counts by migration relevance. */
    public Map<AdfArtifactType.Relevance, Integer> countsByRelevance() {
        Map<AdfArtifactType.Relevance, Integer> out = new TreeMap<>();
        for (AdfArtifact a : artifacts) {
            out.merge(a.type().relevance(), 1, Integer::sum);
        }
        return out;
    }

    public List<AdfArtifact> ofType(AdfArtifactType type) {
        return artifacts.stream()
                .filter(a -> a.type() == type)
                .sorted(Comparator.comparing(AdfArtifact::path))
                .toList();
    }

    public int count(AdfArtifactType type) {
        return (int) artifacts.stream().filter(a -> a.type() == type).count();
    }

    /**
     * Whether the application already exposes an HTTP contract. Applications that do are the
     * cheapest and safest to migrate: their URL structure, operation signatures and security
     * grants are all declared, so the result can be checked against the original response by
     * response.
     */
    public boolean hasExistingRestContract() {
        return count(AdfArtifactType.REST_RESOURCE_REGISTRY) > 0
                || count(AdfArtifactType.REST_RESOURCE) > 0;
    }

    /**
     * Whether the model is consumed by an ADF Faces UI. When true, migrating the backend alone
     * strands the front end: ADF Faces binds through page definitions, not over HTTP.
     */
    public boolean hasAdfFacesConsumers() {
        return count(AdfArtifactType.PAGE_DEFINITION) > 0
                || count(AdfArtifactType.JSF_PAGE) > 0
                || count(AdfArtifactType.JSF_FRAGMENT) > 0;
    }
}
