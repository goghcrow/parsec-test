package xiao.playground.peg;

import java.util.Optional;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.Character.isAlphabetic;
import static java.util.Collections.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static xiao.playground.peg.Parsec4.Combinators.*;


/**
 * Parser Combinator
 * @author chuxiaofeng
 *
 * Parsec Demo <br>
 *
 * Parsec2 基础上从把 Seq<E> 变成不可变状态, 把 Choose 回溯的实现从异常替换成分支判断, 用来验证性能,
 * 可能结果性能严重变差，应该跟申请了大量小对象有关
 */
public interface Parsec4<R, E> {

    Result<R, E> parse(Sequence<E> s);

    default Result<R, E> parse(String s) {
        return parse(((Sequence<E>) new Sequence<>(chars(s))));
    }

    default <C> Parsec4<C, E> map(Function<R, C> mapper) {
        return s -> {
            Result<R, E> r = parse(s);
            if (r.succ) {
                return r.map(mapper);
            } else {
                return Result.fail(r);
            }
        };
    }

    default <C> Parsec4<C, E> flatMap(Function<R, Parsec4<C, E>> binder) {
        return s -> {
            Result<R, E> r = parse(s);
            if (r.succ) {
                return binder.apply(r.ret).parse(r.state);
            } else {
                return Result.fail(r);
            }
        };
    }


    default <C, RC> Parsec4<RC, E> seq(Parsec4<C, E> p, BiFunction<R, C, RC> mapper)
                                                                { return Seq(this, p, mapper); }
    default <C> Parsec4<C, E> then(Parsec4<C, E> p)             { return seq(p, (a, b) -> b); }
    default <C> Parsec4<R, E> over(Parsec4<C, E> p)             { return seq(p, (a, b) -> a); }
    default Parsec4<R, E> or(Parsec4<? extends R, E> p)         { return Choose(this, p); } // or | orElse | otherwise
    default Parsec4<R, E> peek()                                { return LookAhead(this); }
    default Parsec4<Optional<R>, E> optional()                  { return Optional(this); }
    default Parsec4<R, E> optional(R def)                       { return Option(this, def); }
    default Parsec4<List<R>, E> many()                          { return Many(this); }
    default Parsec4<List<R>, E> many1()                         { return Many1(this); }
    default <L> Parsec4<List<R>, E> manyTill(Parsec4<L, E> end) { return ManyTill(this, end); }
    default Parsec4<R, E> skip()                                { return Skip(this); }
    default Parsec4<R, E> skipMany()                            { return SkipMany(this); }
    default Parsec4<R, E> skipMany1()                           { return SkipMany1(this); }
    default Parsec4<List<R>, E> count(int n)                    { return Count(this, n); }
    default <S> Parsec4<List<R>, E> sepBy(Parsec4<S, E> by)     { return SepBy(this, by); }
    default <S> Parsec4<List<R>, E> sepBy1(Parsec4<S, E> by)    { return SepBy1(this, by); }
    default <S> Parsec4<List<R>, E> endBy(Parsec4<S, E> by)     { return EndBy(this, by); }
    default <S> Parsec4<List<R>, E> endBy1(Parsec4<S, E> by)    { return EndBy1(this, by); }
    default <S> Parsec4<List<R>, E> sepEndBy(Parsec4<S, E> by)  { return SepEndBy(this, by); }
    default <S> Parsec4<List<R>, E> sepEndBy1(Parsec4<S, E> by) { return SepEndBy1(this, by); }
    default <S> Parsec4<R, E> notFollowedBy()                   { return NotFollowedBy(this); }
    default <O, C> Parsec4<R, E> between(Parsec4<O, E> open, Parsec4<C, E> close)
                                                                { return Between(open, close, this); }

