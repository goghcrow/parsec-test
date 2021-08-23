# Parsec 测试

写了几个版本的 parsec 对比

## 版本说明

- PEG 最早的 demo
- Parsec : 只能处理 String, CPS风格, 无状态, 弱类型, 结果用 Result 表达
- Parsec1: 只能处理 String, CPS风格, 无状态, 弱类型, 结果用 Object 表达
- Parsec2: 通用版本, 强类型, 能处理 Sequence<E>, 有状态, 用异常处理回溯, 有状态
- Parsec3: Parsec2 Sequence<E> 特化成 String 的版本
- Parsec4: Parsec2 去掉 Sequence 状态的版本

## 结论

- 有状态的代码心智负担重一些
- 弱类型表达能力没变, 编写代码方便, 虽然通用但是编写组合子的类型负担重一些
- CPS 风格解析大的 json 需要 -Xss?M 把栈空间开大, 因为状态都保存在栈里头
- CPS 风格的程序性能会好上一倍, 未优化处理 json 但仍旧比 fastjson 慢 100 倍
- 综上：Parsec1 与 Parsec2 版本有用, 其他版本没什么用, 推荐 Parsec1 版本可用来处理一些 DSL

## DEMO


```java
package xiao.playground.peg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static xiao.playground.peg.Parsec1.Pair;
import static xiao.playground.peg.Parsec1.Rule;
import static xiao.playground.peg.Parsec1.Rules.*;
import static xiao.playground.peg.Utils.unEscape;

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
```


```java
package xiao.playground.peg;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static xiao.playground.peg.Parsec2.CharParsers.Pat;
import static xiao.playground.peg.Parsec2.Combinators.*;
import static xiao.playground.peg.Parsec2.Pair;
import static xiao.playground.peg.Utils.unEscape;

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
        System.out.println(Parse(Utils.resource("/small.json")));
        System.out.println(Parse(Utils.resource("/large.json")));
    }
}
```


```java
package xiao.playground.peg;

import java.util.function.BiFunction;

import static xiao.playground.peg.Parsec.*;
import static xiao.playground.peg.Parsec.Rules.*;


/**
 * expr    = term   `chainl1` addop
 * term    = factor `chainl1` mulop
 * factor  = parens expr <|> integer
 * mulop   =   do{ symbol "*"; return (*) } <|> do{ symbol "/"; return (div) }
 * addop   =   do{ symbol "+"; return (+) } <|> do{ symbol "-"; return (-)   }
 */
public interface Calculator {

    class IntRet implements Result {
        final int i;
        public IntRet(String s) { this.i = Integer.parseInt(s); }
    }

    class OPRet implements Result {
        final BiFunction<Integer, Integer, Integer> op;
        public OPRet(BiFunction<Integer, Integer, Integer> op) {
            this.op = op;
        }
    }

    Rule integer = Pat("-?(?=[1-9]|0(?!\\d))\\d+", IntRet::new);
    static Rule parens(Rule rule) {
        return Between(Pat("\\s*\\(\\s*"), Pat("\\s*\\)\\s*"), rule);
    }
    static Rule expr() {
        return Chainl1(term(), addop());
    }
    static Rule term() {
        return Chainl1(factor(), mulop());
    }
    static Rule factor() {
        Rule exprThunk = (s, m, f) -> expr().match(s, m, f);
        return Choose(parens(exprThunk), integer);
    }
    static Rule mulop() {
        return Choose(
                Pat("\\s*\\*\\s*", s -> new OPRet((x, y) -> x * y)),
                Pat("\\s*/\\s*", s -> new OPRet((x, y) -> x / y))
        );
    }
    static Rule addop() {
        return Choose(
                Pat("\\s\\+\\s", s -> new OPRet((x, y) -> x + y)),
                Pat("\\s-\\s", s -> new OPRet((x, y) -> x - y))
        );
    }

    Rule calculate = expr().over(EOF());

    static int eval(Result r) {
        if (r instanceof IntRet) {
            return ((IntRet) r).i;
        } else if (r instanceof Triple) {
            Triple t = (Triple) r;
            return ((OPRet) t.fst).op.apply(eval(t.sec), eval(t.trd));
        } else {
            throw new IllegalStateException();
        }
    }

    static int calculate(String expr) {
        int[] ref = new int[1];
        calculate.match(expr, (s, r) -> {
            ref[0] = eval(r);
        }, (s, r) -> {throw new RuntimeException(r + "");});
        return ref[0];
    }

    static void main(String[] args) {
        System.out.println(calculate("4 * (1 + 2) / 6"));
    }
}
```

