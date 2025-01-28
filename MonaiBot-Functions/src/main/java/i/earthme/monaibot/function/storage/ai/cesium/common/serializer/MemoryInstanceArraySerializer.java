package i.earthme.monaibot.function.storage.ai.cesium.common.serializer;

import i.earthme.monaibot.function.ai.MemoryEntry;
import i.earthme.monaibot.function.storage.ai.cesium.api.io.ISerializer;
import org.jetbrains.annotations.NotNull;

import java.io.*;

public class MemoryInstanceArraySerializer implements ISerializer<MemoryEntry[]> {
    @Override
    public byte[] serialize(MemoryEntry @NotNull [] input) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final DataOutputStream dos = new DataOutputStream(bos);

        dos.writeInt(input.length);
        for (MemoryEntry record : input) {
            dos.writeUTF(record.role());
            dos.writeUTF(record.content());
        }
        dos.flush();
        dos.close();

        return bos.toByteArray();
    }

    @Override
    public MemoryEntry[] deserialize(byte[] input) throws IOException {
        final ByteArrayInputStream bis = new ByteArrayInputStream(input);
        final DataInputStream dis = new DataInputStream(bis);

        final int len = dis.readInt();
        final MemoryEntry[] records = new MemoryEntry[len];

        for (int i = 0; i < len; i++) {
            records[i] = new MemoryEntry(dis.readUTF(), dis.readUTF());
        }

        return records;
    }
}
