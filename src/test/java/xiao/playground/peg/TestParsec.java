package xiao.playground.peg;

import xiao.playground.peg.Parsec.*;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static xiao.playground.peg.Parsec.*;
import static xiao.playground.peg.Parsec.Rules.*;

@SuppressWarnings("unused")
public class TestParsec {

    static boolean strEq(String s, Result r) {
        return new StrRet(s).equals(r);
    }
    static boolean lstSz(int sz, Result r) {
        return ((ListRet) r).lst.size() == sz;
    }

    static Cont kStr(String state, String rStr) {
        return (s, r) -> {
            assert Objects.equals(s, state);
            assert strEq(rStr, r);
        };
    }
    static Cont kLst(String state, int sz) {
        return (s, r) -> {
            assert Objects.equals(s, state);
            assert lstSz(sz, r);
        };
    }
    static Cont kNull(String state) {
        return (s, r) -> {
            assert Objects.equals(s, state);
            assert r == null;
        };
    }

    static void fail(Runnable r) {
        try {
            r.run();
            throw new Error();
        } catch (RuntimeException ignored) { }
    }

    static Cont onFail = (s, r) -> {
        FailRet fr = (FailRet) r;
        throw new RuntimeException("expected: " + fr.expected + ", state: " + fr.state);
    };

    static void test_choose() {
        // Choose().match("", kNull(""), onFail);
        Rule aOrb = Choose(Pat("a", StrRet::new), Pat("b", StrRet::new));
        aOrb.match("c", (s, r) -> {
            throw new RuntimeException();
        }, (s, r) -> {
            assert "c".equals(s);
            List<String> expected = ((FailRet) r).expected;
            assert expected.size() == 2 && expected.get(0).equals("a") && expected.get(1).equals("b");
        });
    }

    static void test_over() {
        Pat("a", StrRet::new).over(Whitespace()).match("a   ", kStr("", "a"), onFail);
    }

    static void test_then() {
        Whitespace().then(Pat("a", StrRet::new)).match("    a", kStr("", "a"), onFail);
    }

    static void test_between() {
        Rule bw = Between(Pat("\\s*\\(\\s*"), Pat("\\s*\\)\\s*"), Pat("a", StrRet::new));
        bw.match("  ( a )", kStr("", "a"), onFail);
    }

    static void test_optional() {
        Rule ab = Option(Pat("a", StrRet::new), new StrRet("b"));
        ab.match("a", kStr("", "a"), onFail);
        ab.match("", kStr("", "b"), onFail);

        Rule zeroOrOneA = Optional(Pat("a", StrRet::new));
        zeroOrOneA.match("", kNull(""), onFail);
        zeroOrOneA.match("a", kStr("", "a"), onFail);
    }

    static void test_lookAhead() {
        // 没有消耗 state
        LookAhead(Pat("a", StrRet::new)).match("ab", kStr("ab", "a"), onFail);
    }

    static void test_skip() {
        Skip(Whitespace()).match("   a", kNull("a"), onFail);

        Rule skipBThenAEOF = Skip(Many(Pat("b"))).then(Pat("a", StrRet::new)).over(EOF());
        skipBThenAEOF.match("bbbba", kStr("", "a"), onFail);
        skipBThenAEOF.match("a", kStr("", "a"), onFail);
    }

    static void test_EOF() {
        EOF().match("", kNull(""), onFail);

        fail(() -> EOF().match("a", (s, r) -> { }, onFail));
    }

    static void test_many() {
        Many(Pat("a", StrRet::new)).match("", kLst("", 0), onFail);
        Many(Pat("a", StrRet::new)).match("a", kLst("", 1), onFail);
        Many(Pat("a", StrRet::new)).match("aaaaa", kLst("", 5), onFail);
    }

    static void test_many1() {
        fail(() -> Many1(Pat("a", StrRet::new)).match("", (s, r) -> { }, onFail));
        Many1(Pat("a", StrRet::new)).match("a", kLst("", 1), onFail);
        Many1(Pat("a", StrRet::new)).match("aaaaa", kLst("", 5), onFail);
    }

    static void test_count() {
        Rule a0 = Count(Pat("a", StrRet::new), 0);
        Rule a1 = Count(Pat("a", StrRet::new), 1);
        Rule a2 = Count(Pat("a", StrRet::new), 2);
        Rule a3 = Count(Pat("a", StrRet::new), 3);

        a0.match("a", kLst("a", 0), onFail);
        a1.match("a", kLst("", 1), onFail);
        a1.match("aa", kLst("a", 1), onFail);
        a2.match("aaa", kLst("a", 2), onFail);

        fail(() -> a3.match("aa", (s, r) -> { }, onFail));
    }

