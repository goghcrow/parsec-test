package xiao.playground.peg;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser Expression Grammar
 * 限制：无法处理左递归
 * > PEG 本质上是将递归下降分析器（Recursive descent parser）中控制流的一般模式抽象出来，然后用近似 BNF 的方法表示之
 */
public interface PEG {
    // 演示 1. 控制流抽象(CPS风格) 2. 将 Rule 对象抽象成 方法 3. 将数据对象抽象成构造器

    interface Result { }

    class Str implements Result {
        final String s;
        public Str(String s) { this.s = s; }
        @Override public String toString() { return "\"" + s + "\""; }
    }
    class Pair implements Result {
        final Result car;
        final Result cdr;
        Pair(Result car, Result cdr) { this.car = car; this.cdr = cdr; }
        @Override public String toString() { return "Pair(" + car + ", " + cdr + ")"; }
    }

    interface Cont {
        void apply(String state, Result result);
    }

    interface Rule {
        void match(String state, Cont onMatch, Cont onFail);
    }


    interface V1 {
        class FinalRule implements Rule {
            final String pattern;
            FinalRule(String pattern) {
                this.pattern = pattern;
            }
            @Override
            public void match(String state, Cont onMatch, Cont onFail) {
                if (state.startsWith(pattern)) {
                    onMatch.apply(state.substring(pattern.length()), new Str(pattern));
                } else {
                    onFail.apply(state, new Str(pattern));
                }
            }
        }
        class SequenceRule implements Rule {
            Rule front;
            Rule rear;
            SequenceRule(Rule front, Rule rear) {
                this.front = front;
                this.rear = rear;
            }
            @Override
            public void match(String state, Cont onMatch, Cont onFail) {
                front.match(state, (s1, r1) ->
                                rear.match(s1, (s2, r2) ->
                                        onMatch.apply(s2, new Pair(r1, r2)), onFail), onFail);
            }
        }
        class ChooseRule implements Rule {
            Rule superior;
            Rule inferior;
            ChooseRule(Rule superior, Rule inferior) {
                this.superior = superior;
                this.inferior = inferior;
            }
            @Override
            public void match(String state, Cont onMatch, Cont onFail) {
                superior.match(state, onMatch, (s2, r2) ->
                        inferior.match(state, onMatch, onFail));
            }
        }

        static FinalRule Pat(String ptn) { return new FinalRule(ptn); }
        static SequenceRule Seq(Rule front, Rule rear) { return new SequenceRule(front, rear); }
        static ChooseRule Choose(Rule superior, Rule inferior) { return new ChooseRule(superior, inferior); }
    }

    interface V2 {
        static Rule Pat(String pattern) {
            return (state, onMatch, onFail) -> {
                if (state.startsWith(pattern)) {
                    onMatch.apply(state.substring(pattern.length()), new Str(pattern));
                } else {
                    onFail.apply(state, new Str(pattern));
                }
            };
        }
        static Rule Seq(Rule front, Rule rear) {
            return (state, onMatch, onFail) ->
                    front.match(state, (s1, r1) ->
                            rear.match(s1, (s2, r2) ->
                                    onMatch.apply(s2, new Pair(r1, r2)), onFail), onFail);
        }
        static Rule Choose(Rule superior, Rule inferior) {
            return (state, onMatch, onFail) ->
                    superior.match(state, onMatch, (s2, r2) ->
                            inferior.match(state, onMatch, onFail));
        }
    }


    interface V3 {
        static Rule Pat(String pattern, Function<String, Result> mapper) {
            return (state, onMatch, onFail) -> {
                if (state.startsWith(pattern)) {
                    onMatch.apply(state.substring(pattern.length()), mapper.apply(pattern));
                } else {
                    onFail.apply(state, new Str(pattern));
                }
            };
        }
        static Rule Seq(Rule front, Rule rear, BiFunction<Result, Result, Result> mapper) {
            return (state, onMatch, onFail) ->
                    front.match(state, (s1, r1) ->
                            rear.match(s1, (s2, r2) ->
                                    // 这里用 BiFunc 的好处
                                    // 1. 抽象掉对具体数据结构的依赖,
                                    // 2. 不见得 r1, r2 两个结果都要, 比如 r1, r2 -> r1, skip r2 保留 r1
                                    onMatch.apply(s2, mapper.apply(r1, r2)), onFail), onFail);
        }
        static Rule Choose(Rule superior, Rule inferior) {
            return (state, onMatch, onFail) ->
                    superior.match(state, onMatch, (s2, r2) ->
                            inferior.match(state, onMatch, onFail));
        }
    }



    interface V4 {
        static Rule Pat(String regex, Function<String, Result> mapper) {
            return (state, onMatch, onFail) -> {
                Matcher m = Pattern.compile(regex).matcher(state);
                if (m.lookingAt()/*start with*/) {
                    onMatch.apply(state.substring(m.end()), mapper.apply(state.substring(0, m.end())));
                } else {
                    onFail.apply(state, new Str(regex)/*expected*/);
                }
            };
        }
        static Rule Seq(Rule front, Rule rear, BiFunction<Result, Result, Result> mapper) {
            return (state, onMatch, onFail) ->
                    front.match(state, (s1, r1) ->
                            rear.match(s1, (s2, r2) ->
                                    onMatch.apply(s2, mapper.apply(r1, r2)), onFail), onFail);
        }
        static Rule Choose(Rule superior, Rule inferior) {
            return (state, onMatch, onFail) ->
                    superior.match(state, onMatch, (s2, r2) ->
                            inferior.match(state, onMatch, onFail));
        }
    }

    interface V5 {
        interface Cont {
            void apply(String state, Object result);
        }

