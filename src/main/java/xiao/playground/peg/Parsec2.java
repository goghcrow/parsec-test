package xiao.playground.peg;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Character.*;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.Collections.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static xiao.playground.peg.Parsec2.CharParsers.*;
import static xiao.playground.peg.Parsec2.Combinators.*;


/**
 * Parser Combinator
 * @author chuxiaofeng
 *
 * Parsec Demo <br>
 *
 * - 通用的 parsec, seq<E>
 * - 强类型，非 CPS, 正常的写法, 用异常来做回溯
 * - seq 带状态, 写起来的心智负担比 CPS 风格无状态的版本心智负担更高
 */
public interface Parsec2<R, E> {

    R parse(Sequence<E> s) throws ParsecException;

    default R parse(String s) {
        return parse(new Sequence<>(s));
    }

    default <C> Parsec2<C, E> map(Function<R, C> mapper) {
        return s -> mapper.apply(parse(s));
    }

    default <C> Parsec2<C, E> flatMap(Function<R, Parsec2<C, E>> binder) {
        return s -> binder.apply(parse(s)).parse(s);
    }


    default <C, RC> Parsec2<RC, E> seq(Parsec2<C, E> p, BiFunction<R, C, RC> mapper)
                                                                { return Seq(this, p, mapper); }
    default <C> Parsec2<C, E> then(Parsec2<C, E> p)             { return seq(p, (a, b) -> b); }
    default <C> Parsec2<R, E> over(Parsec2<C, E> p)             { return seq(p, (a, b) -> a); }
    default Parsec2<R, E> or(Parsec2<? extends R, E> p)         { return Choose(this, p); } // or | orElse | otherwise
    default Parsec2<R, E> try1()                                { return Try(this); }
    default Parsec2<R, E> peek()                                { return LookAhead(this); }
    default Parsec2<Optional<R>, E> optional()                  { return Optional(this); }
    default Parsec2<R, E> optional(R def)                       { return Option(this, def); }
    default Parsec2<List<R>, E> many()                          { return Many(this); }
    default Parsec2<List<R>, E> many1()                         { return Many1(this); }
    default <L> Parsec2<List<R>, E> manyTill(Parsec2<L, E> end) { return ManyTill(this, end); }
    default Parsec2<R, E> skip()                                { return Skip(this); }
    default Parsec2<R, E> skipMany()                            { return SkipMany(this); }
    default Parsec2<R, E> skipMany1()                           { return SkipMany1(this); }
    default Parsec2<List<R>, E> count(int n)                    { return Count(this, n); }
    default <S> Parsec2<List<R>, E> sepBy(Parsec2<S, E> by)     { return SepBy(this, by); }
    default <S> Parsec2<List<R>, E> sepBy1(Parsec2<S, E> by)    { return SepBy1(this, by); }
    default <S> Parsec2<List<R>, E> endBy(Parsec2<S, E> by)     { return EndBy(this, by); }
    default <S> Parsec2<List<R>, E> endBy1(Parsec2<S, E> by)    { return EndBy1(this, by); }
    default <S> Parsec2<List<R>, E> sepEndBy(Parsec2<S, E> by)  { return SepEndBy(this, by); }
    default <S> Parsec2<List<R>, E> sepEndBy1(Parsec2<S, E> by) { return SepEndBy1(this, by); }
    default <S> Parsec2<R, E> notFollowedBy()                   { return NotFollowedBy(this); }
    default <O, C> Parsec2<R, E> between(Parsec2<O, E> open, Parsec2<C, E> close)
                                                                { return Between(open, close, this); }

    default <O, Expr> Parsec2<Expr, E> chainl(Parsec2<O, E> op,
                                              Expr def,
                                              BiOperator<Expr, O, R> alg) { return Chainl(this, op, def, alg); }
    default <O, Expr> Parsec2<Expr, E> chainl1(Parsec2<O, E> op,
                                               BiOperator<Expr, O, R> alg) { return Chainl1(this, op, alg); }
    default <O, Expr> Parsec2<Expr, E> chainr(Parsec2<O, E> op,
                                              Expr def,
                                              BiOperator<Expr, O, R> alg) { return Chainr(this, op, def, alg); }
    default <O, Expr> Parsec2<Expr, E> chainr1(Parsec2<O, E> op,
                                               BiOperator<Expr, O, R> alg) { return Chainr1(this, op, alg); }


