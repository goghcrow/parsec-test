package xiao.playground.peg;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static xiao.playground.peg.Parsec4.*;
import static xiao.playground.peg.Parsec4.CharParsers.*;
import static xiao.playground.peg.Parsec4.Combinators.*;

@SuppressWarnings("unused")
public interface TestParsec4 {

    static <E> Function<List<E>, String> Join() {
        return v -> v.stream().map(String::valueOf).collect(joining());
    }

    static <E> Function<List<E>, String> Join(String delimiter) {
        return v -> v.stream().map(String::valueOf).collect(joining(delimiter));
    }

    static void assertEquals(Object a, Result b) {
        assert b.succ;
        assert Objects.equals(a, b.ret);
    }
    static void assertNotEquals(Object a, Result b) {
        assert b.succ;
        assert !Objects.equals(a, b.ret);
    }
    static void assertArrayEquals(Object[] a, Result b) {
        assert b.succ;
        assert Arrays.equals(a, ((Object[]) b.ret)); // todo
    }


    static void main(String[] args) throws Exception {
        TestUtils.runMainWithEnableAssert(TestParsec4.class, args, n -> n.startsWith(TestParsec4.class.getPackage().getName()));

        for (Method it : TestParsec4.class.getDeclaredMethods()) {
            if (it.getName().startsWith("test") || it.getName().endsWith("Test")) {
                it.invoke(null);
            } else {
                System.out.println(it);
            }
        }
    }



    static void test_choose() {
        Parsec4<String, Character> choose = Choose(Str("Hello"), Str("World"));
        assertEquals("Hello", choose.parse("Hello"));
        assertEquals("World", choose.parse("World"));
        assert !choose.parse("xxx").succ;
    }



    interface Expr {
        class Val implements Expr {
            public final String val;
            public Val(String val) { this.val = val; }
            @Override public String toString() { return val; }
        }
        class App implements Expr {
            public final String op;
            public final Expr lval;
            public final Expr rval;
            public App(String op, Expr lval, Expr rval) {
                this.op = op;
                this.lval = lval;
                this.rval = rval;
            }
            @Override public String toString() { return "(" + op + ", " + lval + ", " + rval + ")"; }
        }
    }
    class Triple implements BiOperator<Expr, String, String> {
        @Override public Expr val(String val) { return new Expr.Val(val); }
        @Override public Expr app(String op, Expr lval, Expr rval) { return new Expr.App(op, lval, rval); }
    }
    BiOperator<Expr, String, String> triple = new Triple();

    static void test_Chain() {
        Parsec4<Expr, Character> cl = Chainl(Pat("\\d+"), Pat("\\s*[+-]\\s*").map(String::trim), null, triple);
        Parsec4<Expr, Character> cr = Chainr(Pat("\\d+"), Pat("\\s*[+-]\\s*").map(String::trim), null, triple);

        Result<Expr, Character> r1 = cl.parse("1 + 2 - 3 + 4");
        assert r1.succ;
        System.out.println(r1.ret);

        Result<Expr, Character> r2 = cr.parse("1 + 2 - 3 + 4");
        assert r2.succ;
        System.out.println(r2.ret);



//        for (Rule rule : new Rule[]{cl1, cl}) {
//            rule.match("1 + 2 -3 + 4", (s, r) -> {
//                assert s.isEmpty();
//                assert new Triple(
//                        new StrRet("+"),
//                        new Triple(
//                                new StrRet("-"),
//                                new Triple(
//                                        new StrRet("+"),
//                                        new StrRet("1"),
//                                        new StrRet("2")),
//                                new StrRet("3")
//                        ),
//                        new StrRet("4")
//                ).equals(r);
//            }, onFail);
//        }
//
//        cl.match("", kNull(""), onFail);
//        fail(() -> cl1.match("", (s, r) -> { }, onFail));
//
//        for (Rule rule : new Rule[]{cl1, cl}) {
//            cl1.match("1+2", (s, r) -> {
//                assert s.isEmpty();
//                assert new Triple(new StrRet("+"), new StrRet("1"), new StrRet("2")).equals(r);
//            }, onFail);
//        }
//
//        for (Rule rule : new Rule[]{cl1, cl}) {
//            rule.match("1", kStr("", "1"), onFail);
//        }
    }







    // ========================================================================

    static void testNext() {
        Sequence<Character> s = new Sequence<>(chars("hello"));
        Result<Character, Character> r;
        assertEquals( 'h', r = s.next());
        assertEquals('e', r = (r.state.next()));
        assertEquals('l', r = (r.state.next()));
        assertEquals('l', r = (r.state.next()));
        assertEquals('o', r = (r.state.next()));
    }

    // ========================================================================


