package edu.washington.cs.knowitall.regex;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Function;

import edu.washington.cs.knowitall.regex.Expression.BaseExpression;
import edu.washington.cs.knowitall.regex.Expression.EndAssertion;
import edu.washington.cs.knowitall.regex.Expression.StartAssertion;
import edu.washington.cs.knowitall.regex.FiniteAutomaton.Automaton;
import edu.washington.cs.knowitall.regex.RegexException.TokenizationRegexException;

/**
 * A regular expression engine that operates over sequences of user-specified
 * objects.
 *
 * @author Michael Schmitz <schmmd@cs.washington.edu>
 *
 * @param  <E>  the type of the sequence elements
 */
public abstract class RegularExpression<E> implements Predicate<List<E>> {
    public final List<Expression<E>> expressions;
    public final Automaton<E> auto;

    public RegularExpression(String expression) {
        this.expressions = tokenize(expression);
        this.auto = this.build(this.expressions);
    }

    public RegularExpression(List<Expression<E>> expressions) {
        this.expressions = expressions;
        this.auto = this.build(this.expressions);
    }

    /***
     * The factory method creates an expression from the supplied token string.
     * @param  token  a string representation of a token
     * @return  an evaluatable representation of a token
     */
    public abstract BaseExpression<E> factory(String token);

    /***
     * Read a token from the remaining text and return it.
     *
     * This is a default implementation that is overridable.
     * In the default implementation, the starting and ending
     * token characters are not escapable.
     *
     * If this implemenation is overridden, A token MUST ALWAYS
     * start with '<' or '[' and end with '>' or ']'.
     *
     * @param remaining
     * @return
     */
    public String readToken(String remaining) {
        int start = 0;
        char c = remaining.charAt(0);

        int end;
        if (c == '<') {
            end = indexOfClose(remaining, start, '<', '>');
        }
        else if (c == '[' ){
            end = indexOfClose(remaining, start, '[', ']');
        }
        else {
            throw new IllegalStateException();
        }

        // make sure we found the end
        if (end == -1) {
            throw new TokenizationRegexException(
                    "bad token. Non-matching brackets (<> or []): " + start
                    + ":\"" + remaining.substring(start) + "\"");
        }

        String token = remaining.substring(start, end + 1);
        return token;
    }

    /***
     * Create a regular expression without tokenization support.
     * @param expressions
     * @return
     */
    public static <E> RegularExpression<E> compile(List<Expression<E>> expressions) {
        return new RegularExpression<E>(expressions) {
            @Override
            public BaseExpression<E> factory(String token) {
                throw new IllegalArgumentException();
            }
        };
    }

    /***
     * Create a regular expression from the specified string.
     * @param expression
     * @param factoryDelegate
     * @return
     */
    public static <E> RegularExpression<E> compile(final String expression,
            final Function<String, BaseExpression<E>> factoryDelegate) {
        return new RegularExpression<E>(expression) {
            @Override
            public BaseExpression<E> factory(String token) {
                return factoryDelegate.apply(token);
            }
        };
    }

    @Override
    public boolean equals(Object other) {
        if (! (other instanceof RegularExpression<?>)) {
            return false;
        }

        RegularExpression<?> expression = (RegularExpression<?>) other;
        return this.toString().equals(expression.toString());
    }

    @Override
    public int hashCode() {
        return this.toString().hashCode();
    }

    @Override
    public String toString() {
        List<String> expressions = new ArrayList<String>(
                this.expressions.size());
        for (Expression<E> expr : this.expressions) {
            expressions.add(expr.toString());
        }

        return Joiner.on(" ").join(expressions);
    }

    /**
     * Build an NFA from the list of expressions.
     * @param exprs
     * @return
     */
    protected Automaton<E> build(List<Expression<E>> exprs) {
        Expression.MatchingGroup<E> group = new Expression.MatchingGroup<E>(exprs);
        return group.build();
    }

