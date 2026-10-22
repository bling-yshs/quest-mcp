package com.yshs.questmcp.core.mcp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Checks the {@code arguments} of a tool call against the tool's input schema.
 *
 * <p>Supports only the JSON Schema subset the tools use: an object with {@code properties}, {@code required} and
 * {@code additionalProperties}, whose properties have a {@code type} of string, integer, number or boolean, optionally
 * with {@code enum}, {@code minimum} and {@code maximum}. Any other keyword is rejected when the validator is built, so
 * that a tool cannot declare a rule that silently goes unchecked.
 */
final class ArgumentValidator {
    /** Keywords that document a schema without constraining it. */
    private static final Set<String> ANNOTATIONS = Set.of("$schema", "title", "description", "default", "examples");
    private static final Set<String> OBJECT_KEYWORDS = Set.of("type", "properties", "required", "additionalProperties");
    private static final Set<String> PROPERTY_KEYWORDS = Set.of("type", "enum", "minimum", "maximum");

    private final Map<String, Property> properties = new LinkedHashMap<>();
    private final Set<String> required = new LinkedHashSet<>();
    private final boolean additionalProperties;

    /** @throws IllegalArgumentException if the schema uses anything outside the supported subset */
    ArgumentValidator(JsonObject schema) {
        checkKeywords(schema, OBJECT_KEYWORDS, "the input schema");
        if (!isString(schema.get("type"), "object")) {
            throw new IllegalArgumentException("The input schema must have \"type\": \"object\"");
        }
        JsonElement declared = schema.get("properties");
        if (declared != null) {
            for (Map.Entry<String, JsonElement> entry : declared.getAsJsonObject().entrySet()) {
                properties.put(entry.getKey(), Property.parse(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
        JsonElement requiredNames = schema.get("required");
        if (requiredNames != null) {
            for (JsonElement name : requiredNames.getAsJsonArray()) {
                if (!properties.containsKey(name.getAsString())) {
                    throw new IllegalArgumentException(
                            "Required property \"" + name.getAsString() + "\" is not declared");
                }
                required.add(name.getAsString());
            }
        }
        JsonElement additional = schema.get("additionalProperties");
        additionalProperties = additional == null || additional.getAsBoolean();
    }

    /** Describes every way {@code arguments} violates the schema; empty if they are valid. */
    List<String> violations(JsonObject arguments) {
        List<String> violations = new ArrayList<>();
        for (String name : required) {
            if (!arguments.has(name)) {
                violations.add("missing required argument \"" + name + "\"");
            }
        }
        for (Map.Entry<String, JsonElement> argument : arguments.entrySet()) {
            Property property = properties.get(argument.getKey());
            if (property != null) {
                property.check(argument.getValue(), violations);
            } else if (!additionalProperties) {
                violations.add("unknown argument \"" + argument.getKey() + "\" (" + (properties.isEmpty()
                        ? "this tool takes no arguments)"
                        : "expected " + quoteAll(properties.keySet()) + ")"));
            }
        }
        return violations;
    }

    private record Property(String name, Type type, @Nullable JsonArray allowed, @Nullable BigDecimal minimum,
            @Nullable BigDecimal maximum) {
        static Property parse(String name, JsonObject schema) {
            checkKeywords(schema, PROPERTY_KEYWORDS, "property \"" + name + "\"");
            JsonElement type = schema.get("type");
            if (!isString(type)) {
                throw new IllegalArgumentException("Property \"" + name + "\" must declare a single \"type\"");
            }
            Type parsedType = Type.parse(type.getAsString());
            if (parsedType == null) {
                throw new IllegalArgumentException("Property \"" + name + "\" has unsupported type " + type);
            }
            BigDecimal minimum = bound(schema, "minimum", parsedType, name);
            BigDecimal maximum = bound(schema, "maximum", parsedType, name);
            JsonElement allowed = schema.get("enum");
            return new Property(name, parsedType, allowed == null ? null : allowed.getAsJsonArray(), minimum, maximum);
        }

        private static @Nullable BigDecimal bound(JsonObject schema, String keyword, Type type, String name) {
            JsonElement bound = schema.get(keyword);
            if (bound == null) {
                return null;
            }
            if (type != Type.INTEGER && type != Type.NUMBER) {
                throw new IllegalArgumentException(
                        "Property \"" + name + "\" has \"" + keyword + "\" but is not numeric");
            }
            return bound.getAsBigDecimal();
        }

        void check(JsonElement value, List<String> violations) {
            if (!type.matches(value)) {
                violations.add("argument \"" + name + "\" must be " + type.description + ", got " + value);
                return;
            }
            if (allowed != null && !allowed.contains(value)) {
                violations.add("argument \"" + name + "\" must be one of " + allowed + ", got " + value);
            }
            BigDecimal number = decimal(value);
            if (minimum != null && number != null && number.compareTo(minimum) < 0) {
                violations.add("argument \"" + name + "\" must be at least " + minimum + ", got " + value);
            }
            if (maximum != null && number != null && number.compareTo(maximum) > 0) {
                violations.add("argument \"" + name + "\" must be at most " + maximum + ", got " + value);
            }
        }
    }

    private enum Type {
        STRING("string", "a string"),
        INTEGER("integer", "an integer"),
        NUMBER("number", "a number"),
        BOOLEAN("boolean", "true or false");

        private final String schemaName;
        private final String description;

        Type(String schemaName, String description) {
            this.schemaName = schemaName;
            this.description = description;
        }

        static @Nullable Type parse(String schemaName) {
            for (Type type : values()) {
                if (type.schemaName.equals(schemaName)) {
                    return type;
                }
            }
            return null;
        }

        boolean matches(JsonElement value) {
            if (!(value instanceof JsonPrimitive primitive)) {
                return false;
            }
            return switch (this) {
                case STRING -> primitive.isString();
                case BOOLEAN -> primitive.isBoolean();
                case NUMBER -> decimal(primitive) != null;
                // As in JSON Schema, 3.0 counts as an integer.
                case INTEGER -> {
                    BigDecimal number = decimal(primitive);
                    yield number != null && number.stripTrailingZeros().scale() <= 0;
                }
            };
        }
    }

    /** The value of a JSON number, or {@code null} if it is not one or too large for Gson (e.g. {@code 1e999999}). */
    private static @Nullable BigDecimal decimal(JsonElement value) {
        if (!(value instanceof JsonPrimitive primitive && primitive.isNumber())) {
            return null;
        }
        try {
            return primitive.getAsBigDecimal();
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void checkKeywords(JsonObject schema, Set<String> supported, String where) {
        for (String keyword : schema.keySet()) {
            if (!supported.contains(keyword) && !ANNOTATIONS.contains(keyword)) {
                throw new IllegalArgumentException("Unsupported keyword \"" + keyword + "\" in " + where);
            }
        }
    }

    private static String quoteAll(Set<String> names) {
        return names.stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(", "));
    }

    private static boolean isString(@Nullable JsonElement element) {
        return element instanceof JsonPrimitive primitive && primitive.isString();
    }

    private static boolean isString(@Nullable JsonElement element, String value) {
        return isString(element) && element.getAsString().equals(value);
    }
}