```java
package xiao.playground.peg;

import java.util.function.BiFunction;

import static xiao.playground.peg.Parsec1.Fun3;
import static xiao.playground.peg.Parsec1.Rule;
import static xiao.playground.peg.Parsec1.Rules.*;


/**
 * expr    = term   `chainl1` addop
 * term    = factor `chainl1` mulop
 * factor  = parens expr <|> integer
 * mulop   =   do{ symbol "*"; return (*) } <|> do{ symbol "/"; return (div) }
 * addop   =   do{ symbol "+"; return (+) } <|> do{ symbol "-"; return (-)   }
 */
public interface Calculator1 {

    interface Operator extends BiFunction<Integer, Integer, Integer> {}
    Fun3 op = (op, x, y) -> ((Operator) op).apply(((Integer) x), ((Integer) y));
    Rule integer = Pat("-?(?=[1-9]|0(?!\\d))\\d+", Integer::parseInt);

    static Rule parens(Rule rule) { return Between(Pat("\\s*\\(\\s*"), Pat("\\s*\\)\\s*"), rule); }
    static Rule expr() { return Chainl1(term(), addop(), op); }
    static Rule term() { return Chainl1(factor(), mulop(), op); }
    static Rule factor() { return Choose(parens((s, m, f) -> expr().match(s, m, f)), integer); }
    static Rule mulop() {
        return Choose(
                Pat("\\s*\\*\\s*", s -> (Operator)((x, y) -> x * y)),
                Pat("\\s*/\\s*", s -> (Operator)((x, y) -> x / y))
        );
    }
    static Rule addop() {
        return Choose(
                Pat("\\s\\+\\s", s -> (Operator)((x, y) -> x + y)),
                Pat("\\s-\\s", s -> (Operator)((x, y) -> x - y))
        );
    }

    Rule calculate = expr().over(EOF());

    static int calculate(String expr) {
        int[] ref = new int[1];
        calculate.match(expr, (s, r) -> {
            ref[0] = ((int) r);
        }, (s, r) -> {throw new RuntimeException(r + "");});
        return ref[0];
    }

    static void main(String[] args) {
        System.out.println(calculate("4 * (1 + 2) / 6"));
    }
}
```

