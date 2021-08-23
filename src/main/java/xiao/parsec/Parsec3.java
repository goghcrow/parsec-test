package xiao.parsec;

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
import static java.util.stream.Collectors.toList;
import static java.util.stream.IntStream.range;
import static xiao.parsec.Parsec3.Combinators.*;


/**
 * Parser Combinator
 * @author chuxiaofeng
 *
 * Parsec Demo <br>
 *
 * Parsec2 基础上从 Seq<E> 特化成处理 String 的版本, 用来验证性能变化, 结果没啥变化
 */
public interface Parsec3<R> {

    R parse(Sequence s) throws ParsecException;

    default R parse(String s) {
        return parse(new Sequence(s));
    }

    default <C> Parsec3<C> map(Function<R, C> mapper) {
        return s -> mapper.apply(parse(s));
    }

    default <C> Parsec3<C> flatMap(Function<R, Parsec3<C>> binder) {
        return s -> binder.apply(parse(s)).parse(s);
    }


    default <C, RC> Parsec3<RC> seq(Parsec3<C> p, BiFunction<R, C, RC> mapper)
                                                          { return Seq(this, p, mapper); }
    default <C> Parsec3<C> then(Parsec3<C> p)             { return seq(p, (a, b) -> b); }
    default <C> Parsec3<R> over(Parsec3<C> p)             { return seq(p, (a, b) -> a); }
    default Parsec3<R> or(Parsec3<? extends R> p)         { return Choose(this, p); } // or | orElse | otherwise
    default Parsec3<R> try1()                             { return Try(this); }
    default Parsec3<R> peek()                             { return LookAhead(this); }
    default Parsec3<Optional<R>> optional()               { return Optional(this); }
    default Parsec3<R> optional(R def)                    { return Option(this, def); }
    default Parsec3<List<R>> many()                       { return Many(this); }
    default Parsec3<List<R>> many1()                      { return Many1(this); }
    default <L> Parsec3<List<R>> manyTill(Parsec3<L> end) { return ManyTill(this, end); }
    default Parsec3<R> skip()                             { return Skip(this); }
    default Parsec3<R> skipMany()                         { return SkipMany(this); }
    default Parsec3<R> skipMany1()                        { return SkipMany1(this); }
    default Parsec3<List<R>> count(int n)                 { return Count(this, n); }
    default <S> Parsec3<List<R>> sepBy(Parsec3<S> by)     { return SepBy(this, by); }
    default <S> Parsec3<List<R>> sepBy1(Parsec3<S> by)    { return SepBy1(this, by); }
    default <S> Parsec3<List<R>> endBy(Parsec3<S> by)     { return EndBy(this, by); }
    default <S> Parsec3<List<R>> endBy1(Parsec3<S> by)    { return EndBy1(this, by); }
    default <S> Parsec3<List<R>> sepEndBy(Parsec3<S> by)  { return SepEndBy(this, by); }
    default <S> Parsec3<List<R>> sepEndBy1(Parsec3<S> by) { return SepEndBy1(this, by); }
    default <S> Parsec3<R> notFollowedBy()                { return NotFollowedBy(this); }
    default <O, C> Parsec3<R> between(Parsec3<O> open, Parsec3<C> close)
                                                          { return Between(open, close, this); }

    default <O, Expr> Parsec3<Expr> chainl(Parsec3<O> op,
                                           Expr def,
                                           BiOperator<Expr, O, R> alg) { return Chainl(this, op, def, alg); }
    default <O, Expr> Parsec3<Expr> chainl1(Parsec3<O> op,
                                            BiOperator<Expr, O, R> alg) { return Chainl1(this, op, alg); }
    default <O, Expr> Parsec3<Expr> chainr(Parsec3<O> op,
                                           Expr def,
                                           BiOperator<Expr, O, R> alg) { return Chainr(this, op, def, alg); }
    default <O, Expr> Parsec3<Expr> chainr1(Parsec3<O> op,
                                            BiOperator<Expr, O, R> alg) { return Chainr1(this, op, alg); }


