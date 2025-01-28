package i.earthme.monaibot.function.storage.ai.cesium.api.database;

public interface IKVTransaction<K, V> {
    void add(final K key, final V value);
}