    /**
     * Apply the expression against a list of tokens.
     *
     * @return true iff the expression if found within the tokens.
     */
    @Override
    public boolean apply(List<E> tokens) {
        if (this.find(tokens) != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Apply the expression against a list of tokens.
     *
     * @return true iff the expression matches all of the tokens.
     */
    public boolean matches(List<E> tokens) {
        Match<E> match = this.lookingAt(tokens, 0);
        return match != null && match.endIndex() == tokens.size();
    }

    /**
     * Find the first match of the regular expression against tokens. This
     * method is slightly slower due to additional memory allocations. However,
     * the response has much greater detail and is very useful for
     * writing/debugging regular expressions.
     *
     * @param tokens
     * @return an object representing the match, or null if no match is found.
     */
    public Match<E> find(List<E> tokens) {
        return this.find(tokens, 0);
    }

    /**
     * Find the first match of the regular expression against tokens, starting
     * at the specified index.
     *
     * @param tokens tokens to match against.
     * @param start index to start looking for a match.
     * @return an object representing the match, or null if no match is found.
     */
    public Match<E> find(List<E> tokens, int start) {
        Match<E> match;
        for (int i = start; i <= tokens.size() - auto.minMatchingLength(); i++) {
            match = this.lookingAt(tokens, i);
            if (match != null) {
                return match;
            }
        }

        return null;
    }

    /**
     * Determine if the regular expression matches the beginning of the
     * supplied tokens.
     *
     * @param tokens the list of tokens to match.
     * @return an object representing the match, or null if no match is found.
     */
    public Match<E> lookingAt(List<E> tokens) {
        return this.lookingAt(tokens, 0);
    }

    /**
     * Determine if the regular expression matches the supplied tokens,
     * starting at the specified index.
     *
     * @param tokens the list of tokens to match.
     * @param start the index where the match should begin.
     * @return an object representing the match, or null if no match is found.
     */
    public Match<E> lookingAt(List<E> tokens, int start) {
        return auto.lookingAt(tokens, start);
    }

    public Match<E> match(List<E> tokens) {
        Match<E> match = this.lookingAt(tokens);
        if (match != null && match.endIndex() == tokens.size()) {
            return match;
        }
        else {
            return null;
        }
    }

    /**
     * Find all non-overlapping matches of the regular expression against tokens.
     *
     * @param tokens
     * @return an list of objects representing the match.
     */
    public List<Match<E>> findAll(List<E> tokens) {
        List<Match<E>> results = new ArrayList<Match<E>>();

        int start = 0;
        Match<E> match;
        do {
            match = this.find(tokens, start);

            if (match != null) {
                start = match.endIndex();

                // match may be empty query string has all optional parts
                if (!match.isEmpty()) {
                    results.add(match);
                }
            }
        } while (match != null);

        return results;
    }

    /**
     * Convert a list of tokens (<...>) to a list of expressions.
     *
     * @param tokens
     * @param factory
     *            Factory class to create a BaseExpression from the text between
     *            angled brackets.
     * @return
     */
    public List<Expression<E>> tokenize(String string) {
        List<Expression<E>> expressions = new ArrayList<Expression<E>>();

        final Pattern whitespacePattern = Pattern.compile("\\s+");
        final Pattern unaryPattern = Pattern.compile("[*?+]");
        final Pattern minMaxPattern = Pattern.compile("\\{(\\d+),(\\d+)\\}");
        final Pattern binaryPattern = Pattern.compile("[|]");

        List<String> tokens = new ArrayList<String>();

        char stack = ' ';
        int start = 0;
        while (start < string.length()) {
            Matcher matcher;

            // skip whitespace
            if ((matcher = whitespacePattern.matcher(string))
                .region(start, string.length()).lookingAt()) {
                start = matcher.end();
                continue;
            }

            char c = string.charAt(start);
            // group, assertion, or token
            if (c == '(' || c == '<' || c == '[' || c == '$' || c == '^') {
                // group
                if (string.charAt(start) == '(') {
                    int end = indexOfClose(string, start, '(', ')');
                    if (end == -1) {
                        throw new TokenizationRegexException("unclosed parenthesis: " + start
                                + ":\"" + string.substring(start) + ")\"");
                    }

                    String group = string.substring(start + 1, end);
                    start = end + 1;

                    final Pattern namedPattern = Pattern.compile("<(\\w*)>:(.*)");
                    final Pattern unnamedPattern = Pattern.compile("\\?:(.*)");

                    // named group (matching)
                    if ((matcher = namedPattern.matcher(group)).matches()) {
                        String groupName = matcher.group(1);
                        group = matcher.group(2);
                        List<Expression<E>> groupExpressions = this.tokenize(group);
                        expressions.add(new Expression.NamedGroup<E>(groupName, groupExpressions));
                    }
                    // unnamed group
                    else if ((matcher = unnamedPattern.matcher(group)).matches()) {
                        group = matcher.group(1);
                        List<Expression<E>> groupExpressions = this.tokenize(group);
                        expressions.add(new Expression.NonMatchingGroup<E>(groupExpressions));
                    }
                    // group (matching)
                    else {
                        List<Expression<E>> groupExpressions = this.tokenize(group);
                        expressions.add(new Expression.MatchingGroup<E>(groupExpressions));
                    }
                }

                // token
                else if (c == '<' || c == '[') {
                    String token = readToken(string.substring(start));
                    try {
                        // strip off enclosing characters
                        String tokenInside = token.substring(1, token.length() - 1);
                        BaseExpression<E> base = factory(tokenInside);
                        expressions.add(base);

                        start += token.length();
                    }
                    catch (Exception e) {
                        throw new TokenizationRegexException("error parsing token: " + token, e);
                    }
                }

                // assertion (^)
                else if (c == '^') {
                    expressions.add(new StartAssertion<E>());
                    start += 1;
                }

                // assertion ($)
                else if (c == '$') {
                    expressions.add(new EndAssertion<E>());
                    start += 1;
                }

                // check if we have a floating OR operator
                if (stack == '|') {
                    try {
                        stack = ' ';
                        if (expressions.size() < 2) {
                            throw new IllegalStateException(
                                    "OR operator is applied to fewer than 2 elements.");
                        }

                        Expression<E> expr1 = expressions.remove(expressions.size() - 1);
                        Expression<E> expr2 = expressions.remove(expressions.size() - 1);
                        expressions.add(new Expression.Or<E>(expr1, expr2));
                    }
                    catch (Exception e) {
                        throw new TokenizationRegexException("error parsing OR (|) operator.", e);
                    }
                }
            }
            // unary operator
            else if ((matcher = unaryPattern.matcher(string))
                     .region(start, string.length()).lookingAt()) {
                char operator = matcher.group(0).charAt(0);

                // pop the last expression
                Expression<E> base = expressions.remove(expressions.size() - 1);

                // add the operator to it
                Expression<E> expr;
                if (operator == '?') {
                    expr = new Expression.Option<E>(base);
                } else if (operator == '*') {
                    expr = new Expression.Star<E>(base);
                } else if (operator == '+') {
                    expr = new Expression.Plus<E>(base);
                }
                else {
                    throw new IllegalStateException();
                }

                expressions.add(expr);

                start = matcher.end();
            }
            // min/max operator "{x,y}"
            else if ((matcher = minMaxPattern.matcher(string))
                    .region(start, string.length()).lookingAt()) {
                int minOccurrences = Integer.parseInt(matcher.group(1));
                int maxOccurrences = Integer.parseInt(matcher.group(2));

                // pop the last expression and add operator
                Expression<E> base = expressions.remove(expressions.size() - 1);
                Expression<E> expr = new Expression.MinMax<E>(base, minOccurrences, maxOccurrences);

                expressions.add(expr);

                start = matcher.end();
            }
            // binary operator (alternation)
            else if ((matcher = binaryPattern.matcher(string))
                     .region(start, string.length()).lookingAt()) {
                tokens.add(matcher.group(0));
                stack = '|';
                start = matcher.end();
            }
            else {
                throw new TokenizationRegexException("unknown symbol: "
                        + string.substring(start));
            }
        }

        if (stack == '|') {
            throw new TokenizationRegexException("OR remains on the stack.");
        }

        return expressions;
    }

    /**
     * An interactive program that compiles a word-based regular expression
     * specified in arg1 and then reads strings from stdin, evaluating them
     * against the regular expression.
     * @param args
     */
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);

        RegularExpression<String> regex = RegularExpressions.word(args[0]);
        System.out.println("regex: " + regex);
        System.out.println();

        while (scan.hasNextLine()) {
            String line = scan.nextLine();

            System.out.println("contains: " + regex.apply(Arrays.asList(line.split("\\s+"))));
            System.out.println("matches:  " + regex.matches(Arrays.asList(line.split("\\s+"))));
            System.out.println();
        }

        scan.close();
    }

    private static int indexOfClose(String string, int start, char open, char close) {
        start--;

        int count = 0;
        do {
            start++;

            // we hit the end
            if (start >= string.length()) {
                return -1;
            }

            char c = string.charAt(start);

            // we hit an open/close
            if (c == open) {
                count++;
            } else if (c == close) {
                count--;
            }

        } while (count > 0);

        return start;
    }
}
