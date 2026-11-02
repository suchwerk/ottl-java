/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

import io.ottl.lang.ast.ComparisonOperator;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.Expression;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;
import org.junit.jupiter.api.Test;

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
}
