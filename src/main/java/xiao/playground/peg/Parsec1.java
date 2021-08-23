package xiao.playground.peg;

import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.*;
import static xiao.playground.peg.Parsec1.Rules.*;


/**
 * Parser Combinator
 * @author chuxiaofeng
 *
 * Parsec Demo <br>
 *  - PEG V5: 发现 Result 好像没啥用, 就彻底把返回值变成 Object 好了
 *
 * - Seq 都可以用 flatmap 来改写成链式调用, Choose 都可以用 or 来改写链式调用 <br>
 *      没有 do 语法糖, 链式调用 + CPS写法啰嗦, 可读性更差 <br>
 * - 可以把 String state 修改成 State state 内部存储字符串位置信息用来提供精确报错 <br>
 *      也可以把 State 泛化成 TokenStream 或者 Sequence<Token>  <br>
 */
public interface Parsec1 {

    interface Fun1 extends Function<Object, Object> { }
    interface Fun2 extends BiFunction<Object, Object, Object> { }
    interface Fun3 { Object apply(Object t1, Object t2, Object t3); }

    interface Cont {
        void apply(String state, Object result);
    }

    @SuppressWarnings({"unused", "CodeBlock2Expr"})
    interface Rule {

        void match(String state, Cont onMatch, Cont onFail);

        default Rule map(Fun1 mapper) {
            return (s, m, f) -> {
                match(s, (s1, r1) -> {
                    m.apply(s1, mapper.apply(r1));
                }, f);
            };
        }

        default Rule flatMap(Function<Object, Rule> binder) {
            return (s, m, f) -> {
                match(s, (s1, r1) -> {
                    binder.apply(r1).match(s1, m, f);
                }, f);
            };
        }

        default Rule seq(Rule rule, Fun2 mapper)    { return Seq(this, rule, mapper);   }
        default Rule then(Rule rule)                { return seq(rule, (a, b) -> b);          }
        default Rule over(Rule rule)                { return seq(rule, (a, b) -> a);          }
        default Rule or(Rule rule)                  { return Choose(this, rule);     }
        default Rule option(Object def)             { return Option(this, def);         }
        default Rule optional()                     { return Optional(this);            }
        default Rule many()                         { return Many(this);                }
        default Rule many1()                        { return Many1(this);               }
        default Rule skip()                         { return Skip(this);                }
        default Rule skipMany()                     { return SkipMany(this);            }
        default Rule skipMany1()                    { return SkipMany1(this);           }
        default Rule count(int n)                   { return Count(this, n);            }
        default Rule between(Rule open, Rule close) { return Between(open, close, this);}
        default Rule sepBy(Rule by)                 { return SepBy(this, by);           }
        default Rule sepBy1(Rule by)                { return SepBy1(this, by);          }
        default Rule endBy(Rule by)                 { return EndBy(this, by);           }
        default Rule endBy1(Rule by)                { return EndBy1(this, by);          }
        default Rule sepEndBy(Rule by)              { return SepEndBy(this, by);        }
        default Rule sepEndBy1(Rule by)             { return SepEndBy1(this, by);       }
        default Rule peek()                         { return LookAhead(this);           }
        default Rule notFollowedBy()                { return NotFollowedBy(this);       }
        default Rule manyTill(Rule till)            { return ManyTill(this, till);      }

        default Rule chainl(Rule op, Object def, Fun3 mapper)    { return Chainl(this, op, def, mapper);     }
        default Rule chainl1(Rule op, Fun3 mapper)               { return Chainl1(this, op, mapper);         }
        default Rule chainr(Rule op, Object def, Fun3 mapper)    { return Chainr(this, op, def, mapper);     }
        default Rule chainr1(Rule op, Fun3 mapper)               { return Chainr1(this, op, mapper);         }
    }

    // Combinators
    @SuppressWarnings("CodeBlock2Expr")
    interface Rules {

        // ~ Core 就 FinalRule SequencingRule AlternativeRule 三个方法 ~

