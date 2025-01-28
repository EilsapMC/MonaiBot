package i.earthme.monaibot.function.storage.ai.cesium.common;

import i.earthme.monaibot.function.ai.MemoryEntry;
import i.earthme.monaibot.function.storage.ai.cesium.api.io.ISerializer;
import i.earthme.monaibot.function.storage.ai.cesium.common.serializer.MemoryInstanceArraySerializer;
import i.earthme.monaibot.function.storage.ai.cesium.common.serializer.StringSerializer;
import i.earthme.monaibot.function.storage.ai.cesium.common.serializer.UUIDSerializer;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import java.util.UUID;

public class DefaultSerializers {
    private static final Reference2ReferenceMap<Class<?>, ISerializer<?>> serializers = new Reference2ReferenceOpenHashMap<>();

    static {
        serializers.put(UUID.class, new UUIDSerializer());
        serializers.put(String.class, new StringSerializer());
        serializers.put(MemoryEntry[].class, new MemoryInstanceArraySerializer());
    }

    @SuppressWarnings("unchecked")
    public static <K> ISerializer<K> getSerializer(Class<K> clazz) {
        ISerializer<?> serializer = DefaultSerializers.serializers.get(clazz);

        if (serializer == null) {
            throw new NullPointerException("No serializer exists for type: " + clazz.getName());
        }

        return (ISerializer<K>) serializer;
    }
}
