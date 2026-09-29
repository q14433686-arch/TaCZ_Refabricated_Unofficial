package cn.sh1rocu.tacz.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.tacz.guns.GunMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 枪包 {@code assets/<ns>/lang/<code>.json} 的"保底"过滤器。
 *
 * <h2>为什么需要它（26.3 专有）</h2>
 * 26.3 起 vanilla 的 {@code ClientLanguage#loadFrom} 去掉了 26.2 及更早版本里
 * 逐命名空间的 {@code try { ... } catch (Exception) { "Skipped language file" }}；
 * {@code appendFrom} 现在只捕获 {@link java.io.IOException}。于是任何一个
 * lang 文件解析失败（Gson 抛出的 {@code JsonSyntaxException} 是 RuntimeException）
 * 都会从 {@code loadFrom} 一路抛到 {@code LanguageManager#onResourceManagerReload}，
 * 那里只记一条 {@code WARN "Unable to load languages: ..."} 就返回——
 * <b>{@code Language.inject} 不会被调用</b>，游戏继续使用 JVM 默认的
 * {@code Language}（只含 Minecraft 自带 jar 里的 en_us）。
 * 玩家看到的就是"语言变成英语 + 所有 mod 文本变成 item.xxx 一类的原始键"，
 * 而且没有任何弹窗，只有日志里一行 WARN。
 *
 * <p>本仓库把 {@code tacz/} 目录下<b>所有</b>枪包合并成一个 {@code tacz_resources} 资源包，
 * 所以任意一个第三方枪包里一个写错的 lang 文件（例如少一个逗号）就能让整局游戏失去翻译。
 * 已知实例：Enlisted Gun Pack v1.2.1.3 的 {@code assets/ww/lang/en_us.json}
 * 在 {@code "ww.gun.p38.desc"} 一行后少了逗号。
 *
 * <h2>做法</h2>
 * 在 {@link cn.sh1rocu.tacz.util.forge.DelegatingPackResources} 层拦截 lang 文件的
 * {@link IoSupplier}：先按 vanilla 同样的宽松规则（允许注释）解析；
 * <ul>
 *   <li>解析成功且根是对象、所有值都是基元 —— 原字节原样放行（零改动）；</li>
 *   <li>根是对象但有非基元值（vanilla 的 {@code GsonHelper.convertToString} 会因此抛异常）——
 *       丢掉那些条目后重新序列化；</li>
 *   <li>解析失败（少逗号、多余内容、根不是对象、空文件……）——
 *       用一个容错的词法扫描把能救的 {@code "key": "value"} 都救回来，重新序列化；</li>
 * </ul>
 * 任何修补都会在日志里以 WARN 指明是哪个枪包的哪个文件、原始错误是什么，
 * 便于转告枪包作者。这里<b>不修改</b>枪包文件本身。
 *
 * <p>只保证"送到 vanilla 手里的一定是合法 JSON 对象"，不承诺救回文件里的每一条文本。
 */
public final class GunPackLangCompat {
    private static final Marker MARKER = MarkerFactory.getMarker("GunPackLang");
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private GunPackLangCompat() {
    }

    /**
     * 资源路径是否是语言文件（{@code lang/<code>.json}）。
     * 不区分 PackType：{@link cn.sh1rocu.tacz.util.forge.PathPackResources#getResource}
     * 会把 SERVER_DATA 下的 {@code lang/} 请求也映射到 assets，统一处理最省事。
     */
    public static boolean isLangFile(Identifier location) {
        String path = location.getPath();
        return path.startsWith("lang/") && path.endsWith(".json");
    }

    /**
     * {@code listResources} 的目录参数是否可能列出语言文件。
     */
    public static boolean mayListLangFiles(String directory) {
        return directory.isEmpty() || "lang".equals(directory) || directory.startsWith("lang/");
    }

    /**
     * 用于日志的枪包描述：{@code <namespace> (<文件名>)}。
     * 子包的 title 由 {@code GunPackLoader} 设为 zip 文件名 / 目录名。
     */
    public static String describe(PackResources pack) {
        String id = pack.packId();
        String title;
        try {
            title = pack.location().title().getString();
        } catch (RuntimeException e) {
            title = "";
        }
        return title.isEmpty() || title.equals(id) ? id : id + " (" + title + ")";
    }

    /**
     * 包装 {@code listResources} 的输出，使其中的语言文件经过 {@link #wrap}。
     */
    public static PackResources.ResourceOutput wrapOutput(String sourceName, PackResources.ResourceOutput output) {
        return (location, supplier) -> output.accept(location, isLangFile(location) ? wrap(sourceName, location, supplier) : supplier);
    }

    /**
     * 包装语言文件的 {@link IoSupplier}：读出全部内容、检查/修补后再交给调用方。
     */
    @Nullable
    public static IoSupplier<InputStream> wrap(String sourceName, Identifier location, @Nullable IoSupplier<InputStream> original) {
        if (original == null) {
            return null;
        }
        return () -> {
            byte[] bytes;
            try (InputStream in = original.get()) {
                bytes = in.readAllBytes();
            }
            Result result = sanitize(bytes);
            if (result.passThrough()) {
                return new ByteArrayInputStream(bytes);
            }
            // 说明：MC 的 log4j 输出格式不打印 Marker，所以把标签直接写进消息文本，方便在 latest.log 里搜索
            GunMod.LOGGER.warn(MARKER,
                    "[GunPackLang] Language file {} of gun pack {} is not a valid translation file ({}). "
                            + "Serving a repaired copy instead: kept {} entr{}, dropped {}. "
                            + "Without this repair Minecraft 26.3 would abort loading of ALL translations "
                            + "(the game silently falls back to English and shows raw keys such as item.tacz.*). "
                            + "Please report the broken file to the gun pack author.",
                    location, sourceName, result.problem(),
                    result.kept(), result.kept() == 1 ? "y" : "ies", result.dropped());
            return new ByteArrayInputStream(result.json().getBytes(StandardCharsets.UTF_8));
        };
    }

    /**
     * 检查并（必要时）修补一个语言文件。
     *
     * @return {@link Result#passThrough()} 为 true 时表示原内容可以原样使用。
     */
    public static Result sanitize(byte[] bytes) {
        String text = decode(bytes);
        Map<String, String> entries = new LinkedHashMap<>();
        int dropped = 0;
        String problem = null;

        boolean parsed = false;
        try (JsonReader reader = new JsonReader(new StringReader(text))) {
            // 与 vanilla Language.loadFromJson 使用的 new Gson().fromJson(Reader, Class) 一致：宽松模式，允许注释。
            reader.setStrictness(Strictness.LENIENT);
            JsonElement root = JsonParser.parseReader(reader);
            if (reader.peek() != JsonToken.END_DOCUMENT) {
                // 对应 Gson#assertFullConsumption：根对象之后还有内容，vanilla 会抛 JsonSyntaxException
                throw new IllegalStateException("JSON document was not fully consumed");
            }
            if (!root.isJsonObject()) {
                throw new IllegalStateException("root element is " + typeName(root) + ", expected a JSON object");
            }
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (value.isJsonPrimitive()) {
                    entries.put(entry.getKey(), value.getAsString());
                } else {
                    dropped++;
                    if (problem == null) {
                        problem = "value of \"" + entry.getKey() + "\" is " + typeName(value) + ", expected a string";
                    }
                }
            }
            parsed = true;
        } catch (Exception | StackOverflowError e) {
            problem = compactMessage(e);
        }

        if (parsed && dropped == 0) {
            return Result.PASS_THROUGH;
        }

        if (!parsed) {
            Salvage salvaged = salvage(text);
            entries = salvaged.entries;
            dropped = salvaged.dropped;
        }

        JsonObject out = new JsonObject();
        entries.forEach(out::addProperty);
        return new Result(false, GSON.toJson(out), entries.size(), dropped, problem == null ? "unknown problem" : problem);
    }

    /**
     * 解码字节：去掉 UTF-8 BOM（Gson 的 JsonReader 自己也会跳过开头的 BOM，这里去掉是让容错扫描与之一致），
     * 按 UTF-8 解码，非法序列替换为 U+FFFD（与 vanilla 用 InputStreamReader 读取时的行为相同）。
     */
    private static String decode(byte[] bytes) {
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        return new String(bytes, offset, bytes.length - offset, StandardCharsets.UTF_8);
    }

    private static String typeName(JsonElement element) {
        if (element.isJsonNull()) return "null";
        if (element.isJsonArray()) return "an array";
        if (element.isJsonObject()) return "an object";
        return "a primitive";
    }

    private static String compactMessage(Throwable e) {
        // Gson 通常是 JsonSyntaxException 包着 MalformedJsonException，后者的信息才有行列号
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        String name = root.getClass().getSimpleName();
        if (message == null) {
            return name;
        }
        // MalformedJsonException 的信息末尾附带一行 "See https://github.com/google/gson/...", 只保留第一行
        int newline = message.indexOf('\n');
        if (newline >= 0) {
            message = message.substring(0, newline);
        }
        message = message.strip();
        return message.isEmpty() ? name : name + ": " + message;
    }

    // ------------------------------------------------------------------
    // 容错扫描：在无法解析的文本里尽量找回 "key": "value"
    // ------------------------------------------------------------------

    /**
     * 容错地从文本里收集顶层 {@code key: value} 对。
     * <ul>
     *   <li>不要求条目之间有逗号（少逗号是最常见的手写错误）；多余的逗号/分号被忽略；</li>
     *   <li>支持行注释（{@code //}、{@code #}）与块注释，单引号字符串，未加引号的键/值（与 Gson 宽松模式一致）；</li>
     *   <li>值是对象/数组/null 的条目整体丢弃（vanilla 也无法把它们当作翻译使用）；</li>
     *   <li>未闭合的字符串取到文件末尾为止。</li>
     * </ul>
     */
    static Salvage salvage(String text) {
        Salvage result = new Salvage();
        Lexer lexer = new Lexer(text);
        int depth = 0;
        String pendingKey = null;
        boolean expectColon = false;
        boolean expectValue = false;
        Token token;
        while ((token = lexer.next()) != null) {
            switch (token.type()) {
                case OPEN_OBJECT, OPEN_ARRAY -> {
                    if (depth == 0 && token.type() == TokenType.OPEN_OBJECT && !expectValue) {
                        depth = 1;
                    } else {
                        // 嵌套结构（或多余的括号）：整体跳过
                        lexer.skipStructure();
                        if (expectValue && pendingKey != null) {
                            result.dropped++;
                        }
                    }
                    pendingKey = null;
                    expectColon = false;
                    expectValue = false;
                }
                case CLOSE_OBJECT -> {
                    depth = 0;
                    pendingKey = null;
                    expectColon = false;
                    expectValue = false;
                }
                case CLOSE_ARRAY, SEPARATOR -> {
                    pendingKey = null;
                    expectColon = false;
                    expectValue = false;
                }
                case COLON -> {
                    if (pendingKey != null && expectColon) {
                        expectColon = false;
                        expectValue = true;
                    }
                }
                case STRING, LITERAL -> {
                    if (expectValue && pendingKey != null) {
                        if (token.type() == TokenType.LITERAL && "null".equals(token.text())) {
                            result.dropped++;
                        } else {
                            result.entries.put(pendingKey, token.text());
                        }
                        pendingKey = null;
                        expectValue = false;
                    } else {
                        // 新的键；如果上一个键没等到冒号，直接被替换掉
                        pendingKey = token.text();
                        expectColon = true;
                        expectValue = false;
                    }
                }
            }
        }
        return result;
    }

    static final class Salvage {
        final Map<String, String> entries = new LinkedHashMap<>();
        int dropped;
    }

    enum TokenType {
        OPEN_OBJECT, CLOSE_OBJECT, OPEN_ARRAY, CLOSE_ARRAY, COLON, SEPARATOR, STRING, LITERAL
    }

    record Token(TokenType type, String text) {
    }

    /**
     * 极简 JSON 词法扫描器：只切分记号，不校验语法。
     */
    static final class Lexer {
        private final String text;
        private final int length;
        private int pos;

        Lexer(String text) {
            this.text = text;
            this.length = text.length();
        }

        @Nullable
        Token next() {
            while (true) {
                skipWhitespaceAndComments();
                if (pos >= length) {
                    return null;
                }
                char c = text.charAt(pos);
                switch (c) {
                    case '{' -> {
                        pos++;
                        return new Token(TokenType.OPEN_OBJECT, "{");
                    }
                    case '}' -> {
                        pos++;
                        return new Token(TokenType.CLOSE_OBJECT, "}");
                    }
                    case '[' -> {
                        pos++;
                        return new Token(TokenType.OPEN_ARRAY, "[");
                    }
                    case ']' -> {
                        pos++;
                        return new Token(TokenType.CLOSE_ARRAY, "]");
                    }
                    case ':' -> {
                        pos++;
                        return new Token(TokenType.COLON, ":");
                    }
                    case '=' -> {
                        // Gson 宽松模式接受 "=" 与 "=>" 作为键值分隔符
                        pos++;
                        if (pos < length && text.charAt(pos) == '>') {
                            pos++;
                        }
                        return new Token(TokenType.COLON, "=");
                    }
                    case ',', ';' -> {
                        pos++;
                        return new Token(TokenType.SEPARATOR, String.valueOf(c));
                    }
                    case '"', '\'' -> {
                        return readString(c);
                    }
                    default -> {
                        Token literal = readLiteral();
                        if (literal != null) {
                            return literal;
                        }
                        // 无法归类的单个字符：跳过，继续
                        pos++;
                    }
                }
            }
        }

        /**
         * 已经读到一个 {@code {} 或 {@code [}，跳到与之匹配的闭合括号之后。
         */
        void skipStructure() {
            int nesting = 1;
            Token token;
            while (nesting > 0 && (token = next()) != null) {
                switch (token.type()) {
                    case OPEN_OBJECT, OPEN_ARRAY -> nesting++;
                    case CLOSE_OBJECT, CLOSE_ARRAY -> nesting--;
                    default -> {
                    }
                }
            }
        }

        private void skipWhitespaceAndComments() {
            while (pos < length) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f' || c == '\uFEFF') {
                    pos++;
                } else if (c == '#') {
                    skipLine();
                } else if (c == '/' && pos + 1 < length && text.charAt(pos + 1) == '/') {
                    skipLine();
                } else if (c == '/' && pos + 1 < length && text.charAt(pos + 1) == '*') {
                    int end = text.indexOf("*/", pos + 2);
                    pos = end < 0 ? length : end + 2;
                } else {
                    return;
                }
            }
        }

        private void skipLine() {
            while (pos < length) {
                char c = text.charAt(pos++);
                if (c == '\n' || c == '\r') {
                    return;
                }
            }
        }

        private Token readString(char quote) {
            pos++; // 开引号
            StringBuilder sb = new StringBuilder();
            while (pos < length) {
                char c = text.charAt(pos++);
                if (c == quote) {
                    return new Token(TokenType.STRING, sb.toString());
                }
                if (c != '\\') {
                    sb.append(c);
                    continue;
                }
                if (pos >= length) {
                    break;
                }
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'u' -> {
                        if (pos + 4 <= length) {
                            try {
                                sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                                pos += 4;
                            } catch (NumberFormatException e) {
                                sb.append('u');
                            }
                        } else {
                            sb.append('u');
                        }
                    }
                    // \" \\ \/ \' 以及未知转义：保留字符本身
                    default -> sb.append(escaped);
                }
            }
            // 未闭合的字符串：能拿多少算多少
            return new Token(TokenType.STRING, sb.toString());
        }

        @Nullable
        private Token readLiteral() {
            int start = pos;
            while (pos < length) {
                char c = text.charAt(pos);
                if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',' || c == ';' || c == '='
                        || c == '"' || c == '\'' || c == '#' || Character.isWhitespace(c) || c == '\uFEFF') {
                    break;
                }
                if (c == '/' && pos + 1 < length && (text.charAt(pos + 1) == '/' || text.charAt(pos + 1) == '*')) {
                    break;
                }
                pos++;
            }
            if (pos == start) {
                return null;
            }
            return new Token(TokenType.LITERAL, text.substring(start, pos));
        }
    }

    /**
     * {@link #sanitize} 的结果。
     *
     * @param passThrough 为 true 时原内容可原样使用，其余字段无意义
     * @param json        修补后的 JSON 文本
     * @param kept        保留下来的条目数
     * @param dropped     被丢弃的条目数（值不是字符串/基元的条目）
     * @param problem     原始问题的简短描述
     */
    public record Result(boolean passThrough, String json, int kept, int dropped, String problem) {
        static final Result PASS_THROUGH = new Result(true, "", 0, 0, "");
    }
}
