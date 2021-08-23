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
