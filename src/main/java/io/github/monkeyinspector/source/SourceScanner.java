package io.github.monkeyinspector.source;

import io.github.monkeyinspector.edit.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Conservative marker parser. Unsupported syntax is exposed but never rewritten. */
public final class SourceScanner {
    private static final Pattern MARKER = Pattern.compile("(?m)^[\\t ]*//[\\t ]*@mi-(bind[\\t ]+([A-Za-z0-9_.-]{1,128})|end)[\\t ]*\\r?$");
    private static final Pattern NUMBER = Pattern.compile("[+-]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?[fF]?");
    private final Path root;
    public SourceScanner(Path root) throws IOException { this.root = root.toRealPath(); }
    public Path root() { return root; }
    public Path validate(Path file) throws IOException {
        Path real = file.toRealPath();
        if (!real.startsWith(root) || !Files.isRegularFile(real) ||
                !(real.toString().endsWith(".kt") || real.toString().endsWith(".java")))
            throw new IOException("Source path outside allowed project root or unsupported file");
        return real;
    }
    public Map<String, SourceBinding> scan() throws IOException {
        Map<String, SourceBinding> result = new LinkedHashMap<>();
        try (var paths = Files.walk(root)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".kt") || p.toString().endsWith(".java")).toList()) {
                for (var binding : parse(validate(file), Files.readString(file))) {
                    if (result.putIfAbsent(binding.id(), binding) != null)
                        throw new IOException("Duplicate source binding: " + binding.id());
                }
            }
        }
        return Collections.unmodifiableMap(result);
    }
    public List<SourceBinding> parse(Path file, String text) throws IOException {
        List<SourceBinding> result = new ArrayList<>();
        // Ignore marker-looking text inside strings or block comments, retaining actual line comments.
        String markerText = mask(text, false);
        Matcher matcher = MARKER.matcher(markerText);
        String id = null; int start = 0;
        while (matcher.find()) {
            if (matcher.group(2) != null) {
                if (id != null) throw new IOException("Nested source binding");
                id = matcher.group(2); start = matcher.start();
            } else {
                if (id == null) throw new IOException("Unmatched @mi-end");
                String code = text.substring(start, matcher.end());
                result.add(new SourceBinding(id, file, line(text, start), line(text, matcher.end()),
                        start, matcher.end(), SourceRevision.of(text), code, literals(code)));
                id = null;
            }
        }
        if (id != null) throw new IOException("Unclosed source binding: " + id);
        return result;
    }
    private static int line(String text, int offset) {
        return 1 + (int)text.substring(0, offset).chars().filter(c -> c == '\n').count();
    }
    private static Map<TransformProperty, SourceBinding.Literal> literals(String code) {
        var result = new EnumMap<TransformProperty, SourceBinding.Literal>(TransformProperty.class);
        String clean = mask(code, true);
        // Only straight-line setter statements on one receiver are eligible. This deliberately
        // locks conditionals, multiple receivers, nested calls, assignments and other executable code.
        Pattern call = Pattern.compile("([A-Za-z_$][\\w$]*)\\s*\\.\\s*(setLocalTranslation|setLocalScale|setLocalRotation)\\s*\\(");
        Matcher m = call.matcher(clean);
        int previous = 0; String receiver = null;
        Set<TransformProperty> duplicates = new HashSet<>();
        while (m.find()) {
            if (!clean.substring(previous, m.start()).matches("[\\s;]*")) return Map.of();
            if (receiver != null && !receiver.equals(m.group(1))) return Map.of();
            receiver = m.group(1);
            int depth = 1, end = m.end();
            while (end < clean.length() && depth > 0) {
                char c = clean.charAt(end++);
                if (c == '(') depth++; else if (c == ')') depth--;
            }
            if (depth != 0) return Map.of();
            TransformProperty property = Arrays.stream(TransformProperty.values())
                    .filter(p -> p.setter.equals(m.group(2))).findFirst().orElseThrow();
            if (!duplicates.add(property)) return Map.of();
            String args = clean.substring(m.end(), end - 1);
            int offset = m.end();
            Matcher constructor = Pattern.compile("\\s*(?:new\\s+)?(?:com\\.jme3\\.math\\.)?(Vector3f|Quaternion)\\s*\\((.*)\\)\\s*", Pattern.DOTALL).matcher(args);
            if (constructor.matches()) {
                String type = property == TransformProperty.localRotation ? "Quaternion" : "Vector3f";
                if (!constructor.group(1).equals(type)) return Map.of();
                offset += constructor.start(2); args = constructor.group(2);
            } else if (property == TransformProperty.localRotation) { previous = end; m.region(end, clean.length()); continue; }
            var spans = new ArrayList<SourceBinding.Span>();
            var values = new ArrayList<Float>();
            Matcher numbers = NUMBER.matcher(args);
            int last = 0; boolean valid = true;
            while (numbers.find()) {
                if (!args.substring(last, numbers.start()).matches(values.isEmpty() ? "\\s*" : "\\s*,\\s*")) { valid = false; break; }
                try { values.add(Float.parseFloat(numbers.group().replaceAll("[fF]$", ""))); }
                catch (NumberFormatException e) { valid = false; break; }
                spans.add(new SourceBinding.Span(offset + numbers.start(), offset + numbers.end()));
                last = numbers.end();
            }
            valid &= args.substring(last).isBlank();
            if (valid) {
                if (values.size() == 1 && property == TransformProperty.localScale)
                    values = new ArrayList<>(List.of(values.get(0), values.get(0), values.get(0)));
                try {
                    var value = new TransformValue(values); value.validate(property);
                    result.put(property, new SourceBinding.Literal(value, spans));
                } catch (IllegalArgumentException ignored) { /* computed or unsupported */ }
            }
            previous = end; m.region(end, clean.length());
        }
        if (!clean.substring(previous).matches("[\\s;]*")) return Map.of();
        return result;
    }
    /** Length-preserving lexical mask; handles Java/Kotlin strings, text blocks and comments. */
    static String mask(String text, boolean lineComments) {
        StringBuilder out = new StringBuilder(text);
        for (int i = 0; i < text.length();) {
            int start = i, end = i;
            if (text.startsWith("/*", i)) {
                int depth = 1; end = i + 2;
                while (end < text.length() && depth > 0) {
                    if (text.startsWith("/*", end)) { depth++; end += 2; }
                    else if (text.startsWith("*/", end)) { depth--; end += 2; }
                    else end++;
                }
            } else if (text.startsWith("//", i)) {
                end = text.indexOf('\n', i); if (end < 0) end = text.length();
                if (!lineComments) { i = end; continue; }
            } else if (text.startsWith("\"\"\"", i)) {
                end = text.indexOf("\"\"\"", i + 3); end = end < 0 ? text.length() : end + 3;
            } else if (text.charAt(i) == '"' || text.charAt(i) == '\'') {
                char quote = text.charAt(i); end = i + 1;
                while (end < text.length()) {
                    char c = text.charAt(end++);
                    if (c == '\\') end = Math.min(text.length(), end + 1);
                    else if (c == quote) break;
                }
            }
            if (end > start) {
                // Strings remain syntactically non-literal, never disappear into whitespace.
                boolean string = text.charAt(start) == '"' || text.charAt(start) == '\'';
                for (int n = start; n < end; n++)
                    if (text.charAt(n) != '\n' && text.charAt(n) != '\r') out.setCharAt(n, string ? '#' : ' ');
                i = end;
            } else i++;
        }
        return out.toString();
    }
}
