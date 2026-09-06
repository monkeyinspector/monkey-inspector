package io.github.monkeyinspector;

final class JsonWriter {
    private final StringBuilder out = new StringBuilder(16 * 1024);
    private boolean needsComma;

    JsonWriter objectStart() { comma(); out.append('{'); needsComma = false; return this; }
    JsonWriter objectEnd() { out.append('}'); needsComma = true; return this; }
    JsonWriter arrayStart() { comma(); out.append('['); needsComma = false; return this; }
    JsonWriter arrayEnd() { out.append(']'); needsComma = true; return this; }

    JsonWriter name(String name) {
        comma(); stringRaw(name); out.append(':'); needsComma = false; return this;
    }

    JsonWriter value(String value) {
        comma();
        if (value == null) out.append("null"); else stringRaw(value);
        needsComma = true; return this;
    }

    JsonWriter value(boolean value) { comma(); out.append(value); needsComma = true; return this; }
    JsonWriter value(long value) { comma(); out.append(value); needsComma = true; return this; }
    JsonWriter value(double value) {
        comma();
        if (Double.isFinite(value)) out.append(value); else out.append("null");
        needsComma = true; return this;
    }

    JsonWriter raw(String json) { comma(); out.append(json); needsComma = true; return this; }

    private void comma() { if (needsComma) out.append(','); }

    private void stringRaw(String value) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': out.append("\\\""); break;
                case '\\': out.append("\\\\"); break;
                case '\b': out.append("\\b"); break;
                case '\f': out.append("\\f"); break;
                case '\n': out.append("\\n"); break;
                case '\r': out.append("\\r"); break;
                case '\t': out.append("\\t"); break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        out.append('"');
    }

    @Override public String toString() { return out.toString(); }
}