    @SuppressWarnings("InfiniteLoopStatement")
    interface Combinators {

        static <R1, R2, R, E> Parsec2<R, E> Seq(
                Parsec2<R1, E> front,
                Parsec2<R2, E> rear,
                BiFunction<R1, R2, R> mapper) {
            return s -> mapper.apply(front.parse(s), rear.parse(s));
        }

        static <R1, R2, R3, R, E> Parsec2<R, E> Seq(
                Parsec2<R1, E> fst,
                Parsec2<R2, E> sec,
                Parsec2<R3, E> trd,
                TriFunction<R1, R2, R3, R> mapper) {
            return s -> mapper.apply(fst.parse(s), sec.parse(s), trd.parse(s));
        }

        // Choice
        @SafeVarargs
        static <R, E> Parsec2<R, E> Choose(Parsec2<? extends R, E>... ps) {
            if (ps.length == 0) {
                throw new IllegalArgumentException("No Choices");
            }
            return s -> {
                for (Parsec2<? extends R, E> parsec : ps) {
                    try {
                        return Try(parsec).parse(s);
                    } catch (ParsecException ignored) { }
                }
                throw s.trap("No Choice, stop at %d", s.index());
            };
        }

//        static <R, E> Parsec2<R, E> Choose(Parsec2<? extends R, E>... ps) {
//            return s -> {
//                int marked = s.index();
//                for (Parsec2<? extends R, E> parsec : ps) {
//                    try {
//                        return parsec.parse(s);
//                    } catch (ParsecException e) {
//                        // choice 的 parsec 不能消耗SeqElm
//                        if (s.index() != marked) throw e;
//                    }
//                }
//                throw s.trap("No Choice, stop at %s", s.index());
//            };
//        }

        // 配合 Choice 使用的
        static <R, E> Parsec2<R, E> Try(Parsec2<R, E> parsec) {
            return s -> {
                int t = s.begin();
                try{
                    R e = parsec.parse(s);
                    s.commit(t);
                    return e;
                } catch (ParsecException e) {
                    s.rollback(t);
                    throw e;
                }
            };
        }

        // ============================================================================


        final class TypeRef<T> {
            final static TypeRef<Character> Char = new TypeRef<>();
        }

        static <E> Parsec2<E, E> Any()                { return Sequence::next; }
        static <E> Parsec2<E, E> Any(TypeRef<E> t)    { return Sequence::next; }
        static <E> Parsec2<E, E> EQ(E item)           { return Satisfy(e -> Objects.equals(e, item)); }
        static <E> Parsec2<E, E> NE(E item)           { return Satisfy(e -> !Objects.equals(e, item)); }

        static <E> Parsec2<E, E> OneOf(List<E> items) {
            HashSet<E> es = new HashSet<>(items);
            return Satisfy(es::contains);
        }

        static <E> Parsec2<E, E> NoneOf(List<E> items) {
            HashSet<E> es = new HashSet<>(items);
            return Satisfy(e -> !es.contains(e));
        }

        static <E> Parsec2<E, E> Satisfy(Predicate<E> p/*, String expect, Object... args*/) {
            return s -> {
                int idx = s.begin();
                E e = s.next();
                if (p.test(e)) {
                    return e;
                } else {
                    throw s.trap("Not satisfy at %d: %s", idx, Objects.toString(e));
                }
            };
        }

        static <T, E> Parsec2<T, E> Return(T value) {
            return s -> value;
        }

        static <T, E> Parsec2<T, E> Return(T value, TypeRef<E> t) {
            return s -> value;
        }

        static <E> Parsec2<E, E> Fail(String fmt, Object... objects) {
            return s -> {
                throw s.trap(fmt, objects);
            };
        }

        static <R, E> Parsec2<R, E> Null() {
            return s -> null;
        }

        static <R, E> Parsec2<Optional<R>, E> Optional(Parsec2<R, E> p) {
            return Choose(p.map(Optional::of), Return(Optional.empty()));
//            return s -> {
//                try{
//                    return Optional.of(Try(p).parse(s));
//                } catch (ParsecException ignored) { }
//                return Optional.empty();
//            };
        }