    @SuppressWarnings("InfiniteLoopStatement")
    interface Combinators {

        static <R1, R2, R, E> Parsec3<R> Seq(
                Parsec3<R1> front,
                Parsec3<R2> rear,
                BiFunction<R1, R2, R> mapper) {
            return s -> mapper.apply(front.parse(s), rear.parse(s));
        }

        static <R1, R2, R3, R> Parsec3<R> Seq(
                Parsec3<R1> fst,
                Parsec3<R2> sec,
                Parsec3<R3> trd,
                TriFunction<R1, R2, R3, R> mapper) {
            return s -> mapper.apply(fst.parse(s), sec.parse(s), trd.parse(s));
        }

        // Choice
        @SafeVarargs
        static <R> Parsec3<R> Choose(Parsec3<? extends R>... ps) {
            if (ps.length == 0) {
                throw new IllegalArgumentException("No Choices");
            }
            return s -> {
                for (Parsec3<? extends R> parsec : ps) {
                    try {
                        return Try(parsec).parse(s);
                    } catch (ParsecException ignored) { }
                }
                throw s.trap("No Choice, stop at %d", s.index());
            };
        }

//        static <R, E> Parsec3<R> Choose(Parsec3<? extends R>... ps) {
//            return s -> {
//                int marked = s.index();
//                for (Parsec3<? extends R> parsec : ps) {
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
        static <R, E> Parsec3<R> Try(Parsec3<R> parsec) {
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


        static Parsec3<Character> Any()            { return Sequence::next; }
        static Parsec3<Character> EQ(Character item)           { return Satisfy(e -> Objects.equals(e, item)); }
        static Parsec3<Character> NE(Character item)           { return Satisfy(e -> !Objects.equals(e, item)); }

        static Parsec3<Character> OneOf(List<Character> items) {
            HashSet<Character> es = new HashSet<>(items);
            return Satisfy(es::contains);
        }

        static Parsec3<Character> NoneOf(List<Character> items) {
            HashSet<Character> es = new HashSet<>(items);
            return Satisfy(e -> !es.contains(e));
        }

        static Parsec3<Character> Satisfy(Predicate<Character> p/*, String expect, Object... args*/) {
            return s -> {
                int idx = s.begin();
                Character e = s.next();
                if (p.test(e)) {
                    return e;
                } else {
                    throw s.trap("Not satisfy at %d: %s", idx, Objects.toString(e));
                }
            };
        }

        static <T> Parsec3<T> Return(T value) {
            return s -> value;
        }

        static <E> Parsec3<E> Fail(String fmt, Object... objects) {
            return s -> {
                throw s.trap(fmt, objects);
            };
        }

        static <R, E> Parsec3<R> Null() {
            return s -> null;
        }

        static <R> Parsec3<Optional<R>> Optional(Parsec3<R> p) {
            return Choose(p.map(Optional::of), Return(Optional.empty()));
//            return s -> {
//                try{
//                    return Optional.of(Try(p).parse(s));
//                } catch (ParsecException ignored) { }
//                return Optional.empty();
//            };
        }

        static <R> Parsec3<R> Option(Parsec3<R> p, R def) {
            return Choose(p, Return(def));
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R> Parsec3<List<R>> Many(Parsec3<R> p) {
            return s -> {
                List<R> lst = new ArrayList<>();
                Parsec3<R> tp = Try(p);

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
        static <R> Parsec3<List<R>> Many1(Parsec3<R> p) {
            return Seq(p.map(Lists::of), Many(p), Lists::concat);
//            return s -> {
//                List<R> lst = new ArrayList<>();
//
//                lst.add(p.parse(s));
//                Parsec3<R> p = Try(p);
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

        static <R> Parsec3<R> Skip(Parsec3<R> p) {
            return p.map(it -> null);
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R> Parsec3<R> SkipMany(Parsec3<R> p) {
            return Many(p).map(it -> null);
        }

        // p 如果不消耗 state 会 stackoverflow
        static <R> Parsec3<R> SkipMany1(Parsec3<R> p) {
            return Many1(p).map(it -> null);
        }

//        static <R> Parsec3<R> Skip(Parsec3<R> p) {
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
//        static <R> Parsec3<R> Skip1(Parsec3<R> p) {
//            return s -> {
//                p.parse(s);
//                Skip(p).parse(s);
//                return null;
//            };
//        }

        static <R> Parsec3<List<R>> Count(Parsec3<R> p, int n) {
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

        static <R, O, C> Parsec3<R> Between(Parsec3<O> open, Parsec3<C> close, Parsec3<R> p) {
//            return s -> {
//                open.parse(s);
//                R r = p.parse(s);
//                close.parse(s);
//                return r;
//            };
            return open.then(p).over(close);
        }

        // 另一种写法
        static <R, S> Parsec3<List<R>> SepBy(Parsec3<R> p, Parsec3<S> by, boolean optEndedSep) {
            return Option(SepBy1(p, by, optEndedSep), Lists.of());
        }
        static <R, S> Parsec3<List<R>> SepBy1(Parsec3<R> p, Parsec3<S> by, boolean optEndedSep) {
            Parsec3<List<R>> byp = Many(by.then(p));
            if (optEndedSep) {
                byp = byp.over(Optional(by));
            }
            return Seq(p, byp, Lists::prepend);
        }

        static <R, S> Parsec3<List<R>> SepBy(Parsec3<R> p, Parsec3<S> by) {
            return SepBy(p, by, false);
//            return Option(SepBy1(p, by), Lists.of());
//            return s -> {
//                List<R> lst = new ArrayList<>();
//
//                Parsec3<R> tp = Try(p);
//                Parsec3<S> tb = Try(by);
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

        static <R, S> Parsec3<List<R>> SepBy1(Parsec3<R> p, Parsec3<S> by) {
            return SepBy1(p, by, false);
//            return Seq(p, Many(by.then(p)), Lists::prepend);
//            return s -> {
//                List<R> lst = new ArrayList<>();
//                lst.add(p.parse(s));
//
//                Parsec3<R> tp = Try(p);
//                Parsec3<S> tb = Try(by);
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

        static <R, S> Parsec3<List<R>> EndBy(Parsec3<R> p, Parsec3<S> by) {
            return Many(p.over(by));
        }

        static <R, S> Parsec3<List<R>> EndBy1(Parsec3<R> p, Parsec3<S> by) {
            return Many1(p.over(by));
        }

        static <R, S> Parsec3<List<R>> SepEndBy(Parsec3<R> p, Parsec3<S> by) {
            return SepBy(p, by, true);
        }
        static <R, S> Parsec3<List<R>> SepEndBy1(Parsec3<R> p, Parsec3<S> by) {
            return SepBy1(p, by, true);
        }

        // 构造左结合双目运算符解析
        static <R, O, Expr> Parsec3<Expr> Chainl(
                Parsec3<R> p,
                Parsec3<O> op,
                Expr def,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(Chainl1(p, op, alg), def);
        }

        static <R, O, Expr> Parsec3<Expr> Chainl1(
                Parsec3<R> p,
                Parsec3<O> op,
                BiOperator<Expr, O, R> alg
        ) {
            return p.map(alg::val).flatMap(lval -> s -> {
                return chainl1_rest(p, op, lval, alg).parse(s);
            });
        }

        static <R, O, E, Expr> Parsec3<Expr> chainl1_rest(
                Parsec3<R> p,
                Parsec3<O> op,
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
        static <R, O, Expr> Parsec3<Expr> Chainr(
                Parsec3<R> p,
                Parsec3<O> op,
                Expr def,
                BiOperator<Expr, O, R> alg
        ) {
            return Option(Chainr1(p, op, alg), def);
        }

        static <R, O, Expr> Parsec3<Expr> Chainr1(
                Parsec3<R> p,
                Parsec3<O> op,
                BiOperator<Expr, O, R> alg
        ) {
            return p.map(alg::val).flatMap(lval -> s -> {
                return chainr1_rest(p, op, lval, alg).parse(s);
            });
        }

        static <R, O, Expr> Parsec3<Expr> chainr1_rest(
                Parsec3<R> p,
                Parsec3<O> op,
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

        static <E> Parsec3<E> EOF() {
            return s -> {
                try {
                    Character r = s.next();
                    throw s.trap("Expect eof but %s", r);
                } catch (EOFException e) {
                    return null;
                }
            };
        }

        static <R> Parsec3<R> LookAhead(Parsec3<R> p) {
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
        // p 必须消耗 state, 否则永远失败
        static <R, E> Parsec3<R> NotFollowedBy(Parsec3<R> p) {
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

        static <R, L> Parsec3<List<R>> ManyTill(Parsec3<R> many, Parsec3<L> till) {
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
//                Parsec3<R> tp = Try(many);
//                Parsec3<L> te = Try(till);
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

        static <R> Parsec3<R> Find(Parsec3<R> parsec) {
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


        static Parsec3<Character> Ch(char value)     { return EQ(value); }
        static Parsec3<Character> NotCh(char value)  { return NE(value); }
        static Parsec3<Character> ChIn(String str)   { return s -> OneOf(Sequence.chars(str)).parse(s); }
        static Parsec3<Character> ChNone(String str) { return s -> NoneOf(Sequence.chars(str)).parse(s); }

        Parsec3<Character> Digit             = Satisfy(Character::isDigit);
        Parsec3<Character> Alphabetic        = Satisfy(c -> isAlphabetic(c));
        Parsec3<Character> Lower             = Satisfy(Character::isLowerCase);
        Parsec3<Character> Upper             = Satisfy(Character::isUpperCase);
        Parsec3<Character> Letter            = Satisfy(Character::isLetter);
        Parsec3<Character> Space             = Satisfy(Character::isSpaceChar);
        Parsec3<Character> Whitespace        = Satisfy(Character::isWhitespace);
        Parsec3<Character> SkipSpaces        = Skip(Space);
        Parsec3<Character> SkipWhiteSpaces   = Skip(Whitespace);

        Parsec3<String> Int      = Pat(patternInt);
        Parsec3<String> UInt     = Pat(patternUInt);
        Parsec3<String> Decimal  = Pat(patternDecimal);
        Parsec3<String> UDecimal = Pat(patternUDecimal);

        Parsec3<Character> TAB   = Ch('\t');
        Parsec3<Character> LF    = Ch('\n');
        Parsec3<Character> CR    = Ch('\r');
        Parsec3<String> CRLF     = Str("\r\n");
        Parsec3<String> EOL      = Choose(CRLF, Str("\r"), Str("\n"));

        static Parsec3<String> Str(String str) {
            return Pat(Pattern.quote(str));
        }
        static Parsec3<String> Pat(String ptn) {
            return Regex(ptn).map(it -> it.group());
        }
        static <R> Parsec3<R> Pat(String ptn, Function<String, R> mapper) {
            return Pat(ptn).map(mapper);
        }
        static Parsec3<String> Pat(Pattern ptn) {
            return Regex(ptn).map(it -> it.group());
        }
        static Parsec3<MatchResult> Regex(String ptn) {
            return Regex(Pattern.compile(ptn));
        }

        static Parsec3<MatchResult> Regex(Pattern ptn){
            return s -> {
                int t = s.begin();
                try{
                    String left = s.s.substring(t);
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
//        static Parsec3<String> Str(String str) {
//            return s -> { // hack 处理
//                int idx = s.index();
//                if (s.s.startsWith(str, idx)) {
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
     */
    class Sequence {
        final String s;
        int current = 0;
        int tran = -1;

        static List<Character> chars(String s) {
            return range(0, s.length()).mapToObj(s::charAt).collect(toList());
        }

        public Sequence(String s) {
            this.s = s;
        }
        public Character next() throws EOFException {
            if (current >= s.length()) {
                throw EOFException.EOF;
            }
            return s.charAt(current++);
        }
        public int index() {
            return current;
        }
        public void index(int idx) {
            if (current > s.length()) {
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