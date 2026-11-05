/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

import io.ottl.lang.OttlDialect;
import io.ottl.lang.ast.Block;
import io.ottl.lang.ast.ComparisonOperator;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.MathOp;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserTest {

    @Test
    void parsesSimpleSetWithWhere() {
        OttlScript script = Parser.parse("set(order.status, \"REVIEW\") where order.amount > 10000");
        assertThat(script.statements()).hasSize(1);

        Statement stmt = (Statement) script.statements().get(0);
        assertThat(stmt.editor().name()).isEqualTo("set");
        assertThat(stmt.editor().arguments()).hasSize(2);

        Expression firstArg = stmt.editor().arguments().get(0);
        assertThat(firstArg).isInstanceOf(Expression.Path.class);
        Expression.Path path = (Expression.Path) firstArg;
        assertThat(path.segments()).containsExactly(
                new Expression.FieldSegment("order"),
                new Expression.FieldSegment("status"));

        assertThat(stmt.editor().arguments().get(1))
                .isEqualTo(new Expression.StringLiteral("REVIEW"));

        assertThat(stmt.condition()).isPresent();
        Condition.Comparison cmp = (Condition.Comparison) stmt.condition().get();
        assertThat(cmp.operator()).isEqualTo(ComparisonOperator.GT);
        assertThat(cmp.right()).isEqualTo(new Expression.IntLiteral(10000));
    }

    @Test
    void parsesMultipleSelfDelimitingStatements() {
        OttlScript script = Parser.parse("""
                set(attributes["env"], "prod")
                delete_key(attributes, "secret")
                keep_keys(attributes, ["env", "region"])
                """);
        assertThat(script.statements()).hasSize(3);
        assertThat(((Statement) script.statements().get(0)).editor().name()).isEqualTo("set");
        assertThat(((Statement) script.statements().get(1)).editor().name()).isEqualTo("delete_key");
        assertThat(((Statement) script.statements().get(2)).editor().name()).isEqualTo("keep_keys");
    }

    @Test
    void parsesKeyedPathAndIntKeys() {
        Statement stmt = Parser.parseStatement("set(data[\"a\"][0], true)");
        Expression.Path path = (Expression.Path) stmt.editor().arguments().get(0);
        assertThat(path.segments()).containsExactly(
                new Expression.FieldSegment("data"),
                new Expression.KeySegment("a"),
                new Expression.KeySegment(0L));
        assertThat(stmt.editor().arguments().get(1)).isEqualTo(new Expression.BoolLiteral(true));
    }

    @Test
    void parsesBooleanExpressionWithPrecedenceAndParens() {
        Statement stmt = Parser.parseStatement(
                "set(x, 1) where (a > 1 and b < 2) or not c == nil");
        Condition cond = stmt.condition().orElseThrow();
        assertThat(cond).isInstanceOf(Condition.Or.class);
        Condition.Or or = (Condition.Or) cond;
        assertThat(or.left()).isInstanceOf(Condition.And.class);
        assertThat(or.right()).isInstanceOf(Condition.Not.class);
    }

    @Test
    void parsesFloatsNegativesAndNil() {
        Statement stmt = Parser.parseStatement("set(x, -3.5) where y == nil");
        assertThat(stmt.editor().arguments().get(1)).isEqualTo(new Expression.FloatLiteral(-3.5));
        Condition.Comparison cmp = (Condition.Comparison) stmt.condition().orElseThrow();
        assertThat(cmp.right()).isEqualTo(new Expression.NilLiteral());
    }

    @Test
    void parsesNestedFunctionCallAsValue() {
        Statement stmt = Parser.parseStatement("set(name, Concat(a, \"-\", b))");
        Expression arg = stmt.editor().arguments().get(1);
        assertThat(arg).isInstanceOf(Expression.FunctionCall.class);
        assertThat(((Expression.FunctionCall) arg).name()).isEqualTo("Concat");
    }

    @Test
    void parsesWhereBlock() {
        OttlScript script = Parser.parse("""
                where stockMovement.quantity < 0 {
                    set(stockMovement.quantity, 0)
                    set(stockMovement.direction, nil)
                }
                """);
        assertThat(script.statements()).hasSize(1);
        WhereBlock block = (WhereBlock) script.statements().get(0);
        Condition.Comparison cmp = (Condition.Comparison) block.condition();
        assertThat(cmp.operator()).isEqualTo(ComparisonOperator.LT);
        assertThat(block.body()).hasSize(2);
        Statement second = (Statement) block.body().get(1);
        assertThat(second.editor().arguments().get(1)).isEqualTo(new Expression.NilLiteral());
        assertThat(second.condition()).isEmpty();
    }

    @Test
    void parsesNestedWhereBlockAndTrailingWhereInsideIt() {
        OttlScript script = Parser.parse("""
                set(attributes["before"], true)
                where attributes["quantity"] < 0 {
                    set(attributes["quantity"], 0)
                    set(attributes["flag"], true) where attributes["env"] == "prod"
                    where attributes["env"] == "dev" {
                        set(attributes["nested"], true)
                    }
                }
                """);
        assertThat(script.statements()).hasSize(2);
        WhereBlock outer = (WhereBlock) script.statements().get(1);
        assertThat(outer.body()).hasSize(3);
        Statement guarded = (Statement) outer.body().get(1);
        assertThat(guarded.condition()).isPresent();
        assertThat(outer.body().get(2)).isInstanceOf(WhereBlock.class);
    }

    @Test
    void whereFunctionCallIsStillAStatement() {
        Statement stmt = Parser.parseStatement("where(x, 1)");
        assertThat(stmt.editor().name()).isEqualTo("where");
        assertThat(stmt.editor().arguments()).hasSize(2);
    }

    @Test
    void reportsUnclosedWhereBlock() {
        assertThatThrownBy(() -> Parser.parse("where x { set(a, 1)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("'}'");
    }

    @Test
    void reportsPositionOnUnterminatedString() {
        assertThatThrownBy(() -> Parser.parse("set(x, \"oops)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Unterminated string");
    }

    @Test
    void reportsPositionOnMissingParen() {
        assertThatThrownBy(() -> Parser.parse("set(x, 1"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("')'");
    }

    @Test
    void parsesMathWithPrecedenceParenthesesAndUnaryPlus() {
        Statement stmt = Parser.parseStatement("set(n, (1 + 2) * 3 + -4)");
        Expression value = stmt.editor().arguments().get(1);
        assertThat(value).isEqualTo(new Expression.Math(
                new Expression.Math(
                        new Expression.Math(new Expression.IntLiteral(1), MathOp.ADD, new Expression.IntLiteral(2)),
                        MathOp.MUL,
                        new Expression.IntLiteral(3)),
                MathOp.ADD,
                new Expression.IntLiteral(-4)));
    }

    @Test
    void parsesComparisonWhoseLeftSideIsParenthesizedMath() {
        Statement stmt = Parser.parseStatement("set(x, 1) where (1 + 2) > 3");
        Condition.Comparison cmp = (Condition.Comparison) stmt.condition().orElseThrow();
        assertThat(cmp.operator()).isEqualTo(ComparisonOperator.GT);
        assertThat(cmp.left()).isInstanceOf(Expression.Math.class);
        assertThat(cmp.right()).isEqualTo(new Expression.IntLiteral(3));
    }

    @Test
    void parsesMapBytesFloatsAndNamedArguments() {
        Statement stmt = Parser.parseStatement("""
                set(target=attributes["out"], value={"k": .5, "b": 0x0001}) // comment
                """);
        assertThat(stmt.editor().argumentNames()).containsExactly(
                Optional.of("target"), Optional.of("value"));
        Expression.MapLiteral map = (Expression.MapLiteral) stmt.editor().arguments().get(1);
        assertThat(map.entries()).hasSize(2);
        assertThat(map.entries().get(0).value()).isEqualTo(new Expression.FloatLiteral(0.5));
        assertThat(map.entries().get(1).value()).isEqualTo(new Expression.BytesLiteral(new byte[] {0x00, 0x01}));
    }

    @Test
    void parsesCamelCaseNamedArguments() {
        Statement stmt = Parser.parseStatement("flatten(attributes, depth=2, resolveConflicts=true)");
        assertThat(stmt.editor().name()).isEqualTo("flatten");
        assertThat(stmt.editor().argumentNames()).containsExactly(
                Optional.empty(), Optional.of("depth"), Optional.of("resolveConflicts"));
    }

    @Test
    void parsesConverterIndexDynamicKeyAndLambda() {
        Statement stmt = Parser.parseStatement(
                "set(x, Split(field, \",\")[1][name + 1]) where y == (value) => value == 1");
        Expression.FunctionCall split = (Expression.FunctionCall) stmt.editor().arguments().get(1);
        assertThat(split.name()).isEqualTo("Split");
        assertThat(split.keys()).hasSize(2);
        assertThat(split.keys().get(0)).isEqualTo(new Expression.IntLiteral(1));
        assertThat(split.keys().get(1)).isInstanceOf(Expression.Math.class);

        Condition.Comparison cmp = (Condition.Comparison) stmt.condition().orElseThrow();
        assertThat(cmp.right()).isInstanceOf(Expression.Lambda.class);
        Expression.Lambda lambda = (Expression.Lambda) cmp.right();
        assertThat(lambda.parameters()).containsExactly("value");
        assertThat(lambda.body()).isInstanceOf(Expression.LambdaCondition.class);
    }

    @Test
    void rejectsUppercaseEditorLowercaseConverterAndOddBytes() {
        assertThatThrownBy(() -> Parser.parse("Concat(a, b)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("lowercase");
        assertThatThrownBy(() -> Parser.parse("set(x, len(y))"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("uppercase");
        assertThatThrownBy(() -> Parser.parse("set(x, 0x123)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("even number");
        assertThatThrownBy(() -> Parser.parse("set(x, 1)[0]"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("not editors");
    }

    @Test
    void parenthesizedWhereBeforeBraceStaysAWhereBlock() {
        OttlScript script = Parser.parse("""
                set(a, 1) where (b > 1) {
                    set(c, 2)
                }
                set(d, 3)
                """);
        assertThat(script.statements()).hasSize(3);
        Statement first = (Statement) script.statements().get(0);
        assertThat(first.condition()).isEmpty();
        assertThat(script.statements().get(1)).isInstanceOf(WhereBlock.class);
        assertThat(((Statement) script.statements().get(2)).editor().name()).isEqualTo("set");

        OttlScript leading = Parser.parse("""
                where (b > 1) {
                    set(c, 2)
                }
                """);
        assertThat(leading.statements()).hasSize(1);
        assertThat(leading.statements().get(0)).isInstanceOf(WhereBlock.class);
    }

    @Test
    void parenthesizedTrailingWhereStaysOnTheStatement() {
        OttlScript script = Parser.parse("set(a, 1) where (b > 1)");
        Statement stmt = (Statement) script.statements().get(0);
        assertThat(stmt.condition()).isPresent();
        assertThat(stmt.condition().orElseThrow()).isInstanceOf(Condition.Comparison.class);
    }

    @Test
    void whereCallFollowedByABlockStaysAnEditorCall() {
        OttlScript script = Parser.parse("""
                where(flag)
                {
                    set(a, 1)
                }
                """, OttlDialect.EXTENDED);
        assertThat(script.statements()).hasSize(2);
        assertThat(script.statements().get(0)).isInstanceOf(Statement.class);
        assertThat(((Statement) script.statements().get(0)).editor().name()).isEqualTo("where");
        assertThat(script.statements().get(1)).isInstanceOf(Block.class);
    }

    @Test
    void reportsMalformedMapAndLambda() {
        assertThatThrownBy(() -> Parser.parse("set(a, {1: 2})"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Map key")
                .hasMessageContaining("[1:");
        assertThatThrownBy(() -> Parser.parse("set(a, {\"k\" 1})"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("':'");
        assertThatThrownBy(() -> Parser.parse("set(a, (x, x) => 1)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Duplicate lambda parameter");
        assertThatThrownBy(() -> Parser.parse("set(a, (x) => )"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Expected a value");
    }

    @Test
    void rejectsValueNestingPastTheHardCeiling() {
        assertNestingRejected("set(a, " + "[".repeat(80) + "1" + "]".repeat(80) + ")");
        assertNestingRejected("set(a, " + "{\"k\":".repeat(80) + "1" + "}".repeat(80) + ")");
        assertNestingRejected("set(a, " + "Len(".repeat(80) + "a" + ")".repeat(80) + ")");
        StringBuilder indexes = new StringBuilder("set(a, ");
        indexes.append("root[".repeat(80)).append("0").append("]".repeat(80)).append(")");
        assertNestingRejected(indexes.toString());
        assertNestingRejected("set(a, 1) where " + "not ".repeat(80) + "true");
        StringBuilder lambdas = new StringBuilder("set(a, ");
        lambdas.append("(x)=>Len(".repeat(80)).append("x").append(")".repeat(80)).append(")");
        assertNestingRejected(lambdas.toString());
    }

    @Test
    void moderateValueNestingStillParsesWhenTheBlockLimitIsOne() {
        OttlScript script = Parser.parse("set(a, [[1]])", OttlDialect.OTTL, 1);
        assertThat(script.statements()).hasSize(1);
        assertThat(Parser.parse("set(a, {\"k\": {\"n\": 1}})").statements()).hasSize(1);
        assertThat(Parser.parse("set(a, (x) => Len((y) => y))").statements()).hasSize(1);
    }

    private static void assertNestingRejected(String source) {
        OttlParseException ex = org.assertj.core.api.Assertions.catchThrowableOfType(
                () -> Parser.parse(source), OttlParseException.class);
        assertThat(ex).isNotNull();
        assertThat(ex.getMessage()).contains("Nesting too deep (limit " + Parser.MAX_NESTING + ")");
        assertThat(ex.line()).isEqualTo(1);
        assertThat(ex.column()).isPositive();
    }
}