        static Rule Pat(Pattern ptn, Function<String, Object> mapper) {
            return (s, m, f) -> {
                Matcher mat = ptn.matcher(s);
                if (mat.lookingAt()/*start with*/) {
                    // mat.end() == 0 时, 可能死循环
                    m.apply(s.substring(mat.end()), mapper.apply(s.substring(0, mat.end())));
                } else {
                    f.apply(s, new ParseException(s, ptn.pattern()));
                }
            };
        }

        static Rule Seq(Rule front, Rule rear, Fun2 mapper) {
            /*return front.flatMap(r1 -> (s, m, f) -> {
                rear.match(s, (s2, r2) -> {
                    m.apply(s2, mapper.apply(r1, r2));
                }, f);
            });*/
            return (s, m, f) -> {
                front.match(s, (s1, r1) -> {
                    rear.match(s1, (s2, r2) -> {
                        m.apply(s2, mapper.apply(r1, r2));
                    }, f);
                }, f);
            };
        }

        // Choice
        static Rule Choose(Rule superior, Rule inferior) {
            return (s, m, f) -> {
                superior.match(s, m, (s1, r1) -> {
                    inferior.match(s, m, (s2, r2) -> {
                        f.apply(s2, new ParseException(s2, r1 + "<|>" + r2));
                    });
                });
            };
        }

        // ============================================================================

        static Rule Return(Object r) {
            return Pat("", s -> r);
        }

        static Rule Null() {
            return (s, m, f) -> m.apply(s, null);
        }

        static Rule Pat(String regex, Function<String, Object> mapper) {
            return Pat(Pattern.compile(regex, Pattern.DOTALL), mapper);
        }

        static Rule Pat(String regex) {
            return Pat(regex, s -> null);
        }

        static Rule Seq(Rule fst, Rule sec, Rule trd, Fun3 mapper) {
            return (s, m, f) -> {
                fst.match(s, (s1, r1) -> {
                    sec.match(s1, (s2, r2) -> {
                        trd.match(s2, (s3, r3) -> {
                            m.apply(s3, mapper.apply(r1, r2, r3));
                        }, f);
                    }, f);
                }, f);
            };
        }

        // CPS 的写法不方便处理报错信息 a or b or c ...
        static Rule Choose(Rule... rules) {
            if (rules.length == 0) {
                throw new IllegalArgumentException("No Choice");
            }
            if (rules.length == 1) {
                return rules[0];
            } else {
                return Choose(rules[0], Choose(copyOfRange(rules, 1, rules.length)));
            }
        }

        static Rule Whitespace() {
            return Pat("\\s*");
        }

        // 注意, 被 Option 或者 Optional  wrap 的 rule 永远成功, Choose 或者 or 没用
        // e.g. SepBy SepEndBy Chainl Chainr

        static Rule Optional(Rule rule) {
            return Option(rule, null);
        }

        static Rule Option(Rule rule, Object def) {
            return Choose(rule, Return(def));
        }


//        // 另一种写法
//        // private
//        static Rule many1_(Rule rule) {
//            Rule many1 = (s, m, f) -> many1_(rule).match(s, m, f);  // thunk
//            return Seq(rule, Optional(many1), Pair::new);
//        }
//        static Rule Many1(Rule rule) {
//            return many1_(rule).map(r -> Lists.fromPair(((Pair) r)));
//        }
//        static Rule Many(Rule rule) {
//            return Option(Many1(rule), Lists.emptyList);
//        }

        // private
        static Rule many_(Rule rule) {
            Rule many_ = (s, m, f) -> many_(rule).match(s, m, f); // thunk
            return Optional(Seq(rule, many_, Pair::new));
        }

        // rule 如果不消耗 state 会 stackoverflow
        static Rule Many(Rule rule) {
            return many_(rule).map(r -> Lists.fromPair(((Pair) r)));
        }

        // rule 如果不消耗 state 会 stackoverflow
        static Rule Many1(Rule rule) {
            return Seq(
                    rule,
                    Many(rule),
                    (a, b) -> Lists.prepend(((List<?>) b), a)
            );
        }

        static Rule Skip(Rule rule) {
            return rule.map(it -> null);
        }

        // rule 如果不消耗 state 会 stackoverflow
        static Rule SkipMany(Rule rule) {
            return Skip(Many(rule));
        }

