package i.earthme.monaibot.function.ai;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class APIDataEntry {
    private final String baseUrl;
    private final String apiKe;
    private final String modelName;

    public int usedCnt = 0;

    public APIDataEntry(String baseUrl, String apiKe, String modelName) {
        this.baseUrl = baseUrl;
        this.apiKe = apiKe;
        this.modelName = modelName;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public String getApiKey() {
        return this.apiKe;
    }

    public String getModelName() {
        return this.modelName;
    }

    public JsonObject toJson() {
        final JsonObject jsonObject = new JsonObject();

        jsonObject.addProperty("base_url", this.baseUrl);
        jsonObject.addProperty("api_key", this.apiKe);
        jsonObject.addProperty("model_name", this.modelName);

        return jsonObject;
    }

    @Contract("_ -> new")
    public static @NotNull APIDataEntry fromJson(@NotNull JsonObject jsonObject) {
        return new APIDataEntry(jsonObject.get("base_url").getAsString(), jsonObject.get("api_key").getAsString(), jsonObject.get("model_name").getAsString());
    }
}
