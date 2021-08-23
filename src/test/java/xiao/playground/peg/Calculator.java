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