    static void test_manyTill() {
        Rule comment = Seq(Pat("/\\*"), ManyTill(AnyChar(), Pat("\\*/")), (a, b) -> b);
        comment.match("/* hello world!  */", (s, r) -> {
            assert s.isEmpty();
            String s1 = ((ListRet) r).lst.stream().map(it -> ((StrRet) it).str).collect(Collectors.joining("")).trim();
            assert "hello world!".equals(s1);
        }, onFail);

        Rule commentReg = Seq(Pat("/\\*"), ManyTill(Pat("((?!\\*/).)+", StrRet::new), Pat("\\*/")), (a, b) -> b);
        commentReg.match("/* hello world!  */", (s, r) -> {
            assert s.isEmpty();
            assert "hello world!".equals(((StrRet) ((ListRet) r).lst.get(0)).str.trim());
        }, onFail);
    }

    static void test_notFollowedBy() {
        Rule let = Seq(
                Pat("let", StrRet::new),
                NotFollowedBy(Pat("[a-zA-Z0-9]+")),
                (a, b) -> a
        );
        let.match("let", kStr("", "let"), onFail);
        fail(() -> let.match("lets", (s, r) -> { }, onFail));

        Rule notFollowByEOF = Seq(Pat("let", StrRet::new),
                NotFollowedBy(EOF()),
                (a, b) -> a
        );
        notFollowByEOF.match("let ", kStr(" ", "let"), onFail);
        fail(() -> notFollowByEOF.match("let", (s, r) -> { }, onFail));
    }

    static void sepBy_(Rule sepBy, Rule sepBy1) {
        sepBy.match("", kLst("", 0), onFail);
        sepBy.match("1 , 2, 3,4", kLst("", 4), onFail);
        sepBy.match("1 , 2, 3,4,", kLst(",", 4), onFail);
        sepBy.match("1 , 2, 3,4 ,", kLst(" ,", 4), onFail);

        fail(() -> sepBy1.match("", (s, r) -> {}, onFail));
        sepBy1.match("1", kLst("", 1), onFail);
        sepBy1.match("1,", kLst(",", 1), onFail);
        sepBy1.match("1 , 2, 3,4", kLst("", 4), onFail);
        sepBy1.match("1 , 2, 3,4,", kLst(",", 4), onFail);
        sepBy1.match("1 , 2, 3,4 ,", kLst(" ,", 4), onFail);
    }

    static void sepEndBy_(Rule sepEndBy, Rule sepEndBy1) {
        sepEndBy.match("", kLst("", 0), onFail);
        sepEndBy.match("1 , 2, 3,4", kLst("", 4), onFail);
        sepEndBy.match("1 , 2, 3,4,", kLst("", 4), onFail);
        sepEndBy.match("1 , 2, 3,4 ,", kLst("", 4), onFail);

        fail(() -> sepEndBy1.match("", (s, r) -> {}, onFail));
        sepEndBy1.match("1", kLst("", 1), onFail);
        sepEndBy1.match("1,", kLst("", 1), onFail);
        sepEndBy1.match("1 , 2, 3,4", kLst("", 4), onFail);
        sepEndBy1.match("1 , 2, 3,4,", kLst("", 4), onFail);
        sepEndBy1.match("1 , 2, 3,4 ,", kLst("", 4), onFail);
    }

    static void endBy_(Rule endBy, Rule endBy1) {
        endBy.match("", kLst("", 0), onFail);
        endBy.match("1 , 2, 3,4", kLst("4", 3), onFail);
        endBy.match("1 , 2, 3,4,", kLst("", 4), onFail);
        endBy.match("1 , 2, 3,4 ,", kLst("", 4), onFail);

        fail(() -> endBy1.match("", (s, r) -> {}, onFail));
        fail(() -> endBy1.match("1", (s, r) -> {}, onFail));
        endBy1.match("1,", kLst("", 1), onFail);
        endBy1.match("1 , 2, 3,4", kLst("4", 3), onFail);
        endBy1.match("1 , 2, 3,4,", kLst("", 4), onFail);
        endBy1.match("1 , 2, 3,4 ,", kLst("", 4), onFail);
    }