    static void testCRLF() {
        assertEquals("\r\n", CRLF.parse("\r\n"));
        assertEquals("\r\n", EOL.parse("\r\n"));
        assertEquals("\n", EOL.parse("\n"));
        assertEquals("\r", EOL.parse("\r"));
        assertEquals( "\n", Str("\r").then(Str("\n")).parse("\r\n"));
    }

    static void testFail() {
        assert !Fail("").parse("").succ;
    }

    static void testOne() {
        assertEquals('H', Any().parse("Hello"));
    }

    static void testEQ() {
        assertEquals('h', EQ('h').parse("hello"));
    }

    static void testEOF() {
        assert !EOF().parse("hello").succ;
    }

    static void testStr() {
        assertEquals("Hello", Str("Hello").over(EOF()).parse("Hello"));
    }

    static void testReturn() {
        Sequence<Character> s = new Sequence<>(chars(""));
        assertEquals("Hello", Combinators.<String, Character>Return("Hello").parse(s));
        assertEquals("Hello", Return("Hello", TypeRef.Char).parse(s));

        {
            Sequence<Character> s1 = new Sequence<>(chars("Hello World"));
            assertEquals(new BigDecimal("3.1415926"), Combinators.<BigDecimal, Character>Return(new BigDecimal("3.1415926")).parse(s1));
            assertEquals(new BigDecimal("3.1415926"), Return(new BigDecimal("3.1415926"), TypeRef.Char).parse(s1));
        }
    }

    static void testAhead() {
        Sequence<Character> s = new Sequence<>(chars("this is a string."));
        assertEquals( "this", Str("this").over(LookAhead(Str(" is"))).parse(s));

        Sequence<Character> s1 = new Sequence<>(chars("this is a string."));
        assertEquals( "is", Str("this").then(Space).then(LookAhead(Str("is"))).parse(s1));


        Sequence<Character> s2 = new Sequence<>(chars("this is a string."));
        assert !Str("this").then(Space).then(LookAhead(Str(" is"))).parse(s2).succ;
    }

    static void testBetween() {
        assertEquals("hello", Between(
                Ch('['),
                Ch(']'),
                Many1(NE(']'))
        ).map(Join()).parse("[hello]"));

        assertEquals("hello", Between(
                Ch('['),
                Ch(']'),
                Pat("[^\\]]*")
        ).parse("[hello]"));
    }

    static void testFind() {
        assertEquals("find", Find(Str("find")).parse("It is a junit test case for find parsec."));
        assert !Find(Str("Fail")).parse("It is a junit test case for find parsec.").succ;
    }

    static void testMany() {
        assert !Many1(EQ('h')).parse("ello").succ;

        assertEquals( "h", Many1(EQ('h')).map(Join()).parse("hello"));

        assertEquals( "hello", Many1(Combinators.<Character>Any()).map(Join()).parse("hello"));
        assertEquals( "hello", Many1(Any(TypeRef.Char)).map(Join()).parse("hello"));

        assert 2 == Many(EQ('h')).parse("hhello").ret.size();
        assert 2 == Many1(EQ('h')).parse("hhello").ret.size();
    }

    static void testManyTill() {
        assert 6 == ManyTill(EQ('h'), EQ('l')).parse("hhhhhhlhhhll").ret.size();
    }

    static void testNCh() {
        assertEquals('e', NotCh('H').parse("ello"));
        assert !NotCh('H').parse("Hello").succ;
    }

    static void testChNone() {
        assertEquals('X', ChNone("HELLO").parse("X"));
        assert !ChNone("HELLO").parse("H").succ;
    }

    static void testSkipSpaces() {
        assertEquals(null, SkipSpaces.parse(" Hello"));
    }

    static void testSkipWhiteSpaces() {
        assertEquals(null, SkipWhiteSpaces.parse(" Hello"));
    }

    static void testJoinChars() {
        assertEquals("HHHHHH", Many1(Ch('H')).map(Join()).parse("HHHHHH"));
    }

    static void NoneOfTest() {
        assertEquals('h', NoneOf(Stream.of('k', 'o', 'f').collect(toList())).parse("hello"));
        assert !NoneOf(Stream.of('k', 'f', 's').collect(toList())).parse("sound").succ;
    }


    static void OneOfTest() {
        assertEquals('h', OneOf(Stream.of('b', 'e', 'h', 'f').collect(toList())).parse("hello"));
        assert !OneOf(Stream.of('d', 'a', 't', 'e').collect(toList())).parse("hello").succ;
    }

    static void SepBy1Test() {
        Parsec4<List<Character>, Character> m = SepBy1(Ch('h'), Ch('l'));

        List<Character> a = m.parse("hlhlhlhlhlhll").ret;
        assert 6 == a.size();

        Sequence<Character> s1 = new Sequence<>(chars("hlh,h.hlhlhll"));
        List<Character> b = m.parse(s1).ret;
        assert 2 == b.size();
        m.parse(s1);
    }

