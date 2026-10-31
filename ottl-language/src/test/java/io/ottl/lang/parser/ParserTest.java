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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserTest {

    @Test
    void parsesSimpleSetWithWhere() {
        OttlScript script = Parser.parse("set(order.status, \"REVIEW\") where order.amount > 10000");
        assertThat(script.statements()).hasSize(1);

        Statement stmt = script.statements().get(0);
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
        assertThat(script.statements().get(0).editor().name()).isEqualTo("set");
        assertThat(script.statements().get(1).editor().name()).isEqualTo("delete_key");
        assertThat(script.statements().get(2).editor().name()).isEqualTo("keep_keys");
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
