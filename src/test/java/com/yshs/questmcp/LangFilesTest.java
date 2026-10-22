package com.yshs.questmcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Every translation has the same keys and arguments as English, so no language shows raw keys or drops the URL. */
class LangFilesTest {
    /** Minecraft's argument syntax: {@code %s}, or {@code %2$s} for an explicit position; {@code %%} is a literal. */
    private static final Pattern ARGUMENT = Pattern.compile("%(?:(\\d+)\\$)?s|%%");

    @Test
    void chineseHasExactlyTheEnglishKeysWithTheSameArguments() throws IOException {
        Map<String, Set<Integer>> english = argumentsByKey("en_us");
        Map<String, Set<Integer>> chinese = argumentsByKey("zh_cn");

        assertFalse(english.isEmpty(), "en_us.json has no translations");
        assertEquals(english.keySet(), chinese.keySet(), "zh_cn.json keys differ from en_us.json");
        assertEquals(english, chinese, "zh_cn.json uses different arguments than en_us.json");
    }

    private static Map<String, Set<Integer>> argumentsByKey(String language) throws IOException {
        String path = "/assets/questmcp/lang/" + language + ".json";
        try (InputStream in = LangFilesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is missing");
            JsonObject translations = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            Map<String, Set<Integer>> arguments = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : translations.entrySet()) {
                String text = entry.getValue().getAsString();
                assertFalse(text.isBlank(), language + ": " + entry.getKey() + " is blank");
                arguments.put(entry.getKey(), argumentPositions(text));
            }
            return arguments;
        }
    }

    /** The 1-based argument positions a translation uses, e.g. {@code "%s and %s"} uses 1 and 2. */
    private static Set<Integer> argumentPositions(String text) {
        Set<Integer> positions = new TreeSet<>();
        Matcher matcher = ARGUMENT.matcher(text);
        int next = 1;
        while (matcher.find()) {
            if (matcher.group().equals("%%")) {
                continue;
            }
            positions.add(matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : next++);
        }
        return positions;
    }
}
