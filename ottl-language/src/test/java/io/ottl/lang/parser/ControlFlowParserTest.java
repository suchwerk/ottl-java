/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lang.parser;

import io.ottl.lang.OttlDialect;
import io.ottl.lang.ast.Block;
import io.ottl.lang.ast.Condition;
import io.ottl.lang.ast.IfStatement;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlFlowParserTest {

    private static OttlScript extended(String source) {
        return Parser.parse(source, OttlDialect.EXTENDED);
    }

    @Test
    void parsesEmptyAndNestedBlocks() {
        Block empty = (Block) extended("{ }").statements().get(0);
        assertThat(empty.statements()).isEmpty();
        assertThat(empty.line()).isEqualTo(1);

        OttlScript script = extended("""
                {
                    set(order.status, "REVIEW")
                    {
                        set(order.priority, "HIGH")
                    }
                }
                """);
        Block outer = (Block) script.statements().get(0);
        assertThat(outer.statements()).hasSize(2);
        assertThat(outer.statements().get(0)).isInstanceOf(Statement.class);
        Block inner = (Block) outer.statements().get(1);
        assertThat(((Statement) inner.statements().get(0)).editor().name()).isEqualTo("set");
    }

    @Test
    void parsesIfElseIfElseAsNestedIf() {
        IfStatement root = (IfStatement) extended("""
                if order.amount > 10000 {
                    set(order.status, "REVIEW")
                    set(order.priority, "HIGH")
                } else if order.amount > 5000 {
                    set(order.priority, "MEDIUM")
                } else {
                    set(order.priority, "LOW")
                }
                """).statements().get(0);

        assertThat(root.condition()).isInstanceOf(Condition.Comparison.class);
        assertThat(root.thenBlock().statements()).hasSize(2);
        assertThat(root.line()).isEqualTo(1);
        assertThat(root.column()).isEqualTo(1);

        IfStatement middle = (IfStatement) root.elseBranch().orElseThrow();
        assertThat(middle.thenBlock().statements()).hasSize(1);
        Block otherwise = (Block) middle.elseBranch().orElseThrow();
        Statement low = (Statement) otherwise.statements().get(0);
        assertThat(low.editor().arguments().get(1).toString()).contains("LOW");
    }

    @Test
    void parsesNestedIfAndWhereInsideExtendedBlocks() {
        IfStatement outer = (IfStatement) extended("""
                if order.customer.vip == true {
                    if order.amount > 1000 {
                        set(order.priority, "HIGH")
                    } else {
                        set(order.priority, "NORMAL")
                    }
                    where order.amount > 0 {
                        set(order.status, "REVIEW")
                    }
                }
                """).statements().get(0);
        assertThat(outer.thenBlock().statements()).hasSize(2);
        assertThat(outer.thenBlock().statements().get(0)).isInstanceOf(IfStatement.class);
        assertThat(outer.thenBlock().statements().get(1)).isInstanceOf(WhereBlock.class);
    }

    @Test
    void keepsWhereBlocksAndEditorCallsInTheOttlDialect() {
        OttlScript script = Parser.parse("""
                set(order.status, "REVIEW") where order.amount > 10000
                where order.amount > 0 {
                    set(order.priority, "HIGH")
                }
                """);
        assertThat(script.statements().get(0)).isInstanceOf(Statement.class);
        assertThat(script.statements().get(1)).isInstanceOf(WhereBlock.class);
        Statement call = Parser.parseStatement("if(x, 1)");
        assertThat(call.editor().name()).isEqualTo("if");
    }

    @Test
    void fieldNamedIfStaysAPath() {
        Statement stmt = (Statement) extended("set(order.if, 1)").statements().get(0);
        assertThat(stmt.editor().arguments().get(0).toString()).contains("if");
    }

    @Test
    void ottlDialectRejectsBlocksAndConditionals() {
        assertThatThrownBy(() -> Parser.parse("""
                if order.amount > 1 {
                    set(order.status, "REVIEW")
                }
                """))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Language feature 'if/else' requires OttlDialect.EXTENDED");
        assertThatThrownBy(() -> Parser.parse("{ set(order.status, \"REVIEW\") }"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Language feature 'block' requires OttlDialect.EXTENDED");
        assertThatThrownBy(() -> Parser.parse("else { set(order.status, \"REVIEW\") }"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Language feature 'if/else' requires OttlDialect.EXTENDED");
    }

    @Test
    void reportsMalformedConditionals() {
        assertThatThrownBy(() -> extended("else { set(a, 1) }"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Unexpected 'else' without a preceding 'if'");
        assertThatThrownBy(() -> extended("if order.amount > 1 set(a, 1)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Expected '{' to open if block");
        assertThatThrownBy(() -> extended("if { set(a, 1) }"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Expected a condition after 'if'");
        assertThatThrownBy(() -> extended("if == 1 { set(a, 1) }"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Expected a value");
        assertThatThrownBy(() -> extended("if order.amount > 1 { set(a, 1)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("'}'");
        assertThatThrownBy(() -> extended("set(a, 1) }"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Unexpected '}'");
        assertThatThrownBy(() -> extended("if order.amount > 1 { set(a, 1) } else set(a, 2)"))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Expected '{' or 'if' after 'else'");
    }

    @Test
    void rejectsNestingPastTheConfiguredLimit() {
        String nested = """
                {
                    {
                        set(a, 1)
                    }
                }
                """;
        assertThatThrownBy(() -> Parser.parse(nested, OttlDialect.EXTENDED, 1))
                .isInstanceOf(OttlParseException.class)
                .hasMessageContaining("Nesting too deep (limit 1)");
    }

    @Test
    void existingEditorSyntaxStillParses() {
        OttlScript script = Parser.parse("""
                set(attributes["env"], "prod")
                delete_key(attributes, "secret")
                """);
        assertThat(script.statements()).hasSize(2);
    }
}
