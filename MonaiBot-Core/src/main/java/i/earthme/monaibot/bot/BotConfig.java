package i.earthme.monaibot.bot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.util.UUID;

public record BotConfig(
        @SerializedName("token")
        String token,
        @SerializedName("websocket_url")
        String wsUrl,
        @SerializedName("enable_bot_log")
        boolean enableBotLog,
        @SerializedName("data_uuid")
        UUID dataUUID
) {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public String toJson() {
        return gson.toJson(this);
    }

    public static BotConfig fromJson(String str) {
        return gson.fromJson(str, BotConfig.class);
    }
}
