package org.miau.particleeffects.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Options {

    public static final Options EMPTY = new Options(new LinkedHashMap<>());

    private final Map<String, String> entries;

    private Options(Map<String, String> entries) {
        this.entries = entries;
    }

    public static Options parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (String token : tokenize(raw)) {
            int eq = token.indexOf('=');
            if (eq <= 0) {
                throw new IllegalArgumentException("无效选项 '" + token + "'，请使用 键=值 格式");
            }
            String key = token.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String value = stripQuotes(token.substring(eq + 1).trim());
            if (key.isEmpty()) {
                throw new IllegalArgumentException("无效选项 '" + token + "'：键不能为空");
            }
            if (map.containsKey(key)) {
                throw new IllegalArgumentException("选项 '" + key + "' 重复出现，只允许一次");
            }
            map.put(key, value);
        }
        return new Options(map);
    }

    /**
     * 按空白切分选项，但引号内的空白保留（例。in="slide:up, scale:enlarge"）。     */
    private static List<String> tokenize(String raw) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (quote != 0) {
                current.append(c);
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                current.append(c);
            } else if (Character.isWhitespace(c)) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    /**
     * 校验选项键：出现未知键时抛错并列出可用键，避免拼写错误被静默忽略。     */
    public void checkKeys(String... allowed) {
        if (entries.isEmpty()) {
            return;
        }
        Set<String> set = Set.of(allowed);
        for (String key : entries.keySet()) {
            if (!set.contains(key)) {
                throw new IllegalArgumentException("未知选项 '" + key
                        + "'，可用：" + String.join(" / ", allowed));
            }
        }
    }

    public String get(String key) {
        return entries.get(key);
    }

    public String optString(String key, String defaultValue) {
        String v = entries.get(key);
        return v != null ? v : defaultValue;
    }

    public double optDouble(String key, double defaultValue, double min, double max) {
        String v = entries.get(key);
        if (v == null) {
            return defaultValue;
        }
        double parsed;
        try {
            parsed = Double.parseDouble(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("选项 '" + key + "' 需要数值，收到 '" + v + "'");
        }
        if (Double.isNaN(parsed) || parsed < min || parsed > max) {
            throw new IllegalArgumentException("选项 '" + key + "' 取值应在" + min + " ~ " + max);
        }
        return parsed;
    }

    public int optInt(String key, int defaultValue, int min, int max) {
        String v = entries.get(key);
        if (v == null) {
            return defaultValue;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("选项 '" + key + "' 需要整数，收到 '" + v + "'");
        }
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException("选项 '" + key + "' 取值应在" + min + " ~ " + max);
        }
        return parsed;
    }

    public boolean optBool(String key, boolean defaultValue) {
        String v = entries.get(key);
        if (v == null) {
            return defaultValue;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "true", "1", "yes", "on" -> true;
            case "false", "0", "no", "off" -> false;
            default -> throw new IllegalArgumentException("选项 '" + key + "' 需要布尔。true/false，收。'" + v + "'");
        };
    }

    public <E extends Enum<E>> E optEnum(String key, java.util.function.Function<String, E> parser, E defaultValue) {
        String v = entries.get(key);
        if (v == null) {
            return defaultValue;
        }
        E parsed = parser.apply(v.trim());
        if (parsed == null) {
            throw new IllegalArgumentException("选项 '" + key + "' 取值无效'" + v + "'");
        }
        return parsed;
    }
}