```java
package xiao.playground.peg;

import xiao.playground.peg.Parsec.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static xiao.playground.peg.Parsec.*;
import static xiao.playground.peg.Parsec.Rules.*;

/**
 * @author chuxiaofeng <br>
 *  <br>
 * dquota-string: "(\\["\\trnbf\/]|""|[^"\\])*" <br>
 * squota-string: '(\\['\\trnbf\/]|''|[^'\\])*' <br>
 * string-literal: <dquota-string> | <squota-string> <br>
 * int-literal: -?(0|[1-9][0-9]*)(?![.Ee]) <br>
 * double-literal: -?(0|[1-9][0-9]*)([.][0-9]+)([eE][-+]?[0-9]+)? <br>
 * constant: NULL | <int-literal> | <double-literal> | string-literal <br>
 * array-constant: constant | (<constant> [, <constant>]* ) <br>
 * comparable-operator: <= | >= | <> | != | = | < | > <br>
 * binary-operator: IS[ NOT] | [NOT] IN | [NOT] LIKE <br>
 * operator: <comparable-operator> | <binary-operator> <br>
 * id-literal: [a-zA-Z_]+?[a-zA-Z_0-9]* <br>
 * binary-expr: <id-literal> <operator> <array-constant> <br>
 * not-between-expr: <id-literal> [NOT ]BETWEEN <constant> AND <constant> <br>
 * not-null-expr: <id-literal> IS [NOT ]NULL <br>
 * factor: <binary-expr> | <not-between-expr> | not-null-expr | <logical-expr> <br>
 * not-factor: [NOT] <factor> <br>
 * term: <not-factor> [AND <not-factor>]* <br>
 * logical-expr: <term> [OR <term>]* <br>
 */
public interface Criteria {

    Rule WS = Whitespace();
    Rule Not = Pat("((?i)(NOT))", StrRet::new).over(WS).map(Criteria::normalize);
    Rule And = Pat("((?i)(AND))", StrRet::new).over(WS).map(Criteria::normalize);
    Rule Or = Pat("((?i)(OR))", StrRet::new).over(WS).map(Criteria::normalize);

    Rule NotBetween = Pat("((?i)(?:NOT\\s+)?BETWEEN)", StrRet::new).over(WS).map(Criteria::normalize);
    Rule IsNotNull = Pat("((?i)IS\\s+(?:NOT\\s+)?NULL)", StrRet::new).over(WS).map(Criteria::normalize);
    Rule IsNotTrue = Pat("((?i)IS\\s+(?:NOT\\s+)?TRUE)", StrRet::new).over(WS).map(Criteria::normalize);

    Rule ParLeft = Pat("\\(\\s*");
    Rule ParRight = Pat("\\)\\s*");
    Rule Comma = Pat(",\\s*");

    Rule DoubleQuotaString = Pat("\"((?:\\\\[\"\\\\trnbf\\/]|\"\"|[^\"\\\\])*)\"", StrRet::new).map(r -> unEscapeStrRet(r, '"'));
    Rule SingleQuotaString = Pat("'((?:\\\\['\\\\trnbf\\/]|''|[^'\\\\])*)'", StrRet::new).map(r -> unEscapeStrRet(r, '\''));

    Rule Null = Pat("(?i)NULL").map(r -> new NullRet());

    // (?![.Ee]) 用来区分 int 与 double，.|e 必然是 double
    Rule IntLiteral = Pat("-?(0|[1-9][0-9]*)(?![.Ee])", IntRet::new);
    Rule DoubleLiteral = Pat("-?(0|[1-9][0-9]*)([.][0-9]+)?([eE][-+]?[0-9]+)?", DoubleRet::new);
    Rule StrLiteral = DoubleQuotaString.or(SingleQuotaString);
    Rule Literal = Null.or(IntLiteral).or(DoubleLiteral).or(StrLiteral).over(WS);
    Rule ArrayLiteral = Literal.or(Between(ParLeft, ParRight, SepBy(Literal, Comma)));

    Rule ComparableOperator = Pat("<=|>=|<>|!=|=|<|>", StrRet::new); // 注意顺序
    String[] BiOps = new String[] {"IN", "LIKE", "MATCH", "CONTAINS", "DISJOINT", "INTERSECT"};
//    String BiOpsRegex = "(?i)" + Arrays.stream(BiOps).map(op -> "(NOT\\s+)?" + op).collect(joining("|")); // todo 编译器错误
    static String BiOpsRegex() {
        return "(?i)" + Arrays.stream(BiOps).map(op -> "(NOT\\s+)?" + op).collect(joining("|"));
    }
    Rule BinaryOperator = Pat(BiOpsRegex(), StrRet::new).map(Criteria::normalize);
    Rule Operator = ComparableOperator.or(BinaryOperator).over(WS);

    Rule IdLiteral = Pat("[a-zA-Z_]+?[a-zA-Z_0-9]*", StrRet::new).over(WS);
    Rule BinaryExpr = Seq(IdLiteral, Operator, ArrayLiteral, Triple::new);

    Rule BetweenExpr = Seq(IdLiteral, NotBetween, Seq(Literal, And, Literal, (l, and, r) -> new Pair(l, r)), Triple::new);
    Rule IsNotNullExpr = Seq(IdLiteral, IsNotNull, (id, op) -> new Triple(id, op, null));
    Rule IsNotTrueExpr = Seq(IdLiteral, IsNotTrue, (id, op) -> new Triple(id, op, null));

    static Rule Factor() {
        // thunk, ide瞎提示, 换成 lam ref 就跪了
        //noinspection Convert2MethodRef,FunctionalExpressionCanBeFolded
        Rule logicalExpr = (s, m, f) -> LogicalExpr.match(s, m, f);
        return Choose(
                BinaryExpr,
                BetweenExpr,
                IsNotNullExpr,
                Between(ParLeft, ParRight, logicalExpr),
                IsNotTrueExpr
        );
    }

    Rule NotFactor = Choose(Seq(Not, Factor(), (op, f) -> new Triple(op, f, null)), Factor());
    Rule Term = Chainl1(NotFactor, And);
    Rule LogicalExpr = Chainl1(Term, Or);

    Rule CriteriaGrammar = LogicalExpr.over(EOF());

    // ================================================================================================================

    static StrRet normalize(Result r) {
        return new StrRet(((StrRet) r).str.replaceAll("\\s+", "_").toUpperCase());
    }
    static StrRet unEscapeStrRet(Result r, char quote) {
        String s = ((StrRet) r).str;
        return new StrRet(Utils.unEscape(s.substring(1, s.length() - 1), quote));
    }


    class NullRet implements Result {
        @Override public String toString() { return "NULL"; }
        @Override public int hashCode() { return 0; }
        @Override public boolean equals(Object obj) { return obj instanceof NullRet; }
    }
    class IntRet implements Result {
        final long v;
        public IntRet(String s) { this.v = Long.parseLong(s); }
        @Override public String toString() { return String.valueOf(v); }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o != null && getClass() == o.getClass() && v == ((IntRet) o).v;
        }
        @Override public int hashCode() { return Objects.hash(v); }
    }
    class DoubleRet implements Result {
        final double v;
        public DoubleRet(String s) { this.v = Double.parseDouble(s); }
        @Override public String toString() { return String.valueOf(v); }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            return o != null && getClass() == o.getClass() && Double.compare(((DoubleRet) o).v, v) == 0;
        }
        @Override public int hashCode() { return Objects.hash(v); }
    }

    // ================================================================================================================


    static double numVal(Result r) {
        if (r instanceof IntRet) {
            return ((IntRet) r).v;
        } else if (r instanceof DoubleRet) {
            return ((DoubleRet) r).v;
        } else {
            throw new IllegalStateException();
        }
    }
    static int numCmp(Object lval, Result rval) {
        assert lval instanceof Number;
        return Double.compare(((Number) lval).doubleValue(), numVal(rval));
    }
    static boolean strCmp(Object lval, Result rval) {
        assert lval instanceof String;
        assert rval instanceof StrRet;
        return Objects.equals(lval, ((StrRet) rval).str);
    }
    static boolean cmp(Object lval, Result rval) {
        if (lval instanceof Number) {
            return numCmp(lval, rval) == 0;
        } else if (lval instanceof String) {
            return strCmp(lval, rval);
        } else {
            throw new IllegalStateException();
        }
    }

    static boolean eval(Result ast, Map<String, Object> binding) {
        if (ast instanceof Triple && ((Triple) ast).sec instanceof Triple) {
            Triple logicalExpr = (Triple) ast;
            String logicalOp = ((StrRet) logicalExpr.fst).str;
            switch (logicalOp) {
                case "AND":
                    if (eval(logicalExpr.sec, binding)) {
                        return eval(logicalExpr.trd, binding);
                    } else {
                        return false;
                    }
                case "OR":
                    if (eval(logicalExpr.sec, binding)) {
                        return true;
                    } else {
                        return eval(logicalExpr.trd, binding);
                    }
                case "NOT":
                    return !eval(logicalExpr.sec, binding);
                default:
                    throw new UnsupportedOperationException(logicalOp);
            }
        } else if (ast instanceof Triple) {
            Triple binExpr = (Triple) ast;
            String id = ((StrRet) binExpr.fst).str;
            String op = ((StrRet) binExpr.sec).str;
            Result rval = binExpr.trd;
            if (!binding.containsKey(id)) {
                throw new RuntimeException(id + " not found");
            }
            Object lval = binding.get(id);

            switch (op) {

                case "BETWEEN": return numCmp(lval, ((Pair) rval).car) >= 0 && numCmp(lval, ((Pair) rval).cdr) <= 0;
                case "NOT_BETWEEN": return !(numCmp(lval, ((Pair) rval).car) >= 0 && numCmp(lval, ((Pair) rval).cdr) <= 0);
                case "IS_TRUE": assert lval instanceof Boolean; return ((Boolean) lval);
                case "IS_NOT_TRUE": assert lval instanceof Boolean; return !((Boolean) lval);
                case "IS_NULL": return lval == null;
                case "IS_NOT_NULL": return lval != null;
                case "<": return numCmp(lval, rval) < 0;
                case ">": return numCmp(lval, rval) > 0;
                case "<=": return numCmp(lval, rval) <= 0;
                case ">=": return numCmp(lval, rval) >= 0;
                case "=": return cmp(lval, rval);
                case "!=": return !cmp(lval, rval);
                case "<>": return !cmp(lval, rval);
                case "IN":
                    assert rval instanceof ListRet;
                    for (Result r : ((ListRet) rval).lst) {
                        if (cmp(lval, r)) return true;
                    }
                    return false;
                case "LIKE":
                    assert lval instanceof String;
                    assert rval instanceof StrRet;
                    return Pattern.compile(((StrRet) rval).str).matcher(((String) lval)).matches();
                case "MATCH":
                case "CONTAINS":
                case "DISJOINT":
                case "INTERSECT":
                default:
                    throw new UnsupportedOperationException(op);
            }
        } else {
            throw new IllegalStateException();
        }
    }

    // ================================================================================================================

    static Result Parse(String expr) {
        Result[] ref = new Result[1];
        CriteriaGrammar.match(expr, (s, r) -> {
            ref[0] = r;
        }, (s, r) -> {throw new RuntimeException(r + "");});
        return ref[0];
    }

    static boolean Eval(Result criteria, Map<String, Object> bindings) {
        return eval(criteria, bindings);
    }

    static Map<String, Object> bindings(Object... args) {
        assert args.length >= 2;
        assert args.length % 2 == 0;
        Map<String, Object> bindings = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            bindings.put(((String) args[i]), args[i + 1]);
        }
        return bindings;
    }

    static void main(String[] args) {
        System.out.println(Parse(
                "(a is true or b is not null) " +
                "and " +
                "x in (42, 3.14, null, 'str')" +
                "or " +
                "i >= 42 or a != 'Hello'" +
                "and " +
                "i between 10 and 20"));
        System.out.println(Parse("id > 1 or id < 2 and id = 3 or id > 5"));
        System.out.println(Parse("a > 1 or (b < 2 and not c >= 3) and not d < 4"));
        System.out.println(Parse("id not in(1,2)"));
        System.out.println(Parse("(((  (   (((id = 2))) and b < 1   ) or (c in (1)) and name = 'xiaofeng')))"));


        Result criteria = Parse(
                "boolVal is true" +
                        " and " +
                        "nullVal is not null" +
                        " and " +
                        "strVal in ('A', 'B', 'C')" +
                        " and " +
                        "intVal between 1 and 100" +
                        " and " +
                        "id >= 42");

        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 42,
                "id", 99
                )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", false,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 42,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", null,
                "strVal", "A",
                "intVal", 42,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "X",
                "intVal", 42,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 101,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 42,
                "id", 1
        )));
    }
}
```