    static void SepByTest() {
        assert 6 == SepBy(EQ('h'), EQ('l')).parse("hlhlhlhlhlhll").ret.size();
    }

    static void testSkip1_simple() {
        assertEquals(null, SkipMany1(Str("left")).parse("left right left right"));
    }

    static void testSkip1_simpleStatus() {
        Sequence<Character> s = new Sequence<>(chars("left right left right"));
        assert SkipMany1(Str("left ")).parse(s).state.buf.size() == "right left right".length();
    }

    static void testSkip1_statusMore() {
        Sequence<Character> state = new Sequence<>(chars("left left right right"));
        assert SkipMany1(Str("left ")).parse(state).state.buf.size() == "right right".length();
    }

    static void testSkip1_fail() {
        Sequence<Character> s = new Sequence<>(chars("right right left left"));
        assert !SkipMany1(Str("left ")).parse(s).succ;
    }

    static void testSkip_oneSkip() {
        Sequence<Character> s = new Sequence<>(chars("hello World"));
        assert SkipMany(EQ('h')).parse(s).state.buf.size() == "hello World".length() - 1;
    }

    static void testSkip_stopAtStart() {
        Sequence<Character> s = new Sequence<>(chars("hello World"));
        assert SkipMany(EQ('e')).parse(s).state.buf.size() == "hello World".length();
    }

    static void testSkip_skipSpaces() {
        Sequence<Character> stase = new Sequence<>(chars("\t\t \thello World"));
        assert SkipMany(ChIn(" \t")).parse(stase).state.buf.size() == "hello World".length();
    }

    static void testSkip_skipNothing() {
        Sequence<Character> stase = new Sequence<>(chars("\nhello World"));
        assert SkipMany(ChIn(" \t")).parse(stase).state.buf.size() == "\nhello World".length();;
    }

    static void SpaceTest() {
        assertEquals(' ', Space.parse(" "));

        assert !Space.parse("\t").succ;
    }

    static void strTest() {
        assertEquals("Hello World", Str("Hello World").parse("Hello World"));
        assertEquals("Hello", Str("Hello").parse("Hello World"));
        assertEquals( "汉", Str("汉").parse("汉字"));

        assert !Str("Hello world").parse("Hello").succ;
    }

    static void digitTest() {
        assertEquals('1', Digit.parse("1"));
    }

    static void decimalTest() {
        // assertEquals("0.123", Decimal().parse(Seq(".123")));
        assertEquals("12345.123", Decimal.parse("12345.123"));
        assertEquals("-12345.123", Decimal.parse("-12345.123"));
        assertEquals("-12345.123", Decimal.parse("-12345.123a123"));

        // assertEquals("0.123", UDecimal.parse(".123"));
        assertEquals("12345.123", UDecimal.parse("12345.123"));
        assertEquals("12345.123", UDecimal.parse("12345.123x123"));

        assertEquals("12345.123e+10", UDecimal.parse("12345.123e+10"));
        assertEquals("12345.123E-01", UDecimal.parse("12345.123E-01"));

        assert !Decimal.parse("-x1234").succ;
        assert !UDecimal.parse("x1234").succ;
    }

    static void intTest() {
        assertEquals("123", Int.parse("123"));
        assertEquals("-123", Int.parse("-123"));

        assertEquals("12345", UInt.parse("12345"));
        assertEquals( "12345", UInt.parse("12345x123"));

        assert !UInt.parse("x1234").succ;
    }

    static void testOtherWise() {
        Parsec4<Character, Character> otherwise = Ch('1').or(Ch('2')).or(Ch('3'));
        assertEquals('1', otherwise.parse("1"));
        assertEquals('2', otherwise.parse("2"));
        assertEquals('3', otherwise.parse("3"));
    }

    static void testOtherWiseEx() {
        Parsec4<Character, Character> otherwise = Ch('1').or(Ch('2')).or(Ch('3'));
        assert !otherwise.parse("4").succ;
    }

    static void testEOS() {
        assert !Find(Ch('a')).parse("cdefg").succ;
    }

    static void testRegex() {
        assertEquals("1234ab", Regex("\\d+").flatMap(v -> s -> {
            Result<String, Character> r = Str("ab").parse(s);
            assert r.succ;
            return Result.succ(r.state, v.group() + r.ret);
        }).parse("1234ab"));
    }

    static void testOptional() {
        assertEquals(Optional.empty(), Optional(Ch('a')).parse("b"));
        assertEquals(Optional.of('a'), Optional(Ch('a')).parse("a"));

        assertEquals(Optional.of('H'), Optional(Ch('H')).parse("Hello"));
        assertEquals(Optional.empty(), Optional(Ch('H')).parse("ello"));
    }
}
