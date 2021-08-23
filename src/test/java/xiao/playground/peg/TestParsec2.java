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
import static xiao.playground.peg.Parsec2.*;
import static xiao.playground.peg.Parsec2.CharParsers.*;
import static xiao.playground.peg.Parsec2.Combinators.*;

@SuppressWarnings("unused")
public interface TestParsec2 {

    static <E> Function<List<E>, String> Join() {
        return v -> v.stream().map(String::valueOf).collect(joining());
    }

    static <E> Function<List<E>, String> Join(String delimiter) {
        return v -> v.stream().map(String::valueOf).collect(joining(delimiter));
    }

    static void assertEquals(Object a, Object b) { assert Objects.equals(a, b); }
    static void assertNotEquals(Object a, Object b) { assert !Objects.equals(a, b); }
    static void assertArrayEquals(Object[] a, Object[] b) { assert Arrays.equals(a, b); }


    static void main(String[] args) throws Exception {
        TestUtils.runMainWithEnableAssert(TestParsec2.class, args, n -> n.startsWith(TestParsec2.class.getPackage().getName()));

        for (Method it : TestParsec2.class.getDeclaredMethods()) {
            if (it.getName().startsWith("test") || it.getName().endsWith("Test")) {
                it.invoke(null);
            } else {
                System.out.println(it);
            }
        }
    }