```java
package xiao.playground.peg;

import xiao.playground.peg.Parsec1.Pair;
import xiao.playground.peg.Parsec1.Rule;
import xiao.playground.peg.Parsec1.Triple;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.joining;
import static xiao.playground.peg.Parsec1.Rules.*;

/**
 * @author chuxiaofeng <br>
 *  <br>
 * dquota-string: "(\\["\\trnbf\/]|""|[^"\\])*" <br>
 * squota-string: '(\\['\\trnbf\/]|''|[^'\\])*' <br>
 * string-literal: <dquota-string> | <squota-string> <br>
 * int-literal: -?(0|[1-9][0-9]*)(?![.Ee]) <br>
 * double-literal: -?(0|[1-9][0-9]*)([.][0-9]+)([eE][-+]?[0-9]+)? <br>
 * constant: NULL | <int-literal> | <double-literal> | string-literal <br>
 * array-constant: constant | (<constant> [, <constant>]* ) <br>
 * comparable-operator: <= | >= | <> | != | = | < | > <br>
 * binary-operator: IS[ NOT] | [NOT] IN | [NOT] LIKE <br>
 * operator: <comparable-operator> | <binary-operator> <br>
 * id-literal: [a-zA-Z_]+?[a-zA-Z_0-9]* <br>
 * binary-expr: <id-literal> <operator> <array-constant> <br>
 * not-between-expr: <id-literal> [NOT ]BETWEEN <constant> AND <constant> <br>
 * not-null-expr: <id-literal> IS [NOT ]NULL <br>
 * factor: <binary-expr> | <not-between-expr> | not-null-expr | <logical-expr> <br>
 * not-factor: [NOT] <factor> <br>
 * term: <not-factor> [AND <not-factor>]* <br>
 * logical-expr: <term> [OR <term>]* <br>
 */
public interface Criteria1 {
    Function<String, Object> id = s -> s;

    Rule WS = Whitespace();
    Rule Not = Pat("((?i)(NOT))", Criteria1::normalize).over(WS);
    Rule And = Pat("((?i)(AND))", Criteria1::normalize).over(WS);
    Rule Or = Pat("((?i)(OR))", Criteria1::normalize).over(WS);

    Rule NotBetween = Pat("((?i)(?:NOT\\s+)?BETWEEN)", Criteria1::normalize).over(WS);
    Rule IsNotNull = Pat("((?i)IS\\s+(?:NOT\\s+)?NULL)", Criteria1::normalize).over(WS);
    Rule IsNotTrue = Pat("((?i)IS\\s+(?:NOT\\s+)?TRUE)", Criteria1::normalize).over(WS);

    Rule ParLeft = Pat("\\(\\s*");
    Rule ParRight = Pat("\\)\\s*");
    Rule Comma = Pat(",\\s*");

    Rule DoubleQuotaString = Pat("\"((?:\\\\[\"\\\\trnbf\\/]|\"\"|[^\"\\\\])*)\"", s -> unEscapeStrRet(s, '"'));
    Rule SingleQuotaString = Pat("'((?:\\\\['\\\\trnbf\\/]|''|[^'\\\\])*)'", s -> unEscapeStrRet(s, '\''));

    Rule Null = Pat("(?i)NULL").map(r -> null);

    // (?![.Ee]) 用来区分 int 与 double，.|e 必然是 double
    Rule IntLiteral = Pat("-?(0|[1-9][0-9]*)(?![.Ee])", Long::parseLong);
    Rule DoubleLiteral = Pat("-?(0|[1-9][0-9]*)([.][0-9]+)?([eE][-+]?[0-9]+)?", Double::parseDouble);
    Rule StrLiteral = DoubleQuotaString.or(SingleQuotaString);
    Rule Literal = Null.or(IntLiteral).or(DoubleLiteral).or(StrLiteral).over(WS);
    Rule ArrayLiteral = Literal.or(Between(ParLeft, ParRight, SepBy(Literal, Comma)));

    Rule ComparableOperator = Pat("<=|>=|<>|!=|=|<|>", id); // 注意顺序
    String[] BiOps = new String[] {"IN", "LIKE", "MATCH", "CONTAINS", "DISJOINT", "INTERSECT"};
    static String BiOpsRegex() { return "(?i)" + Arrays.stream(BiOps).map(op -> "(NOT\\s+)?" + op).collect(joining("|")); }
    Rule BinaryOperator = Pat(BiOpsRegex(), Criteria1::normalize);
    Rule Operator = ComparableOperator.or(BinaryOperator).over(WS);

    Rule IdLiteral = Pat("[a-zA-Z_]+?[a-zA-Z_0-9]*", id).over(WS);
    Rule BinaryExpr = Seq(IdLiteral, Operator, ArrayLiteral, Triple::new);

    Rule BetweenExpr = Seq(IdLiteral, NotBetween, Seq(Literal, And, Literal, (l, and, r) -> new Pair(l, r)), Triple::new);
    Rule IsNotNullExpr = Seq(IdLiteral, IsNotNull, (id, op) -> new Triple(id, op, null));
    Rule IsNotTrueExpr = Seq(IdLiteral, IsNotTrue, (id, op) -> new Triple(id, op, null));

    static Rule Factor() {
        // thunk, ide瞎提示, 换成 lam ref 就跪了
        //noinspection Convert2MethodRef,FunctionalExpressionCanBeFolded
        Rule logicalExpr = (s, m, f) -> LogicalExpr.match(s, m, f);
        return Choose(
                BinaryExpr,
                BetweenExpr,
                IsNotNullExpr,
                Between(ParLeft, ParRight, logicalExpr),
                IsNotTrueExpr
        );
    }

    Rule NotFactor = Choose(Seq(Not, Factor(), (op, f) -> new Triple(op, f, null)), Factor());
    Rule Term = Chainl1(NotFactor, And, Triple::new);
    Rule LogicalExpr = Chainl1(Term, Or, Triple::new);

    Rule CriteriaGrammar = LogicalExpr.over(EOF());

    // ================================================================================================================

    static String normalize(String str) {
        return str.replaceAll("\\s+", "_").toUpperCase();
    }
    static String unEscapeStrRet(String s, char quote) {
        return Utils.unEscape(s.substring(1, s.length() - 1), quote);
    }

    // ================================================================================================================


    static double numVal(Object r) {
        if (r instanceof Long) {
            return ((Long) r).doubleValue();
        } else if (r instanceof Double) {
            return (Double) r;
        } else {
            throw new IllegalStateException();
        }
    }
    static int numCmp(Object lval, Object rval) {
        assert lval instanceof Number;
        return Double.compare(((Number) lval).doubleValue(), numVal(rval));
    }
    static boolean strCmp(Object lval, Object rval) {
        assert lval instanceof String;
        assert rval instanceof String;
        return Objects.equals(lval, rval);
    }
    static boolean cmp(Object lval, Object rval) {
        if (lval instanceof Number) {
            return numCmp(lval, rval) == 0;
        } else if (lval instanceof String) {
            return strCmp(lval, rval);
        } else {
            throw new IllegalStateException();
        }
    }

    static boolean eval(Object ast, Map<String, Object> binding) {
        if (ast instanceof Triple && ((Triple) ast).sec instanceof Triple) {
            Triple logicalExpr = (Triple) ast;
            String logicalOp = (String) logicalExpr.fst;
            switch (logicalOp) {
                case "AND":
                    if (eval(logicalExpr.sec, binding)) {
                        return eval(logicalExpr.trd, binding);
                    } else {
                        return false;
                    }
                case "OR":
                    if (eval(logicalExpr.sec, binding)) {
                        return true;
                    } else {
                        return eval(logicalExpr.trd, binding);
                    }
                case "NOT":
                    return !eval(logicalExpr.sec, binding);
                default:
                    throw new UnsupportedOperationException(logicalOp);
            }
        } else if (ast instanceof Triple) {
            Triple binExpr = (Triple) ast;
            String id = (String) binExpr.fst;
            String op = (String) binExpr.sec;
            Object rval = binExpr.trd;
            if (!binding.containsKey(id)) {
                throw new RuntimeException(id + " not found");
            }
            Object lval = binding.get(id);

            switch (op) {

                case "BETWEEN": return numCmp(lval, ((Pair) rval).car) >= 0 && numCmp(lval, ((Pair) rval).cdr) <= 0;
                case "NOT_BETWEEN": return !(numCmp(lval, ((Pair) rval).car) >= 0 && numCmp(lval, ((Pair) rval).cdr) <= 0);
                case "IS_TRUE": assert lval instanceof Boolean; return ((Boolean) lval);
                case "IS_NOT_TRUE": assert lval instanceof Boolean; return !((Boolean) lval);
                case "IS_NULL": return lval == null;
                case "IS_NOT_NULL": return lval != null;
                case "<": return numCmp(lval, rval) < 0;
                case ">": return numCmp(lval, rval) > 0;
                case "<=": return numCmp(lval, rval) <= 0;
                case ">=": return numCmp(lval, rval) >= 0;
                case "=": return cmp(lval, rval);
                case "!=": return !cmp(lval, rval);
                case "<>": return !cmp(lval, rval);
                case "IN":
                    assert rval instanceof List;
                    for (Object r : (List) rval) {
                        if (cmp(lval, r)) return true;
                    }
                    return false;
                case "LIKE":
                    assert lval instanceof String;
                    assert rval instanceof String;
                    return Pattern.compile(((String) rval)).matcher(((String) lval)).matches();
                case "MATCH":
                case "CONTAINS":
                case "DISJOINT":
                case "INTERSECT":
                default:
                    throw new UnsupportedOperationException(op);
            }
        } else {
            throw new IllegalStateException();
        }
    }

    // ================================================================================================================

    static Object Parse(String expr) {
        Object[] ref = new Object[1];
        CriteriaGrammar.match(expr, (s, r) -> {
            ref[0] = r;
        }, (s, r) -> {throw new RuntimeException(r + "");});
        return ref[0];
    }

    static boolean Eval(Object criteria, Map<String, Object> bindings) {
        return eval(criteria, bindings);
    }

    static Map<String, Object> bindings(Object... args) {
        assert args.length >= 2;
        assert args.length % 2 == 0;
        Map<String, Object> bindings = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            bindings.put(((String) args[i]), args[i + 1]);
        }
        return bindings;
    }

    static void main(String[] args) {
        System.out.println(Parse(
                "(a is true or b is not null) " +
                "and " +
                "x in (42, 3.14, null, 'str')" +
                "or " +
                "i >= 42 or a != 'Hello'" +
                "and " +
                "i between 10 and 20"));
        System.out.println(Parse("id > 1 or id < 2 and id = 3 or id > 5"));
        System.out.println(Parse("a > 1 or (b < 2 and not c >= 3) and not d < 4"));
        System.out.println(Parse("id not in(1,2)"));
        System.out.println(Parse("(((  (   (((id = 2))) and b < 1   ) or (c in (1)) and name = 'xiaofeng')))"));


        Object criteria = Parse(
                "boolVal is true" +
                        " and " +
                        "nullVal is not null" +
                        " and " +
                        "strVal in ('A', 'B', 'C')" +
                        " and " +
                        "intVal between 1 and 100" +
                        " and " +
                        "id >= 42");

        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 42,
                "id", 99
                )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", false,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 42,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", null,
                "strVal", "A",
                "intVal", 42,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "X",
                "intVal", 42,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 101,
                "id", 99
        )));
        System.out.println(Eval(criteria, bindings(
                "boolVal", true,
                "nullVal", "hello",
                "strVal", "A",
                "intVal", 42,
                "id", 1
        )));
    }

}
```