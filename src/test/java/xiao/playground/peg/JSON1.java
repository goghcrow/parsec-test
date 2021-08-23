package xiao.playground.peg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static xiao.playground.peg.Parsec1.Pair;
import static xiao.playground.peg.Parsec1.Rule;
import static xiao.playground.peg.Parsec1.Rules.*;
import static xiao.playground.peg.Utils.unEscape;

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
public interface JSON1 {
    Rule WS = Whitespace();
    Rule jNull = Pat("null", s -> null);
    Rule jBool = Pat("true|false", "true"::equals);
    Rule jNum = Pat("-?(?=[1-9]|0(?!\\d))\\d+(\\.\\d+)?([eE][+-]?\\d+)?", s -> {
        if (s.contains(".") || s.contains("e") || s.contains("E")) {
            return Double.parseDouble(s);
        } else {
            return Long.parseLong(s);
        }
    });
    Rule jStr = Pat("\"([^\"\\\\]*|\\\\[\"\\\\bfnrt\\/]|\\\\u[0-9a-f]{4})*\"", s -> {
        return unEscape(s.substring(1, s.length() - 1), '"');
    });

    Rule jArr = Between(
            Pat("\\["),
            Pat("\\s*\\]"),// \s 处理空数组
            SepBy(json(), Pat(","))
    );

    Rule jPair = Seq(
            Between(WS, WS, jStr),
            Pat(":"),
            json(),
            (k, colon, v) -> new Pair(k, v)
    );

    Rule jObj = Between(
            Pat("\\{"),
            Pat("\\s*\\}"), // \s 处理空对象
            SepBy(jPair, Pat(",")).map(lst -> {
                Map<String, Object> map = new HashMap<>();
                for (Pair pair : ((List<Pair>) lst)) {
                    map.put(((String) pair.car), pair.cdr);
                }
                return map;
            })
    );

    static Rule json() {
        // json 做成方法是因为属性循环引用
        // 这里做 thunk 是因为 jArr 调用 json 时候, jObj 还是 null
        return (s, m, f) -> Between(WS, WS, Choose(
                jNull,
                jNum,
                jBool,
                jStr,
                jArr,
                jObj
        )).match(s, m, f);
    }

    Rule JSONParser = Optional(json()).over(EOF());

    static Object Parse(String str) {
        Object[] ref = new Object[1];
        JSONParser.match(
                str.trim(),
                (s, r) -> ref[0] = r,
                (s, r) -> { throw new RuntimeException(r + ""); }
                );
        return ref[0];
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
        System.out.println(Parse("  "));
        System.out.println(Parse(Utils.resource("/small.json")));
        System.out.println(Parse(Utils.resource("/large.json"))); // -Xss15M
    }
}