        // rule 如果不消耗 state 会 stackoverflow
        static Rule SkipMany1(Rule rule) {
            return Skip(Many1(rule));
        }

        // private
        static Rule count_(Rule rule, int n) {
            if (n <= 0) {
                return Null();
            } else {
                return Seq(rule, count_(rule, n - 1), Pair::new);
            }
        }

        // repeat
        static Rule Count(Rule rule, int n) {
            return count_(rule, n).map(r -> Lists.fromPair(((Pair) r)));
        }

        static Rule Between(Rule open, Rule close, Rule rule) {
            /*return (s, m, f) -> {
                open.match(s, (s1, r1) -> {
                    rule.match(s1, (s2, r2) -> {
                        close.match(s2, (s3, r3) -> {
                            m.apply(s3, r2);
                        }, f);
                    }, f);
                }, f);
            };*/
            return open.then(rule).over(close);
        }


//        // 另一种写法
//        static Rule SepBy(Rule rule, Rule by, boolean optEndedSep) {
//            return Option(SepBy1(rule, by, optEndedSep), Lists.emptyList);
//        }
//        static Rule SepBy1(Rule rule, Rule by, boolean optEndedSep) {
//            Rule sepRules = Many(by.then(rule));
//            if (optEndedSep) {
//                sepRules = sepRules.over(Optional(by));
//            }
//            return Seq(rule, sepRules, (a, b) -> Lists.prepend(((List<?>) b), a));
//        }

        // rule & by 如果不消耗 state 会 stackoverflow
        // 最后无 by
        static Rule SepBy(Rule rule, Rule by) {
            // return SepBy(rule, by, false);
            return Option(SepBy1(rule, by), Lists.emptyList);
        }

        // 最后无 by
        static Rule SepBy1(Rule rule, Rule by) {
            // return SepBy1(rule, by, false);
            return Seq(
                    rule,
                    Many(by.then(rule)),
                    (a, b) -> Lists.prepend(((List<?>) b), a)
            );
        }

        // 最后必须有 by
        static Rule EndBy(Rule rule, Rule by) {
            return Many(rule.over(by));
        }

        // 最后必须有 by
        static Rule EndBy1(Rule rule, Rule by) {
            return Many1(rule.over(by));
        }

        // private
        static Rule sepEndBy_(Rule rule, Rule by) {
            Rule sepEndBy1_ = (s, m, f) -> sepEndBy1_(rule, by).match(s, m, f); // thunk
            return Option(sepEndBy1_, null);
        }

        // private
        static Rule sepEndBy1_(Rule rule, Rule by) {
            return Seq(
                    rule,
                    Optional(by.then(sepEndBy_(rule, by))),
                    Pair::new
            );
        }

        // 可选最后的 by
        static Rule SepEndBy(Rule rule, Rule by) {
            // return SepBy(rule, by, true);
            return sepEndBy_(rule, by).map(it -> Lists.fromPair(((Pair) it)));
        }

        // 可选最后的 by
        static Rule SepEndBy1(Rule rule, Rule by) {
            // return SepBy1(rule, by, true);
            return sepEndBy1_(rule, by).map(it -> Lists.fromPair(((Pair) it)));
        }

        // 构造左结合双目运算符解析
        static Rule Chainl(Rule rule, Rule op, Object def, Fun3 mapper) {
            return Option(Chainl1(rule, op, mapper), def);
        }

        static Rule Chainl1(Rule rule, Rule op, Fun3 mapper) {
            return rule.flatMap(x -> (s, m, f) -> {
                chainl1_rest(rule, op, x, mapper).match(s, m, f);
            });
        }

        // private
        static Rule chainl1_rest(Rule rule, Rule op, Object x, Fun3 mapper) {
            return Option(
                    Seq(
                            op, // app
                            rule, // y
                            Pair::new
                    ).flatMap(fy -> (s, m, f) -> {
                        Object app = ((Pair) fy).car;
                        Object y = ((Pair) fy).cdr;
                        chainl1_rest(rule, op, mapper.apply(app, x, y), mapper).match(s, m, f);
                    }),
                    x
            );
        }