        interface Rule {
            void match(String state, Cont onMatch, Cont onFail);
        }

        interface Fun2 extends BiFunction<Object, Object, Object> { }


        static Rule Pat(String regex, Function<String, Object> mapper) {
            return (state, onMatch, onFail) -> {
                Matcher m = Pattern.compile(regex).matcher(state);
                if (m.lookingAt()/*start with*/) {
                    onMatch.apply(state.substring(m.end()), mapper.apply(state.substring(0, m.end())));
                } else {
                    onFail.apply(state, new Str(regex)/*expected*/);
                }
            };
        }
        static Rule Seq(Rule front, Rule rear, Fun2 mapper) {
            return (state, onMatch, onFail) ->
                    front.match(state, (s1, r1) ->
                            rear.match(s1, (s2, r2) ->
                                    onMatch.apply(s2, mapper.apply(r1, r2)), onFail), onFail);
        }
        static Rule Choose(Rule superior, Rule inferior) {
            return (state, onMatch, onFail) ->
                    superior.match(state, onMatch, (s2, r2) ->
                            inferior.match(state, onMatch, onFail));
        }
    }


    interface V6 {
        interface Fun1 extends Function<Object, Object> { }
        interface Fun2 extends BiFunction<Object, Object, Object> { }



        class State<E> {
            final List<E> seq;
            public State(List<E> seq) {
                this.seq = seq;
            }
            public E next() { return seq.get(0); }
            public State<E> sub(int fromIdx, int toIdx) { return new State<>(seq.subList(fromIdx, toIdx)); }
            public State<E> sub(int fromIdx) { return new State<>(seq.subList(fromIdx, seq.size())); }
        }

        interface Cont<E> {
            void apply(State<E> state, Object result);
        }

        interface Rule<E> {
            void match(State<E> state, Cont<E> onMatch, Cont<E> onFail);

//            default Rule<E> map(Fun1 mapper) {
//                return (s, m, f) -> {
//                    match(s, (s1, r1) -> {
//                        m.apply(s1, mapper.apply(r1));
//                    }, f);
//                };
//            }
//
//            default Rule<E> flatMap(Function<Object, Rule<E>> mapper) {
//                return (s, m, f) -> {
//                    match(s, (s1, r1) -> {
//                        mapper.apply(r1).match(s1, m, f);
//                    }, f);
//                };
//            }
        }

        static <E> Rule<E> Pat(E e, Fun1 mapper) {
            return (state, onMatch, onFail) -> {
                E nxt = state.next();
                if (Objects.equals(e, nxt)) {
                    onMatch.apply(state.sub(1), mapper.apply(nxt));
                } else {
                    onFail.apply(state, e);
                }
            };
        }
        static <E> Rule<E> Seq(Rule<E> front, Rule<E> rear, Fun2 mapper) {
            return (state, onMatch, onFail) ->
                    front.match(state, (s1, r1) ->
                            rear.match(s1, (s2, r2) ->
                                    onMatch.apply(s2, mapper.apply(r1, r2)), onFail), onFail);
        }
        static <E> Rule<E> Choose(Rule<E> superior, Rule<E> inferior) {
            return (state, onMatch, onFail) ->
                    superior.match(state, onMatch, (s2, r2) ->
                            inferior.match(state, onMatch, onFail));
        }
    }


    class Brackets {
        static Rule bracketsV1() {
            V1.SequenceRule seq = V1.Seq(null, V1.Pat(")"));
            Rule brackets = V1.Choose(
                    V1.Seq(V1.Pat("("), seq),
                    V1.Pat("")
            );
            seq.front = brackets;
            return brackets;
        }

        static Rule bracketsV2() {
            return V2.Choose(
                    V2.Seq(V2.Pat("("), (s, a, b) -> V2.Seq(bracketsV2(), V2.Pat(")")).match(s, a, b)),
                    V2.Pat("")
            );
        }

        static Rule bracketsV3() {
            Rule lp = V3.Pat("(", Str::new);
            Rule rp = V3.Pat(")", Str::new);
            Rule empty = V3.Pat("", Str::new);

            return V3.Choose(
                    // V3.Seq(lp, V3.Seq(bracketsV3(), rp, Pair::new), Pair::new),
                    // 注意这里是递归结构, 且 call-by-value 求值模型, 直接构造会 Stack Overflow
                    // 用 η-变换来构造一个 thunk
                    V3.Seq(lp, (s, a, b) -> V3.Seq(bracketsV3(), rp, Pair::new).match(s, a, b), Pair::new),
                    empty
            );
        }

        static Result parse(Rule brackets, String str) {
            Result[] a = new Result[1];
            brackets.match(str, (s, r) -> {
                if (s.isEmpty()) {
                    a[0] = r;
                } else {
                    throw new RuntimeException("Invalid: " + s);
                }
            }, (s, r) -> {
                throw new RuntimeException("Expected: " + r);
            });
            return a[0];
        }

        static void test(Rule brackets) {
            System.out.println(parse(brackets, ""));
            System.out.println(parse(brackets, "()"));
            System.out.println(parse(brackets, "((()))"));

            int i = 0;
            try { System.out.println(parse(brackets, ")")); i++; } catch (RuntimeException ignored) { }
            try { System.out.println(parse(brackets, "()(")); i++; } catch (RuntimeException ignored) { }
            try { System.out.println(parse(brackets, "((())")); i++; } catch (RuntimeException ignored) { }
            if (i > 0) throw new RuntimeException();
        }

        public static void main(String[] args) {
            Brackets.test(bracketsV1());
            Brackets.test(bracketsV2());
            Brackets.test(bracketsV3());
        }
    }
}
