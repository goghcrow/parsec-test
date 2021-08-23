package xiao.parsec;

import xiao.parsec.Parsec.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.util.stream.Collectors.toList;
import static xiao.parsec.Parsec.Rules.*;

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
public interface JSON {
    Rule WS = Whitespace();
    Rule jNull = Pat("null", s -> new JNull());
    Rule jBool = Pat("true|false", JBool::new);
    Rule jNum = Pat("-?(?=[1-9]|0(?!\\d))\\d+(\\.\\d+)?([eE][+-]?\\d+)?", JNum::new);
    Rule jStr = Pat("\"([^\"\\\\]*|\\\\[\"\\\\bfnrt\\/]|\\\\u[0-9a-f]{4})*\"", JStr::new);

    Rule jArr = Between(
            Pat("\\["),
            Pat("\\s*\\]"),// \s 处理空数组
            SepBy(json(), Pat(","))
    ).map(JSON::lst2JArr);

    Rule jPair = Seq(
            Between(WS, WS, jStr),
            Pat(":"),
            json(),
            (k, colon, v) -> new Pair(k, v)
    );

    Rule jObj = Between(
            Pat("\\{"),
            Pat("\\s*\\}"), // \s 处理空对象
            SepBy(jPair, Pat(",")).map(JSON::lst2JObj)
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

    static JVal Parse(String str) {
        JVal[] ref = new JVal[1];
        JSONParser.match(
                str.trim(),
                (s, r) -> ref[0] = ((JVal) r),
                (s, r) -> { throw new RuntimeException(r + ""); }
                );
        return ref[0];
    }

    static Result lst2JArr(Result it) {
        return new JArr(((ListRet) it).lst.stream().map(it1 -> ((JVal) it1)).collect(toList()));
    }

    static Result lst2JObj(Result it) {
        Map<JStr, JVal> map = new HashMap<>();
        for (Result pair : ((ListRet) it).lst) {
            map.put(((JStr) ((Pair) pair).car), ((JVal) ((Pair) pair).cdr));
        }
        return new JObj(map);
    }

    // ================================================================================================

    interface JVal extends Result { }
    class JNum implements JVal {
        final Number val;
        public JNum(String val) {
            if (val.contains(".") || val.contains("e") || val.contains("E")) {
                this.val = Double.parseDouble(val);
            } else {
                this.val = Long.parseLong(val);
            }
        }
        @Override public String toString() { return String.valueOf(val); }
    }
    class JBool implements JVal {
        final boolean val;
        public JBool(String val) { this.val = "true".equals(val); }
        @Override public String toString() { return String.valueOf(val); }
    }
    class JStr implements JVal {
        final String val;
        public JStr(String val) {
            this.val = Utils.unEscape(val.substring(1, val.length() - 1), '"');
        }
        @Override public String toString() { return val; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o != null && getClass() == o.getClass() && val.equals(((JStr) o).val);
        }
        @Override public int hashCode() { return Objects.hash(val); }
    }
    class JNull implements JVal {
        @Override public String toString() { return "null"; }
    }
    class JArr implements JVal {
        final List<JVal> val;
        public JArr(List<JVal> array) { this.val = array; }
        @Override public String toString() { return String.valueOf(val); }
    }
    class JObj implements JVal {
        final Map<JStr, JVal> val;
        public JObj(Map<JStr, JVal> val) { this.val = val; }
        @Override public String toString() { return String.valueOf(val); }
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