    static void test_choose() {
        Parsec2<String, Character> choose = Choose(Str("Hello"), Str("World"));

        assert "Hello".equals(choose.parse("Hello"));
        assert "World".equals(choose.parse("World"));

        try {
            choose.parse("xxx");
            throw new Error();
        } catch (ParsecException ignored) { }
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
        Parsec2<Expr, Character> cl = Chainl(Pat("\\d+"), Pat("\\s*[+-]\\s*").map(String::trim), null, triple);
        Parsec2<Expr, Character> cr = Chainr(Pat("\\d+"), Pat("\\s*[+-]\\s*").map(String::trim), null, triple);

        System.out.println(cl.parse("1 + 2 -3 + 4"));
        System.out.println(cr.parse("1 + 2 -3 + 4"));



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

    static void testIndex() {
        try {
            String data = "abc";
            Sequence<Character> s = new Sequence<>(data);
            while (s.index() < data.length()) {
                assertEquals(data.charAt(s.index()), s.next());
            }
            s.next();
            throw new Error();
        } catch (EOFException ignore) { }
    }

    static void testBegin() {
        Sequence<Character> s = new Sequence<>("hello");
        assertEquals('h', s.next());
        int a = s.begin();
        s.next();
        s.next();
        s.next();
        s.rollback(a);
        assertEquals('e', s.next());
    }

    static void testCommit() {
        Sequence<Character> s = new Sequence<>("hello");

        int a = s.begin();
        assertEquals('h', s.next());

        s.next();
        s.commit(a);
        assertEquals('l', s.next());
    }

    static void testRollback() {
        Sequence<Character> s = new Sequence<>("hello");

        int a = s.begin();
        assertEquals('h', s.next());

        s.rollback(a);
        assertEquals('h', s.next());
    }

    static void testNext() {
        Sequence<Character> s = new Sequence<>("hello");
        assertEquals( 'h', s.next());
        assertEquals('e', s.next());
        assertEquals('l', s.next());
        assertEquals('l', s.next());
        assertEquals('o', s.next());
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
        try {
            Fail("").parse("");
            throw new Error();
        } catch (ParsecException ignore) { }
    }

    static void testOne() {
        assertEquals('H', Any().parse("Hello"));
    }

    static void testEQ() {
        assertEquals('h', EQ('h').parse("hello"));
    }

    static void testEOF() {
        try {
            EOF().parse("hello");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void testStr() {
        assertEquals("Hello", Str("Hello").over(EOF()).parse("Hello"));
    }

    static void testReturn() {
        Sequence<Character> s = new Sequence<>("");
        int idx = s.index();
        assertEquals("Hello", Combinators.<String, Character>Return("Hello").parse(s));
        assertEquals("Hello", Return("Hello", TypeRef.Char).parse(s));
        assertEquals(idx, s.index());

        {
            Sequence<Character> s1 = new Sequence<>("Hello World");
            int idx1 = s1.index();
            assertEquals(new BigDecimal("3.1415926"), Combinators.<BigDecimal, Character>Return(new BigDecimal("3.1415926")).parse(s1));
            assertEquals(new BigDecimal("3.1415926"), Return(new BigDecimal("3.1415926"), TypeRef.Char).parse(s1));
            assertEquals(idx1, s1.index());
        }
    }

    static void testAhead() {
        Sequence<Character> s = new Sequence<>("this is a string.");
        assertEquals( "this", Str("this").over(LookAhead(Str(" is"))).parse(s));
        assertEquals(s.index(), 4);

        Sequence<Character> s1 = new Sequence<>("this is a string.");
        assertEquals( "is", Str("this").then(Space).then(LookAhead(Str("is"))).parse(s1));
        assertEquals(s1.index(), 5);


        Sequence<Character> s2 = new Sequence<>("this is a string.");
        try {
            Str("this").then(Space).then(LookAhead(Str(" is"))).parse(s2);
        } catch (ParsecException e) {
            assertEquals(5, s2.index());
        }
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

        try {
            Find(Str("Fail")).parse("It is a junit test case for find parsec.");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void testMany() {
        try {
            Many1(EQ('h')).parse("ello");
            throw new Error();
        } catch (ParsecException ignored) { }

        assertEquals( "h", Many1(EQ('h')).map(Join()).parse("hello"));

        assertEquals( "hello", Many1(Combinators.<Character>Any()).map(Join()).parse("hello"));
        assertEquals( "hello", Many1(Any(TypeRef.Char)).map(Join()).parse("hello"));

        assertEquals( 2, Many(EQ('h')).parse("hhello").size());
        assertEquals( 2, Many1(EQ('h')).parse("hhello").size());
    }

    static void testManyTill() {
        assertEquals( 6, ManyTill(EQ('h'), EQ('l')).parse("hhhhhhlhhhll").size());
    }

    static void testNCh() {
        assertEquals('e', NotCh('H').parse("ello"));

        try {
            NotCh('H').parse("Hello");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void testChNone() {
        assertEquals('X', ChNone("HELLO").parse("X"));
        try {
            ChNone("HELLO").parse("H");
            throw new Error();
        } catch (ParsecException ignored) { }
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
        try {
            NoneOf(Stream.of('k', 'f', 's').collect(toList())).parse("sound");
            throw new Error();
        } catch (ParsecException ignored) { }
    }


    static void OneOfTest() {
        assertEquals('h', OneOf(Stream.of('b', 'e', 'h', 'f').collect(toList())).parse("hello"));
        try {
            OneOf(Stream.of('d', 'a', 't', 'e').collect(toList())).parse("hello");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void SepBy1Test() {
        try {
            Parsec2<List<Character>, Character> m = SepBy1(Ch('h'), Ch('l'));

            List<Character> a = m.parse("hlhlhlhlhlhll");
            assertEquals( 6, a.size());

            Sequence<Character> s1 = new Sequence<>("hlh,h.hlhlhll");
            List<Character> b = m.parse(s1);
            assertEquals( 2, b.size());
            m.parse(s1);
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void SepByTest() {
        assertEquals( 6, SepBy(EQ('h'), EQ('l')).parse("hlhlhlhlhlhll").size());
    }

    static void testSkip1_simple() {
        assertEquals(null, SkipMany1(Str("left")).parse("left right left right"));
    }

    static void testSkip1_simpleStatus() {
        Sequence<Character> s = new Sequence<>("left right left right");
        SkipMany1(Str("left ")).parse(s);
        assertEquals(5, s.index()); }

    static void testSkip1_statusMore() {
        Sequence<Character> state = new Sequence<>("left left right right");
        SkipMany1(Str("left ")).parse(state);
        assertEquals(10, state.index());
    }

    static void testSkip1_fail() {
        Sequence<Character> s = new Sequence<>("right right left left");
        try {
            SkipMany1(Str("left ")).parse(s);
        } catch (ParsecException e) {
            assertEquals(0, s.index());
        }
    }

    static void testSkip_oneSkip() {
        Sequence<Character> s = new Sequence<>("hello World");
        SkipMany(EQ('h')).parse(s);
        assertEquals(1, s.index());
    }

    static void testSkip_stopAtStart() {
        Sequence<Character> s = new Sequence<>("hello World");
        SkipMany(EQ('e')).parse(s);
        assertEquals(0, s.index());
    }

    static void testSkip_skipSpaces() {
        Sequence<Character> stase = new Sequence<>("\t\t \thello World");
        SkipMany(ChIn(" \t")).parse(stase);
        assertEquals(4, stase.index());
    }

    static void testSkip_skipNothing() {
        Sequence<Character> stase = new Sequence<>("\nhello World");
        SkipMany(ChIn(" \t")).parse(stase);
        assertEquals(0, stase.index());
    }

    static void SpaceTest() {
        assertEquals(' ', Space.parse(" "));

        try {
            Space.parse("\t");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void strTest() {
        assertEquals("Hello World", Str("Hello World").parse("Hello World"));
        assertEquals("Hello", Str("Hello").parse("Hello World"));
        assertEquals( "汉", Str("汉").parse("汉字"));

        try {
            Str("Hello world").parse("Hello");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void tryTest() {
        Sequence<Character> s = new Sequence<>("HelloWorld");
        int idx = s.index();
        assertEquals( "Hello", Try(Str("Hello")).parse(s));
        assertNotEquals(idx, s.index());

        Sequence<Character> s1 = new Sequence<>("HelloWorld");
        int idx1 = s1.index();
        try{
            Try(Str("hello")).parse(s1);
            throw new Error();
        } catch(Exception e){
            assertEquals(s1.index(), idx1);
        }
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

        try {
            Decimal.parse("-x1234");
            throw new Error();
        } catch (ParsecException ignored) { }

        try {
            UDecimal.parse("x1234");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void intTest() {
        assertEquals("123", Int.parse("123"));
        assertEquals("-123", Int.parse("-123"));

        assertEquals("12345", UInt.parse("12345"));
        assertEquals( "12345", UInt.parse("12345x123"));

        try {
            UInt.parse("x1234");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void testOtherWise() {
        Parsec2<Character, Character> otherwise = Ch('1').or(Ch('2')).or(Ch('3'));
        assertEquals('1', otherwise.parse("1"));
        assertEquals('2', otherwise.parse("2"));
        assertEquals('3', otherwise.parse("3"));
    }

    static void testOtherWiseEx() {
        try {
            Parsec2<Character, Character> otherwise = Ch('1').or(Ch('2')).or(Ch('3'));
            otherwise.parse("4");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void testEOS() {
        try {
            Find(Ch('a')).parse("cdefg");
            throw new Error();
        } catch (ParsecException ignored) { }
    }

    static void testRegex() {
        assertEquals("1234ab", Regex("\\d+").flatMap(v -> s -> v.group() + Str("ab").parse(s)).parse("1234ab"));
    }

    static void testOptional() {
        assertEquals(Optional.empty(), Optional(Ch('a')).parse("b"));
        assertEquals(Optional.of('a'), Optional(Ch('a')).parse("a"));

        assertEquals(Optional.of('H'), Optional(Ch('H')).parse("Hello"));
        assertEquals(Optional.empty(), Optional(Ch('H')).parse("ello"));
    }
}
