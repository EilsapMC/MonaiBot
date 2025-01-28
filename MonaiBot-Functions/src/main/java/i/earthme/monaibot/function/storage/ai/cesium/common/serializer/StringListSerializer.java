package i.earthme.monaibot.function.storage.ai.cesium.common.serializer;

import i.earthme.monaibot.function.storage.ai.cesium.api.io.ISerializer;

import java.io.IOException;
import java.util.List;

public class StringListSerializer implements ISerializer<List<String>> {
    @Override
    public byte[] serialize(List<String> input) throws IOException {
        return new byte[0];
    }

    @Override
    public List<String> deserialize(byte[] input) throws IOException {
        return List.of();
    }
}
