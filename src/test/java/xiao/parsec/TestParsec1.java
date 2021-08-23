package xiao.parsec;

import java.lang.reflect.Method;

import static xiao.parsec.Parsec1.*;
import static xiao.parsec.Parsec1.Rules.*;

@SuppressWarnings("unused")
public interface TestParsec1 {

    Cont onFail = (s, r) -> { throw ((ParseException) r); };

    interface Chainer {
        Rule chain(Rule rule, Rule op, Fun3 mapper);
    }

    static Rule expr(Chainer chainer) {
        Rule self = (s, m, f) -> expr(chainer).match(s, m, f);
        return chainer.chain(
                Choose(
                        Between(
                                Pat("\\("),
                                Pat("\\)"),
                                self
                        ),
                        Pat("\\d+", Integer::parseInt)
                ),
                Pat("\\s*\\+\\s*", String::trim),
                Triple::new
        );
    }
    // expr -> expr + factor
    // factor -> (expr) | \d
    Rule EXPR_L = expr(Rules::Chainl1).over(EOF());

    // expr -> factor + expr
    // factor -> (expr) | \d
    Rule EXPR_R = expr(Rules::Chainr1).over(EOF());

    static void match(Rule rule, String e) {
        rule.match(e, (s, r) -> System.out.println(e + "\t=>\t" + r), onFail);
    }
    static void expr_test(Rule rule) {
        match(rule, "1 + 2 + 3");
        match(rule, "(1 + 2) + 3");
        match(rule, "1 + (2 + 3)");
        match(rule, "1 + (2 + (3 + 4))");
    }

    static void test_left_right_Recursive() {
        expr_test(EXPR_L);
        System.out.println();
        expr_test(EXPR_R);
    }

    static void main(String[] args) throws Exception {
        for (Method it : TestParsec1.class.getDeclaredMethods()) {
            if (it.getName().startsWith("test_")) {
                it.invoke(null);
            }
        }
    }
}
