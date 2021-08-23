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