package se.su.dsv.json;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class Json {

    public abstract String toString();

    @SafeVarargs
    public static Json jObject(Tuple<String, Json>... elements) {
        final Map<String, Json> elem = new HashMap<>();
        for (Tuple<String, Json> element : elements) {
            elem.put(element.getFst(), element.getSnd());
        }
        return new JObject(elem);
    }

    public static Json jNumber(long number) {
        return new JNumber(number);
    }

    public static Json jString(String string) {
        return new JString(string);
    }

    private static final class JObject extends Json {
        final Map<String, Json> elements;

        private JObject(final Map<String, Json> elements) {
            this.elements = elements;
        }

        @Override
        public String toString() {
            return elements.entrySet()
                    .stream()
                    .map(entry -> String.format("\"%s\":%s", entry.getKey(), entry.getValue().toString()))
                    .collect(Collectors.joining(",", "{", "}"));
        }
    }

    private static final class JArray extends Json {
        final List<Json> elements;

        private JArray(final List<Json> elements) {
            this.elements = elements;
        }

        @Override
        public String toString() {
            return elements.stream()
                    .map(Json::toString)
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }

    private static final class JNumber extends Json {
        final long number;

        private JNumber(final long number) {
            this.number = number;
        }

        @Override
        public String toString() {
            return Long.toString(number);
        }
    }

    private static final class JString extends Json {
        final String string;

        private JString(final String string) {
            this.string = string;
        }

        @Override
        public String toString() {
            return "\"" + string + "\"";
        }
    }
}