        static <R, E> Parsec2<R, E> Option(Parsec2<R, E> p, R def) {
            return Choose(p, Return(def));
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec2<List<R>, E> Many(Parsec2<R, E> p) {
            return s -> {
                List<R> lst = new ArrayList<>();
                Parsec2<R, E> tp = Try(p);

                try{
                    while (true) {
                        lst.add(tp.parse(s));
                    }
                } catch (ParsecException ignored) {
                    return unmodifiableList(lst);
                }
            };
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec2<List<R>, E> Many1(Parsec2<R, E> p) {
            return Seq(p.map(Lists::of), Many(p), Lists::concat);
//            return s -> {
//                List<R> lst = new ArrayList<>();
//
//                lst.add(p.parse(s));
//                Parsec2<R, E> p = Try(p);
//
//                try{
//                    while (true) {
//                        lst.add(p.parse(s));
//                    }
//                } catch (ParsecException ignored) {
//                    return unmodifiableList(lst);
//                }
//            };
        }

        static <R, E> Parsec2<R, E> Skip(Parsec2<R, E> p) {
            return p.map(it -> null);
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec2<R, E> SkipMany(Parsec2<R, E> p) {
            return Many(p).map(it -> null);
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec2<R, E> SkipMany1(Parsec2<R, E> p) {
            return Many1(p).map(it -> null);
        }

//        static <R, E> Parsec2<R, E> Skip(Parsec2<R, E> p) {
//            return s -> {
//                int t = -1;
//                try {
//                    while (true) {
//                        t = s.begin();
//                        p.parse(s);
//                        s.commit(t);
//                    }
//                } catch (ParsecException ignored) {
//                    s.rollback(t);
//                }
//                return null;
//            };
//        }
//
//        static <R, E> Parsec2<R, E> Skip1(Parsec2<R, E> p) {
//            return s -> {
//                p.parse(s);
//                Skip(p).parse(s);
//                return null;
//            };
//        }

        static <R, E> Parsec2<List<R>, E> Count(Parsec2<R, E> p, int n) {
            if (n <= 0) {
                return Null();
            }
            return s -> {
                List<R> lst = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    lst.add(p.parse(s));
                }
                return lst;
            };
        }

        static <R, E, O, C> Parsec2<R, E> Between(Parsec2<O, E> open, Parsec2<C, E> close, Parsec2<R, E> p) {
//            return s -> {
//                open.parse(s);
//                R r = p.parse(s);
//                close.parse(s);
//                return r;
//            };
            return open.then(p).over(close);
        }

        // 另一种写法
        static <R, S, E> Parsec2<List<R>, E> SepBy(Parsec2<R, E> p, Parsec2<S, E> by, boolean optEndedSep) {
            return Option(SepBy1(p, by, optEndedSep), Lists.of());
        }
        static <R, S, E> Parsec2<List<R>, E> SepBy1(Parsec2<R, E> p, Parsec2<S, E> by, boolean optEndedSep) {
            Parsec2<List<R>, E> byp = Many(by.then(p));
            if (optEndedSep) {
                byp = byp.over(Optional(by));
            }
            return Seq(p, byp, Lists::prepend);
        }

        static <R, S, E> Parsec2<List<R>, E> SepBy(Parsec2<R, E> p, Parsec2<S, E> by) {
            return SepBy(p, by, false);
//            return Option(SepBy1(p, by), Lists.of());
//            return s -> {
//                List<R> lst = new ArrayList<>();
//
//                Parsec2<R, E> tp = Try(p);
//                Parsec2<S, E> tb = Try(by);
//
//                try {
//                    lst.add(tp.parse(s));
//                    while (true) {
//                        tb.parse(s);
//                        lst.add(tp.parse(s));
//                    }
//                } catch (ParsecException e) {
//                    return unmodifiableList(lst);
//                }
//            };
        }

        static <R, S, E> Parsec2<List<R>, E> SepBy1(Parsec2<R, E> p, Parsec2<S, E> by) {
            return SepBy1(p, by, false);
//            return Seq(p, Many(by.then(p)), Lists::prepend);
//            return s -> {
//                List<R> lst = new ArrayList<>();
//                lst.add(p.parse(s));
//
//                Parsec2<R, E> tp = Try(p);
//                Parsec2<S, E> tb = Try(by);
//
//                try {
//                    while (true) {
//                        tb.parse(s);
//                        lst.add(tp.parse(s));
//                    }
//                } catch (ParsecException e) {
//                    return unmodifiableList(lst);
//                }
//            };
        }

        static <R, S, E> Parsec2<List<R>, E> EndBy(Parsec2<R, E> p, Parsec2<S, E> by) {
            return Many(p.over(by));
        }

        static <R, S, E> Parsec2<List<R>, E> EndBy1(Parsec2<R, E> p, Parsec2<S, E> by) {
            return Many1(p.over(by));
        }

        static <R, S, E> Parsec2<List<R>, E> SepEndBy(Parsec2<R, E> p, Parsec2<S, E> by) {
            return SepBy(p, by, true);
        }
        static <R, S, E> Parsec2<List<R>, E> SepEndBy1(Parsec2<R, E> p, Parsec2<S, E> by) {
            return SepBy1(p, by, true);
        }

        // 构造左结合双目运算符解析
        static <R, O, E, Expr> Parsec2<Expr, E> Chainl(
                Parsec2<R, E> p,
                Parsec2<O, E> op,
                Expr def,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(Chainl1(p, op, alg), def);
        }

        static <R, O, E, Expr> Parsec2<Expr, E> Chainl1(
                Parsec2<R, E> p,
                Parsec2<O, E> op,
                BiOperator<Expr, O, R> alg
        ) {
            return p.map(alg::val).flatMap(lval -> s -> {
                return chainl1_rest(p, op, lval, alg).parse(s);
            });
        }

        static <R, O, E, Expr> Parsec2<Expr, E> chainl1_rest(
                Parsec2<R, E> p,
                Parsec2<O, E> op,
                Expr lval,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(
                    Seq(
                            op,
                            p,
                            (a, b) -> new Pair<>(a, alg.val(b))
                    ).flatMap(opp -> s -> {
                        Expr app = alg.app(opp.car, lval, opp.cdr);
                        return chainl1_rest(p, op, app, alg).parse(s);
                    }),
                    lval
            );
        }

        // 构造右结合双目运算符解析
        static <R, O, E, Expr> Parsec2<Expr, E> Chainr(
                Parsec2<R, E> p,
                Parsec2<O, E> op,
                Expr def,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(Chainr1(p, op, alg), def);
        }

        static <R, O, E, Expr> Parsec2<Expr, E> Chainr1(
                Parsec2<R, E> p,
                Parsec2<O, E> op,
                BiOperator<Expr, O, R> alg
        ) {
            return p.map(alg::val).flatMap(lval -> s -> {
                return chainr1_rest(p, op, lval, alg).parse(s);
            });
        }

        static <R, O, E, Expr> Parsec2<Expr, E> chainr1_rest(
                Parsec2<R, E> p,
                Parsec2<O, E> op,
                Expr lval,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(
                    Seq(
                            op,
                            Chainr1(p, op, alg),
                            (app, rval) -> alg.app(app, lval, rval)
                    ),
                    lval
            );
        }

        static <E> Parsec2<E, E> EOF() {
            return s -> {
                try {
                    E r = s.next();
                    throw s.trap("Expect eof but %s", r);
                } catch (EOFException e) {
                    return null;
                }
            };
        }

        static <R, E> Parsec2<R, E> LookAhead(Parsec2<R, E> p) {
            return s -> {
                int t = s.begin();
                try {
                    return p.parse(s);
                } finally {
                    s.rollback(t);
                }
            };
        }

        // 实现最长匹配规则, e.g. 识别关键词let, lets 应该识别为 id 不是 let
        // p 必须消耗 state, 否则永远失败  todo test
        static <R, E> Parsec2<R, E> NotFollowedBy(Parsec2<R, E> p) {
            return s -> {
                int t = s.begin();
                try {
                    p.parse(s);
                    throw s.trap("Not followed, stop at %d", s.index());
                } catch (ParsecException ignored) {
                    return null;
                } finally {
                    s.rollback(t);
                }
            };
        }

        static <R, L, E> Parsec2<List<R>, E> ManyTill(Parsec2<R, E> many, Parsec2<L, E> till) {
            return Choose(
                    till.map(it -> Lists.of()),
                    Seq(
                            many,
                            s -> ManyTill(many, till).parse(s),
                            Lists::prepend
                    )
            );
//            return s -> {
//                List<R> lst = new ArrayList<>();
//
//                Parsec2<R, E> tp = Try(many);
//                Parsec2<L, E> te = Try(till);
//
//                while (true) {
//                    try {
//                        te.parse(s);
//                        return unmodifiableList(lst);
//                    } catch (ParsecException e) {
//                        lst.add(tp.parse(s));
//                    }
//                }
//            };
        }

        static <R, E> Parsec2<R, E> Find(Parsec2<R, E> parsec) {
            return s -> {
                int marked = s.index();
                try {
                    while (true) {
                        int t = s.begin();
                        try {
                            R r = parsec.parse(s);
                            s.commit(t);
                            return r;
                        } catch (ParsecException ignored) {
                            s.rollback(t);
                            s.next();
                        }
                    }
                } catch (EOFException e) {
                    throw s.trap("Find from %s to end but failed", marked);
                }
            };
        }
    }


    @SuppressWarnings("Convert2MethodRef")
    interface CharParsers {
        Pattern patternInt          = Pattern.compile("-?(0|[1-9][0-9]*)");
        Pattern patternUInt         = Pattern.compile("(0|[1-9][0-9]*)");
        Pattern patternDecimal      = Pattern.compile("-?(0|[1-9][0-9]*)([.][0-9]+)?([eE][-+]?[0-9]+)?");
        Pattern patternUDecimal     = Pattern.compile("(0|[1-9][0-9]*)([.][0-9]+)?([eE][-+]?[0-9]+)?");


        static Parsec2<Character, Character> Ch(char value)     { return EQ(value); }
        static Parsec2<Character, Character> NotCh(char value)  { return NE(value); }
        static Parsec2<Character, Character> ChIn(String str)   { return s -> OneOf(Sequence.chars(str)).parse(s); }
        static Parsec2<Character, Character> ChNone(String str) { return s -> NoneOf(Sequence.chars(str)).parse(s); }

        Parsec2<Character, Character> Digit             = Satisfy(Character::isDigit);
        Parsec2<Character, Character> Alphabetic        = Satisfy(c -> isAlphabetic(c));
        Parsec2<Character, Character> Lower             = Satisfy(Character::isLowerCase);
        Parsec2<Character, Character> Upper             = Satisfy(Character::isUpperCase);
        Parsec2<Character, Character> Letter            = Satisfy(Character::isLetter);
        Parsec2<Character, Character> Space             = Satisfy(Character::isSpaceChar);
        Parsec2<Character, Character> Whitespace        = Satisfy(Character::isWhitespace);
        Parsec2<Character, Character> SkipSpaces        = Skip(Space);
        Parsec2<Character, Character> SkipWhiteSpaces   = Skip(Whitespace);

        Parsec2<String, Character> Int      = Pat(patternInt);
        Parsec2<String, Character> UInt     = Pat(patternUInt);
        Parsec2<String, Character> Decimal  = Pat(patternDecimal);
        Parsec2<String, Character> UDecimal = Pat(patternUDecimal);

        Parsec2<Character, Character> TAB   = Ch('\t');
        Parsec2<Character, Character> LF    = Ch('\n');
        Parsec2<Character, Character> CR    = Ch('\r');
        Parsec2<String, Character> CRLF     = Str("\r\n");
        Parsec2<String, Character> EOL      = Choose(CRLF, Str("\r"), Str("\n"));

        static Parsec2<String, Character> Str(String str) {
            return Pat(Pattern.quote(str));
        }
        static Parsec2<String, Character> Pat(String ptn) {
            return Regex(ptn).map(it -> it.group());
        }
        static <R> Parsec2<R, Character> Pat(String ptn, Function<String, R> mapper) {
            return Pat(ptn).map(mapper);
        }
        static Parsec2<String, Character> Pat(Pattern ptn) {
            return Regex(ptn).map(it -> it.group());
        }
        static Parsec2<MatchResult, Character> Regex(String ptn) {
            return Regex(Pattern.compile(ptn));
        }

        static Parsec2<MatchResult, Character> Regex(Pattern ptn){
            return s -> {
                int t = s.begin();
                try{
                    // String left = s.buf.subList(t, s.buf.size()).stream().map(String::valueOf).collect(joining());
                    assert s.ori instanceof String;
                    String left = ((String) s.ori).substring(t);
                    Matcher matcher = ptn.matcher(left);
                    if (matcher.lookingAt()) {
                        s.commit(t);
                        s.current += matcher.end();
                        // left.substring(0, matcher.end())
                        return matcher.toMatchResult();
                    } else {
                        throw s.trap("Expect %s but %s", ptn.toString(), left);
                    }
                } catch (ParsecException e) {
                    s.rollback(t);
                    throw e;
                }
            };
        }
//        static Parsec2<String, Character> Str(String str) {
//            return s -> { // hack 处理
//                assert s.ori instanceof String;
//                int idx = s.index();
//                if (((String) s.ori).startsWith(str, idx)) {
//                    s.index(idx + str.length());
//                } else {
//                    throw s.trap("Expect %s at %d", str, idx);
//                }
//                return str;
//            };
//            return s -> {
//                int idx = 0;
//                for(Character c: str.toCharArray()) {
//                    Character n = s.next();
//                    if (!c.equals(n)) {
//                        throw s.trap("Expect %c at %s[%d] but %c", c, str, idx, n);
//                    }
//                    idx++;
//                }
//                return str;
//            };
//        }
    }


    interface TriFunction<T1, T2, T3, R> {
        R apply(T1 fst, T2 sec, T3 trd);
    }

    interface BiOperator<E, O, R> {
        E val(R val);
        E app(O op, E lval, E rval);
    }

    class Lists {
        @SafeVarargs
        static <E> List<E> of(E... els) {
            if (els.length == 0) {
                return unmodifiableList(emptyList());
            } else if (els.length == 1) {
                return unmodifiableList(singletonList(els[0]));
            } else {
                List<E> lst = new ArrayList<>();
                addAll(lst, els);
                return unmodifiableList(lst);
            }
        }
        static <E> List<E> concat(List<E> l1, List<E> l2) {
            List<E> lst = new ArrayList<>(l2.size() + l1.size());
            lst.addAll(l1);
            lst.addAll(l2);
            return unmodifiableList(lst);
        }
        static <E> List<E> prepend(E el, List<E> l) {
            List<E> cp = new ArrayList<>(l.size() + 1);
            cp.add(el);
            cp.addAll(l);
            return unmodifiableList(cp);
        }
    }

    class Pair<A, D> {
        public final A car;
        public final D cdr;
        public Pair(A car, D cdr) { this.car = car; this.cdr = cdr; }
        @Override public String toString() { return "Pair(" + car + ", " + cdr + ")"; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Objects.equals(car, ((Pair<?, ?>) o).car) && Objects.equals(cdr, ((Pair<?, ?>) o).cdr);
        }
        @Override public int hashCode() { return Objects.hash(car, cdr); }
    }

    /**
     * State
     * @param <E>
     */
    @SuppressWarnings("unchecked")
    class Sequence<E> {
        public final Object ori; // hack for performance
        final List<E> buf;
        int current = 0;
        int tran = -1;

        static List<Character> chars(String s) {
            return range(0, s.length()).mapToObj(s::charAt).collect(toList());
        }

        public Sequence(String s) {
            ori = s;
            buf = (List<E>) chars(s);
        }
        public Sequence(List<E> items) {
            assert items instanceof RandomAccess;
            buf = items;
            this.ori = null;
        }
        public E next() throws EOFException {
            if (current >= buf.size()) {
                throw EOFException.EOF;
            }
            return buf.get(current++);
        }
        public int index() {
            return current;
        }
        public void index(int idx) {
            if (current > buf.size()) {
                throw EOFException.EOF;
            }
            current = idx;
        }
        public int begin() {
            if (tran == -1) {
                tran = current;
            }
            return current;
        }
        public void rollback(int t) {
            if (tran == t) {
                tran = -1;
            }
            current = t;
        }
        public void commit(int t) {
            if (tran == t) {
                tran = -1;
            }
        }
        public ParsecException trap(String fmt, Object ...args) {
            return new ParsecException(current, "at " + index() + " " + String.format(fmt, args));
        }
    }


    class EOFException extends ParsecException {
        static EOFException EOF = new EOFException();
        EOFException() { super(); }
    }

    class ParsecException extends RuntimeException {
        public final int current;
        ParsecException() {
            super(null, null, false, false);
            current = -1;
        }
        ParsecException(int current, String msg) {
            super(msg, null, DBG, DBG);
            this.current = current;
        }
    }

    boolean DBG = false;
}