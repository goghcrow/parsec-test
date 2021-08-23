package xiao.parsec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static xiao.parsec.Parsec2.CharParsers.Pat;
import static xiao.parsec.Parsec2.Combinators.*;
import static xiao.parsec.Parsec2.Pair;
import static xiao.parsec.Utils.unEscape;

/**
 *   /
 *   (?(DEFINE)
 *      (?<number>   -? (?= [1-9]|0(?!\d) ) \d+ (\.\d+)? ([eE] [+-]? \d+)? )
 *      (?<boolean>   true | false | null )
 *      (?<string>    " ([^"\\\\]* | \\\\ ["\\\\bfnrt\/] | \\\\ u [0-9a-f]{4} )* " )
 *      (?<array>     \[  (?:  (?&json)  (?: , (?&json)  )*  )?  \s* \] )
 *      (?<pair>      \s* (?&string) \s* : (?&json)  )
 *      (?<object>    \{  (?:  (?&pair)  (?: , (?&pair)  )*  )?  \s* \} )
 *      (?<json>   \s* (?: (?&number) | (?&boolean) | (?&string) | (?&array) | (?&object) ) \s* )
 *   )
 *   \A (?&json) \Z
 *   /
 */
public interface JSON2 {
    // Parsec2<Character, Character> WS = SkipMany(Whitespace);
    Parsec2<String, Character> WS = Pat("\\s*");
    Parsec2<String, Character> jNull = Pat("null");
    Parsec2<Boolean, Character> jBool = Pat("true|false", "true"::equals);
    Parsec2<Number, Character> jNum = Pat("-?(?=[1-9]|0(?!\\d))\\d+(\\.\\d+)?([eE][+-]?\\d+)?", s -> {
        if (s.contains(".") || s.contains("e") || s.contains("E")) {
            return Double.parseDouble(s);
        } else {
            return Long.parseLong(s);
        }
    });
    Parsec2<String, Character> jStr = Pat("\"([^\"\\\\]*|\\\\[\"\\\\bfnrt\\/]|\\\\u[0-9a-f]{4})*\"", s -> {
        return unEscape(s.substring(1, s.length() - 1), '"');
    });
    // \s 处理空数组
    Parsec2<List<Object>, Character> jArr = Between(
            Pat("\\["),
            Pat("\\s*\\]"),// \s 处理空数组
            SepBy(json(), Pat(","))
    );
    Parsec2<Pair<String, Object>, Character> jPair = Seq(
            Between(WS, WS, jStr),
            Pat(":"),
            json(),
            (k, colon, v) -> new Pair<>(k, v)
    );
    // \s 处理空对象
    Parsec2<Map<String, Object>, Character> jObj = Between(
            Pat("\\{"),
            Pat("\\s*\\}"), // \s 处理空对象
            SepBy(jPair, Pat(",")).map(lst -> {
                Map<String, Object> map = new HashMap<>();
                for (Pair<String, Object> pair : lst) {
                    map.put(pair.car, pair.cdr);
                }
                return map;
            })
    );


    static Parsec2<Object, Character> json() {
        // json 做成方法是因为属性循环引用
        // 这里做 thunk 是因为 jArr 调用 json 时候, jObj 还是 null
        return s -> Between(WS, WS, Choose(
                jNull,
                jNum,
                jBool,
                jStr,
                jArr,
                jObj
        )).parse(s);
    }

    Parsec2<Optional<Object>, Character> JSONParser = Optional(json()).over(EOF());

    static Object Parse(String str) {
        return JSONParser.parse(str).get();
    }

    // ================================================================================================

    static void main(String[] args) {
        int i = 0;
        try { Parse("{\"a\":}"); i++; } catch (RuntimeException ignored) {}
        try { Parse("[12,,1]"); i++; } catch (RuntimeException ignored) {}
        try { Parse("[12,]"); i++; } catch (RuntimeException ignored) {}
        if (i > 0) throw new RuntimeException();


        System.out.println(Parse("null"));
        System.out.println(Parse("true"));
        System.out.println(Parse("false"));
        System.out.println(Parse("-12345.123E23"));
        System.out.println(Parse("\"123Hello\\t🍺\""));
        System.out.println(Parse("[1, [], [1], [1, 2], [1, 2, 3]]"));
        System.out.println(Parse("{}"));
        System.out.println(Parse("{\"k\":1}"));
        System.out.println(Parse("{\"k1\":1, \"k2\":2}"));
        System.out.println(Parse("{\"k1\":1, \"k2\":2, \"k3\":3}"));
        System.out.println(Parse("123.456e-789"));
        // System.out.println(Parse("  "));
        System.out.println(Parse(Utils.resource("/small.json")));
        System.out.println(Parse(Utils.resource("/large.json")));
    }
}