    default <O, Expr> Parsec4<Expr, E> chainl(Parsec4<O, E> op,
                                              Expr def,
                                              BiOperator<Expr, O, R> alg) { return Chainl(this, op, def, alg); }
    default <O, Expr> Parsec4<Expr, E> chainl1(Parsec4<O, E> op,
                                               BiOperator<Expr, O, R> alg) { return Chainl1(this, op, alg); }
    default <O, Expr> Parsec4<Expr, E> chainr(Parsec4<O, E> op,
                                              Expr def,
                                              BiOperator<Expr, O, R> alg) { return Chainr(this, op, def, alg); }
    default <O, Expr> Parsec4<Expr, E> chainr1(Parsec4<O, E> op,
                                               BiOperator<Expr, O, R> alg) { return Chainr1(this, op, alg); }


    interface Combinators {

        static <R1, R2, R, E> Parsec4<R, E> Seq(
                Parsec4<R1, E> front,
                Parsec4<R2, E> rear,
                BiFunction<R1, R2, R> mapper) {
            return s -> {
                Result<R1, E> r1 = front.parse(s);
                if (r1.succ) {
                    Result<R2, E> r2 = rear.parse(r1.state);
                    if (r2.succ) {
                        return Result.succ(r2.state, mapper.apply(r1.ret, r2.ret));
                    } else {
                        return Result.fail(r2);
                    }
                } else {
                    return Result.fail(r1);
                }
            };
        }

        static <R1, R2, R3, R, E> Parsec4<R, E> Seq(
                Parsec4<R1, E> fst,
                Parsec4<R2, E> sec,
                Parsec4<R3, E> trd,
                TriFunction<R1, R2, R3, R> mapper) {
            return s -> {
                Result<R1, E> r1 = fst.parse(s);
                if (r1.succ) {
                    Result<R2, E> r2 = sec.parse(r1.state);
                    if (r2.succ) {
                        Result<R3, E> r3 = trd.parse(r2.state);
                        if (r3.succ) {
                            return Result.succ(r3.state, mapper.apply(r1.ret, r2.ret, r3.ret));
                        } else {
                            return Result.fail(r3);
                        }
                    } else {
                        return Result.fail(r2);
                    }
                } else {
                    return Result.fail(r1);
                }
            };
        }

        // Choice
        @SafeVarargs
        static <R, E> Parsec4<R, E> Choose(Parsec4<? extends R, E>... ps) {
            if (ps.length == 0) {
                throw new IllegalArgumentException("No Choices");
            }
            return s -> {
                List<Result<?, E>> causes = new ArrayList<>(ps.length);
                for (Parsec4<? extends R, E> parsec : ps) {
                    Result<? extends R, E> r = parsec.parse(s);
                    if (r.succ) {
                        // return r;
                        return new Result<>(r.succ, r.state, r.ret, r.causes);
                    } else {
                        causes.add(r);
                    }
                }
                return Result.fail(s, causes);
            };
        }

        // ============================================================================


        final class TypeRef<T> {
            final static TypeRef<Character> Char = new TypeRef<>();
        }

        static <E> Parsec4<E, E> Any()                { return Sequence::next; }
        static <E> Parsec4<E, E> Any(TypeRef<E> t)    { return Sequence::next; }
        static <E> Parsec4<E, E> EQ(E item)           { return Satisfy(e -> Objects.equals(e, item)); }
        static <E> Parsec4<E, E> NE(E item)           { return Satisfy(e -> !Objects.equals(e, item)); }

        static <E> Parsec4<E, E> OneOf(List<E> items) {
            HashSet<E> es = new HashSet<>(items);
            return Satisfy(es::contains);
        }

        static <E> Parsec4<E, E> NoneOf(List<E> items) {
            HashSet<E> es = new HashSet<>(items);
            return Satisfy(e -> !es.contains(e));
        }

        static <E> Parsec4<E, E> Satisfy(Predicate<E> p/*, String expect, Object... args*/) {
            return s -> {
                Result<E, E> r = s.next();
                if (p.test(r.ret)) {
                    return r;
                } else {
                    return Result.fail(r);
                }
            };
        }

        static <T, E> Parsec4<T, E> Return(T value) {
            return s -> Result.succ(s, value);
        }

        static <T, E> Parsec4<T, E> Return(T value, TypeRef<E> t) {
            return s -> Result.succ(s, value);
        }

        static <E> Parsec4<E, E> Fail(String fmt, Object... objects) {
            return s -> Result.fail(s, Lists.of()); // todo
        }

