package com.enderbyte.enderlib.database.core.mapping;

/**
 * Caches {@link EntityMetadata} per class.
 *
 * <p>Reflection happens once per entity type for the life of the JVM. {@link ClassValue} is used
 * rather than a map because it is designed for exactly this — it is lock-free on the read path and
 * does not keep classes (and therefore plugin class loaders) alive after a reload.
 */
public final class EntityMetadataRegistry {

    private static final ClassValue<EntityMetadata> CACHE = new ClassValue<>() {
        @Override
        protected EntityMetadata computeValue(final Class<?> type) {
            return EntityMetadata.of(type);
        }
    };

    private EntityMetadataRegistry() {
    }

    /**
     * @param entityType annotated entity class
     * @return its metadata, computed on first use
     */
    public static EntityMetadata get(final Class<?> entityType) {
        return CACHE.get(entityType);
    }
}
