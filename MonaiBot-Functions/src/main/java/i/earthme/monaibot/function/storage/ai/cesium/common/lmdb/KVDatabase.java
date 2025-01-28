package i.earthme.monaibot.function.storage.ai.cesium.common.lmdb;

import i.earthme.monaibot.function.storage.ai.cesium.api.database.DatabaseSpec;
import i.earthme.monaibot.function.storage.ai.cesium.api.database.ICloseableIterator;
import i.earthme.monaibot.function.storage.ai.cesium.api.database.IKVDatabase;
import i.earthme.monaibot.function.storage.ai.cesium.api.io.IScannable;
import i.earthme.monaibot.function.storage.ai.cesium.api.io.ISerializer;
import i.earthme.monaibot.function.storage.ai.cesium.common.DefaultSerializers;
import org.lmdbjava.Cursor;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import java.io.IOException;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class KVDatabase<K, V> implements IKVDatabase<K, V> {
    private final LMDBInstance storage;

    private final Env<byte[]> env;
    private final Dbi<byte[]> dbi;

    private final ISerializer<K> keySerializer;
    private final ISerializer<V> valueSerializer;

    public KVDatabase(LMDBInstance storage, DatabaseSpec<K, V> spec) {
        this.storage = storage;

        this.env = this.storage.env;
        this.dbi = this.env.openDbi(spec.getName(), DbiFlags.MDB_CREATE);

        this.keySerializer = DefaultSerializers.getSerializer(spec.getKeyType());
        this.valueSerializer = DefaultSerializers.getSerializer(spec.getValueType());
    }

    @Override
    public V getValue(K key) {
        ReentrantReadWriteLock lock = this.storage.getLock();
        lock.readLock()
                .lock();

        try {
            byte[] buf;
            try {
                buf = this.dbi.get(this.env.txnRead(), this.keySerializer.serialize(key));
            } catch (final IOException e) {
                throw new RuntimeException("Failed to deserialize key", e);
            }

            if (buf == null) {
                return null;
            }

            try {
                return this.valueSerializer.deserialize(buf);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize value", e);
            }
        } finally {
            lock.readLock()
                    .unlock();
        }
    }

    //idea by https://github.com/mo0dss/radon-fabric
    @Override
    @SuppressWarnings("unchecked")
    public <T> void scan(K key, T scanner) {
        if (!(this.valueSerializer instanceof IScannable<?>)) {
            return;
        }

        ReentrantReadWriteLock lock = this.storage.getLock();
        lock.readLock()
                .lock();

        try {
            byte[] buf;
            try {
                buf = this.dbi.get(this.env.txnRead(), this.keySerializer.serialize(key));
            } catch (final IOException e) {
                throw new RuntimeException("Failed to deserialize key", e);
            }

            if (buf == null) {
                return;
            }

            try {
                ((IScannable<T>) this.valueSerializer).scan(buf, scanner);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to scan value", ex);
            }
        } finally {
            lock.readLock()
                    .unlock();
        }
    }

    public void setDirty() {
        this.storage.isDirty = true;
    }

    @Override
    public ISerializer<K> getKeySerializer() {
        return this.keySerializer;
    }

    @Override
    public ISerializer<V> getValueSerializer() {
        return this.valueSerializer;
    }

    public void putValue(Txn<byte[]> txn, K key, byte[] value) {
        try {
            this.dbi.put(txn, this.keySerializer.serialize(key), value);
        } catch (final IOException e) {
            throw new RuntimeException("Could not serialize key", e);
        }
    }

    public void delete(Txn<byte[]> txn, K key) {
        try {
            this.dbi.delete(txn, this.keySerializer.serialize(key));
        } catch (final IOException e) {
            throw new RuntimeException("Could not serialize key", e);
        }
    }

    @Override
    public ICloseableIterator<K> getIterator() {
        final Cursor<byte[]> cursor = this.dbi.openCursor(this.env.txnRead());
        return new CursorIterator<>(cursor, this.keySerializer);
    }

    public Stat getStats() {
        return this.dbi.stat(this.env.txnRead());
    }


    public void close() {
        this.dbi.close();
    }
}
