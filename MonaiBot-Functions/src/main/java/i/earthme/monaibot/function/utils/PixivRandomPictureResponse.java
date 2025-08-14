package i.earthme.monaibot.function.utils;


import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class PixivRandomPictureResponse {
    public static JSONObject getNewLink(int rType, int num) throws IOException {
        final URL url = new URL("https://setu.yuban10703.xyz/setu?num=" + num + "&r18=" + rType + "&replace_url=https://i.pixiv.re");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:108.0) Gecko/20100101 Firefox/108.0");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.connect();

        if (connection.getResponseCode() == 200) {
            byte[] data = BaseUtils.readInputStreamToByte(connection.getInputStream());
            connection.disconnect();
            String jsonText = new String(data);
            return JSONObject.parseObject(jsonText);
        }

        throw new IOException("Unmatched response code:" + connection.getResponseCode());
    }

    public static byte @NotNull [] downloadFromLink(String url1) throws IOException {
        URL url = new URL(url1);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:108.0) Gecko/20100101 Firefox/108.0");
        connection.setConnectTimeout(30_000);
        connection.setReadTimeout(30_000);
        connection.connect();

        if (connection.getResponseCode() == 200) {
            try {
                return BaseUtils.readInputStreamToByte(connection.getInputStream());
            } finally {
                connection.disconnect();
            }
        }

        throw new IOException("Unmatched response code:" + connection.getResponseCode());
    }

    public static byte @NotNull [] downloadFromLink(String url1, Proxy proxy) throws IOException {
        URL url = new URL(url1);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection(proxy);

        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; Win64; x64; rv:108.0) Gecko/20100101 Firefox/108.0");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.connect();

        if (connection.getResponseCode() == 200) {
            try {
                return BaseUtils.readInputStreamToByte(connection.getInputStream());
            } finally {
                connection.disconnect();
            }
        }

        throw new IOException("Unmatched response code:" + connection.getResponseCode());
    }

    public static @NotNull Stream<String> getAllLinks(@NotNull JSONObject link) {
        List<String> links = new ArrayList<>();
        JSONArray dataArray = link.getJSONArray("data");

        for (Object o : dataArray) {
            JSONObject single = (JSONObject) o;
            JSONArray urls = single.getJSONArray("urls");
            for (Object o2 : urls) {
                JSONObject singleUrl = (JSONObject) o2;
                String original = singleUrl.getString("original");
                links.add(original);
            }
        }

        return links.stream();
    }
}