    static void test_sepEndBy() {
        Rule comma = Between(Whitespace(), Whitespace(), Pat(",", StrRet::new));

        sepBy_(
                SepBy(Pat("\\d+", StrRet::new), comma),
                SepBy1(Pat("\\d+", StrRet::new), comma)
        );
//        sepBy_(
//                SepBy(Pat("\\d+", StrRet::new), comma, false),
//                SepBy1(Pat("\\d+", StrRet::new), comma, false)
//        );

        sepEndBy_(
                SepEndBy(Pat("\\d+", StrRet::new), comma),
                SepEndBy1(Pat("\\d+", StrRet::new), comma)
        );

//        sepEndBy_(
//                SepBy(Pat("\\d+", StrRet::new), comma, true),
//                SepBy1(Pat("\\d+", StrRet::new), comma, true)
//        );

        endBy_(
                EndBy(Pat("\\d+", StrRet::new), comma),
                EndBy1(Pat("\\d+", StrRet::new), comma)
        );
    }

    static void test_chainr() {
        Rule cr1 = Chainr1(Pat("\\d+", StrRet::new), Pat("\\s*[+-]\\s*", s -> new StrRet(s.trim())));
        Rule cr = Chainr(Pat("\\d+", StrRet::new), Pat("\\s*[+-]\\s*", s -> new StrRet(s.trim())), null);

        for (Rule rule : new Rule[]{cr1, cr}) {
            rule.match("1 + 2 -3 + 4", (s, r) -> {
                assert s.isEmpty();
                assert new Triple(
                        new StrRet("+"),
                        new StrRet("1"),
                        new Triple(
                                new StrRet("-"),
                                new StrRet("2"),
                                new Triple(
                                        new StrRet("+"),
                                        new StrRet("3"),
                                        new StrRet("4")))).equals(r);
            }, onFail);
        }

        cr.match("", kNull(""), onFail);
        fail(() -> cr1.match("", (s, r) -> { }, onFail));

        for (Rule rule : new Rule[]{cr1, cr}) {
            cr1.match("1+2", (s, r) -> {
                assert s.isEmpty();
                assert new Triple(new StrRet("+"), new StrRet("1"), new StrRet("2")).equals(r);
            }, onFail);
        }

        for (Rule rule : new Rule[]{cr1, cr}) {
            rule.match("1", kStr("", "1"), onFail);
        }
    }

    static void test_chainl() {
        Rule cl1 = Chainl1(Pat("\\d+", StrRet::new), Pat("\\s*[+-]\\s*", s -> new StrRet(s.trim())));
        Rule cl = Chainl(Pat("\\d+", StrRet::new), Pat("\\s*[+-]\\s*", s -> new StrRet(s.trim())), null);

        for (Rule rule : new Rule[]{cl1, cl}) {
            rule.match("1 + 2 -3 + 4", (s, r) -> {
                assert s.isEmpty();
                assert new Triple(
                        new StrRet("+"),
                        new Triple(
                                new StrRet("-"),
                                new Triple(
                                        new StrRet("+"),
                                        new StrRet("1"),
                                        new StrRet("2")),
                                new StrRet("3")
                                ),
                        new StrRet("4")
                        ).equals(r);
            }, onFail);
        }

        cl.match("", kNull(""), onFail);
        fail(() -> cl1.match("", (s, r) -> { }, onFail));

        for (Rule rule : new Rule[]{cl1, cl}) {
            cl1.match("1+2", (s, r) -> {
                assert s.isEmpty();
                assert new Triple(new StrRet("+"), new StrRet("1"), new StrRet("2")).equals(r);
            }, onFail);
        }

        for (Rule rule : new Rule[]{cl1, cl}) {
            rule.match("1", kStr("", "1"), onFail);
        }
    }

//    static void test_stackoverflow() {
//        try {
//            Many(Pat(".*", StrRet::new)).match("", (s, r) -> { }, onFail);
//            throw new Error();
//        } catch (StackOverflowError ignored) { }
//
//        try {
//            Many1(Pat(".*", StrRet::new)).match("", (s, r) -> { }, onFail);
//            throw new Error();
//        } catch (StackOverflowError ignored) { }
//
//        try {
//            SepBy(Pat(".*", StrRet::new), Pat(".*", StrRet::new)).match("", (s, r) -> { }, onFail);
//            throw new Error();
//        } catch (StackOverflowError ignored) { }
//
//        try {
//            SepBy1(Pat(".*", StrRet::new), Pat(".*", StrRet::new)).match("", (s, r) -> { }, onFail);
//            throw new Error();
//        } catch (StackOverflowError ignored) { }
//    }


    public static void main(String[] args) throws Exception {
        for (Method it : TestParsec.class.getDeclaredMethods()) {
            if (it.getName().startsWith("test_")) {
                it.invoke(null);
            }
        }
    }
}