        // 构造右结合双目运算符解析
        static Rule Chainr(Rule rule, Rule op, Object def, Fun3 mapper) {
            return Option(Chainr1(rule, op, mapper), def);
        }

        static Rule Chainr1(Rule rule, Rule op, Fun3 mapper) {
            return rule.flatMap(x -> (s, m, f) -> {
                chainr1_rest(rule, op, x, mapper).match(s, m, f);
            });
        }

        // private
        static Rule chainr1_rest(Rule rule, Rule op, Object x, Fun3 mapper) {
            return Option(
                    Seq(
                            op,
                            Chainr1(rule, op, mapper),
                            (app, y) -> mapper.apply(app, x, y)
                    ),
                    x
            );
        }

        static Rule EOF() {
            return (s, m, f) -> {
                if (s.isEmpty()) {
                    m.apply(s, null);
                } else {
                    f.apply(s, new ParseException(s, "EOF"));
                }
            };
        }

        // Tricky combinators

        static Rule AnyChar() {
            return (s, m, f) -> {
                if (s.isEmpty()) {
                    f.apply(s, new ParseException("", "EOF"));
                } else {
                    m.apply(s.substring(1), s.substring(0, 1));
                }
            };
        }

        static Rule LookAhead(Rule rule) {
            return (s, m, f) -> {
                rule.match(s, (s1, r1) -> {
                    m.apply(s, r1);
                }, f);
            };
        }

        // 实现最长匹配规则, e.g. 识别关键词let, lets 应该识别为 id 不是 let
        // rule 必须消耗 state, 否则永远失败
        static Rule NotFollowedBy(Rule rule) {
            return (s, m, f) -> {
                rule.match(s, (s1, r1) -> {
                    f.apply(s1, new ParseException(s,"not followed by " + r1));
                }, (s2, r2) -> {
                    m.apply(s, null);
                });
            };
        }

        // private
        static Rule manyTill_(Rule many, Rule till) {
            // 注意这里先匹配 till
            return Choose(
                    Skip(till),
                    Seq(
                            many,
                            (s, m, f) -> manyTill_(many, till).match(s, m, f), // thunk
                            Pair::new
                    )
            );
        }

        static Rule ManyTill(Rule many, Rule till) {
            return manyTill_(many, till).map(it -> Lists.fromPair((Pair) it));
        }

    }

    class ParseException extends RuntimeException {
        public ParseException(String state, String expected) {
            super("State=" + state + ", expected=" + expected, null, true, false);
        }
    }

    @SuppressWarnings("rawtypes")
    interface Lists {
        List emptyList = Collections.unmodifiableList(Collections.emptyList());
        static List prepend(List lst, Object el) {
            // CopyOnWriteArrayList ?
            List<Object> cp = new ArrayList<>(lst.size() + 1);
            cp.add(el);
            cp.addAll(lst);
            return Collections.unmodifiableList(cp);
        }
        static List<Object> fromPair(Pair pair) {
            List<Object> lst = new ArrayList<>();
            if (pair == null) {
                return lst;
            }
            lst.add(pair.car);
            while (pair.cdr != null) {
                pair = ((Pair) pair.cdr);
                lst.add(pair.car);
            }
            return lst;
        }
    }

    class Pair {
        public final Object car;
        public final Object cdr;
        public Pair(Object car, Object cdr) { this.car = car; this.cdr = cdr; }
        @Override public String toString() { return "(" + car + ", " + cdr + ")"; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Objects.equals(car, ((Pair) o).car) && Objects.equals(cdr, ((Pair) o).cdr);
        }
        @Override public int hashCode() { return Objects.hash(car, cdr); }
    }

    class Triple {
        public final Object fst;
        public final Object sec;
        public final Object trd;
        public Triple(Object fst, Object sec, Object trd) {
            this.fst = fst;
            this.sec = sec;
            this.trd = trd;
        }
        @Override public String toString() { return "(" + fst + ", " + sec + ", " + trd + ')'; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return Objects.equals(fst, ((Triple) o).fst) && Objects.equals(sec, ((Triple) o).sec) && Objects.equals(trd, ((Triple) o).trd);
        }
        @Override public int hashCode() { return Objects.hash(fst, sec, trd); }
    }
}