        static <R, E> Parsec4<R, E> Null() {
            return s -> Result.succ(s, null);
        }

        static <R, E> Parsec4<Optional<R>, E> Optional(Parsec4<R, E> p) {
            return Choose(p.map(Optional::of), Return(Optional.empty()));
        }

        static <R, E> Parsec4<R, E> Option(Parsec4<R, E> p, R def) {
            return Choose(p, Return(def));
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec4<List<R>, E> Many(Parsec4<R, E> p) {
            return s -> {
                List<R> lst = new ArrayList<>();
                while (true) {
                    Result<R, E> r = p.parse(s);
                    if (r.succ) {
                        lst.add(r.ret);
                        s = r.state;
                    } else {
                        return Result.succ(s, unmodifiableList(lst));
                    }
                }
            };
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec4<List<R>, E> Many1(Parsec4<R, E> p) {
            return Seq(p.map(Lists::of), Many(p), Lists::concat);
        }

        static <R, E> Parsec4<R, E> Skip(Parsec4<R, E> p) {
            return p.map(it -> null);
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec4<R, E> SkipMany(Parsec4<R, E> p) {
            return Many(p).map(it -> null);
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R, E> Parsec4<R, E> SkipMany1(Parsec4<R, E> p) {
            return Many1(p).map(it -> null);
        }

        static <R, E> Parsec4<List<R>, E> Count(Parsec4<R, E> p, int n) {
            if (n <= 0) {
                return Null();
            }
            return s -> {
                List<R> lst = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    Result<R, E> r = p.parse(s);
                    if (r.succ) {
                        lst.add(r.ret);
                        s = r.state;
                    } else {
                        return Result.fail(r);
                    }
                }
                return Result.succ(s, unmodifiableList(lst));
            };
        }

        static <R, E, O, C> Parsec4<R, E> Between(Parsec4<O, E> open, Parsec4<C, E> close, Parsec4<R, E> p) {
            return open.then(p).over(close);
        }

        // 另一种写法
        static <R, S, E> Parsec4<List<R>, E> SepBy(Parsec4<R, E> p, Parsec4<S, E> by, boolean optEndedSep) {
            return Option(SepBy1(p, by, optEndedSep), Lists.of());
        }
        static <R, S, E> Parsec4<List<R>, E> SepBy1(Parsec4<R, E> p, Parsec4<S, E> by, boolean optEndedSep) {
            Parsec4<List<R>, E> byp = Many(by.then(p));
            if (optEndedSep) {
                byp = byp.over(Optional(by));
            }
            return Seq(p, byp, Lists::prepend);
        }

        static <R, S, E> Parsec4<List<R>, E> SepBy(Parsec4<R, E> p, Parsec4<S, E> by) {
            return SepBy(p, by, false);
        }

        static <R, S, E> Parsec4<List<R>, E> SepBy1(Parsec4<R, E> p, Parsec4<S, E> by) {
            return SepBy1(p, by, false);
        }

        static <R, S, E> Parsec4<List<R>, E> EndBy(Parsec4<R, E> p, Parsec4<S, E> by) {
            return Many(p.over(by));
        }

        static <R, S, E> Parsec4<List<R>, E> EndBy1(Parsec4<R, E> p, Parsec4<S, E> by) {
            return Many1(p.over(by));
        }

        static <R, S, E> Parsec4<List<R>, E> SepEndBy(Parsec4<R, E> p, Parsec4<S, E> by) {
            return SepBy(p, by, true);
        }
        static <R, S, E> Parsec4<List<R>, E> SepEndBy1(Parsec4<R, E> p, Parsec4<S, E> by) {
            return SepBy1(p, by, true);
        }

        // 构造左结合双目运算符解析
        static <R, O, E, Expr> Parsec4<Expr, E> Chainl(
                Parsec4<R, E> p,
                Parsec4<O, E> op,
                Expr def,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(Chainl1(p, op, alg), def);
        }

        static <R, O, E, Expr> Parsec4<Expr, E> Chainl1(
                Parsec4<R, E> p,
                Parsec4<O, E> op,
                BiOperator<Expr, O, R> alg
        ) {
            return p.map(alg::val).flatMap(lval -> s -> {
                return chainl1_rest(p, op, lval, alg).parse(s);
            });
        }

        static <R, O, E, Expr> Parsec4<Expr, E> chainl1_rest(
                Parsec4<R, E> p,
                Parsec4<O, E> op,
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
        static <R, O, E, Expr> Parsec4<Expr, E> Chainr(
                Parsec4<R, E> p,
                Parsec4<O, E> op,
                Expr def,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(Chainr1(p, op, alg), def);
        }

        static <R, O, E, Expr> Parsec4<Expr, E> Chainr1(
                Parsec4<R, E> p,
                Parsec4<O, E> op,
                BiOperator<Expr, O, R> alg
        ) {
            return p.map(alg::val).flatMap(lval -> s -> {
                return chainr1_rest(p, op, lval, alg).parse(s);
            });
        }

        static <R, O, E, Expr> Parsec4<Expr, E> chainr1_rest(
                Parsec4<R, E> p,
                Parsec4<O, E> op,
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

        static <E> Parsec4<E, E> EOF() {
            return s -> {
                Result<E, E> r = s.next();
                if (r.succ) {
                    return Result.fail(r.state, "EOF");
                } else {
                    assert r.state.buf.isEmpty();
                    return Result.succ(r.state, null);
                }
            };
        }

        static <R, E> Parsec4<R, E> LookAhead(Parsec4<R, E> p) {
            return s -> {
                Result<R, E> r = p.parse(s);
                return new Result<>(r.succ, s, r.ret, r.causes);
            };
        }

        // 实现最长匹配规则, e.g. 识别关键词let, lets 应该识别为 id 不是 let
        // p 必须消耗 state, 否则永远失败
        static <R, E> Parsec4<R, E> NotFollowedBy(Parsec4<R, E> p) {
            return s -> {
                Result<R, E> r = p.parse(s);
                if (r.succ) {
                    return Result.fail(s, "Not followed");
                } else {
                    return Result.succ(s, null);
                }
            };
        }

        static <R, L, E> Parsec4<List<R>, E> ManyTill(Parsec4<R, E> many, Parsec4<L, E> till) {
            return Choose(
                    till.map(it -> Lists.of()),
                    Seq(
                            many,
                            s -> ManyTill(many, till).parse(s),
                            Lists::prepend
                    )
            );
        }

        static <R, E> Parsec4<R, E> Find(Parsec4<R, E> parsec) {
            return s -> {
                while (true) {
                    Result<R, E> r = parsec.parse(s);
                    if (r.succ) {
                        return r;
                    } else {
                        Result<E, E> r1 = s.next();
                        if (r1.succ) {
                            s = r1.state;
                        } else {
                            return Result.fail(r1);
                        }
                    }
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


        static Parsec4<Character, Character> Ch(char value)     { return EQ(value); }
        static Parsec4<Character, Character> NotCh(char value)  { return NE(value); }
        static Parsec4<Character, Character> ChIn(String str)   { return s -> OneOf(chars(str)).parse(s); }
        static Parsec4<Character, Character> ChNone(String str) { return s -> NoneOf(chars(str)).parse(s); }

        Parsec4<Character, Character> Digit             = Satisfy(Character::isDigit);
        Parsec4<Character, Character> Alphabetic        = Satisfy(c -> isAlphabetic(c));
        Parsec4<Character, Character> Lower             = Satisfy(Character::isLowerCase);
        Parsec4<Character, Character> Upper             = Satisfy(Character::isUpperCase);
        Parsec4<Character, Character> Letter            = Satisfy(Character::isLetter);
        Parsec4<Character, Character> Space             = Satisfy(Character::isSpaceChar);
        Parsec4<Character, Character> Whitespace        = Satisfy(Character::isWhitespace);
        Parsec4<Character, Character> SkipSpaces        = Skip(Space);
        Parsec4<Character, Character> SkipWhiteSpaces   = Skip(Whitespace);

        Parsec4<String, Character> Int      = Pat(patternInt);
        Parsec4<String, Character> UInt     = Pat(patternUInt);
        Parsec4<String, Character> Decimal  = Pat(patternDecimal);
        Parsec4<String, Character> UDecimal = Pat(patternUDecimal);

        Parsec4<Character, Character> TAB   = Ch('\t');
        Parsec4<Character, Character> LF    = Ch('\n');
        Parsec4<Character, Character> CR    = Ch('\r');
        Parsec4<String, Character> CRLF     = Str("\r\n");
        Parsec4<String, Character> EOL      = Choose(CRLF, Str("\r"), Str("\n"));

        static Parsec4<String, Character> Str(String str) {
            return Pat(Pattern.quote(str));
        }
        static Parsec4<String, Character> Pat(String ptn) {
            return Regex(ptn).map(it -> it.group());
        }
        static <R> Parsec4<R, Character> Pat(String ptn, Function<String, R> mapper) {
            return Pat(ptn).map(mapper);
        }
        static Parsec4<String, Character> Pat(Pattern ptn) {
            return Regex(ptn).map(it -> it.group());
        }
        static Parsec4<MatchResult, Character> Regex(String ptn) {
            return Regex(Pattern.compile(ptn));
        }

        static Parsec4<MatchResult, Character> Regex(Pattern ptn){
            return s -> {
                String left = s.buf.stream().map(String::valueOf).collect(joining());
                Matcher matcher = ptn.matcher(left);
                if (matcher.lookingAt()) {
                    // left.substring(0, matcher.end())
                    // return matcher.toMatchResult();
                    return Result.succ(new Sequence<>(chars(left.substring(matcher.end()))), matcher.toMatchResult());
                } else {
                    return Result.fail(s, "expected " + ptn);
                }
            };
        }
// todo test
//        static Parsec4<String, Character> Str(String str) {
//            return s -> {
//                for(Character c: str.toCharArray()) {
//                    Result<Character, Character> r = s.next();
//                    if (r.succ) {
//                        if (!c.equals(r.ret)) {
//                            return Result.fail(s, "expected " + str);
//                        }
//                    } else {
//                        return r;
//                    }
//                }
//                return Result.succ(new Sequence<>(s.buf.subList(str.length(), s.buf.size())), str);
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
     * Parse Result
     * @param <R>
     * @param <E>
     */
    class Result<R, E> {
        public final boolean succ;
        public final Sequence<E> state;
        public final R ret;
        public final Object causes; // String | List<Result<?, E>>
        public static <R, E> Result<R, E> succ(Sequence<E> state, R ret) {
            return new Result<>(true, state, ret, null);
        }
        // todo err msg
        public static <R, E> Result<R, E> fail(Result<?, E> cause) {
            return new Result<>(false, cause.state, null, cause);
        }
        // todo err msg
        public static <R, E> Result<R, E> fail(Sequence<E> state, Object causes) {
            return new Result<>(false, state, null, causes);
        }
        public Result(boolean succ, Sequence<E> state, R ret, Object causes) {
            this.succ = succ;
            this.state = state;
            this.ret = ret;
            this.causes = causes;
        }
        public <C> Result<C, E> map(Function<R, C> mapper) {
            assert succ;
            return new Result<>(succ, state, mapper.apply(ret), causes);
        }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Objects.equals(state, ((Result<?, ?>) o).state) && Objects.equals(ret, ((Result<?, ?>) o).ret);
        }
        @Override public int hashCode() { return Objects.hash(state, ret); }
    }

    /**
     * State
     * @param <E>
     */
    class Sequence<E> {
        final List<E> buf;
        public Sequence(List<E> items) {
            buf = unmodifiableList(items);
        }
        public Result<E, E> next() {
            if (buf.isEmpty()) {
                return Result.fail(this, Lists.of()); // EOF
            } else {
                return Result.succ(new Sequence<>(buf.subList(1, buf.size())), buf.get(0));
            }
        }
    }

    static List<Character> chars(String s) {
        return range(0, s.length()).mapToObj(s::charAt).collect(toList());
    }
}