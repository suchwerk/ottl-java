/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp;

import io.ottl.lsp.analysis.Analyzer;
import io.ottl.lsp.analysis.TextDocument;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.SignatureHelp;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for the OTTL analyzer: diagnostics, completion, hover and signature help. */
class OttlAnalyzerTest {

    private final Analyzer analyzer = new Analyzer(TestMetadata.build());

    private static final String HEADER = "# context: Order\n";

    private TextDocument doc(String rule) {
        return new TextDocument("mem://test.ottl", HEADER + rule, 1);
    }

    private Position endOf(String rule) {
        return new Position(1, rule.length());
    }

    // ----- diagnostics -----

    @Test
    void validRuleHasNoDiagnostics() {
        List<Diagnostic> d = analyzer.diagnose(doc("set(order.status, \"REVIEW\") where IsHighValue(order.amount)"));
        assertThat(d).isEmpty();
    }

    @Test
    void unknownPropertyIsReported() {
        List<Diagnostic> d = analyzer.diagnose(doc("set(order.nope, \"X\")"));
        assertThat(d).hasSize(1);
        assertThat(d.get(0).getMessage()).contains("Unknown property 'nope'").contains("Order");
        assertThat(d.get(0).getRange().getStart().getLine()).isEqualTo(1);
    }

    @Test
    void writeToReadOnlyPropertyIsReported() {
        List<Diagnostic> d = analyzer.diagnose(doc("set(order.amount, 5)"));
        assertThat(d).hasSize(1);
        assertThat(d.get(0).getMessage()).contains("amount").contains("not writable");
    }

    @Test
    void unknownFunctionIsReported() {
        List<Diagnostic> d = analyzer.diagnose(doc("frobnicate(order.id)"));
        assertThat(d).anyMatch(x -> x.getMessage().contains("Unknown or disallowed function")
                && x.getMessage().contains("frobnicate"));
    }

    @Test
    void syntaxErrorIsReported() {
        List<Diagnostic> d = analyzer.diagnose(doc("set(order.status, )"));
        assertThat(d).hasSize(1);
        assertThat(d.get(0).getRange().getStart().getLine()).isEqualTo(1);
    }

    @Test
    void unknownNestedPropertyIsReported() {
        List<Diagnostic> d = analyzer.diagnose(doc("set(order.customer.nope, \"X\")"));
        assertThat(d).hasSize(1);
        assertThat(d.get(0).getMessage()).contains("Unknown property 'nope'").contains("Customer");
    }

    @Test
    void whereBlockValidatesPropertiesInsideTheBody() {
        assertThat(analyzer.diagnose(doc("""
                where order.amount > 10 {
                    set(order.status, "REVIEW")
                    set(order.priority, "HIGH")
                }
                """))).isEmpty();
        List<Diagnostic> d = analyzer.diagnose(doc("""
                where order.amount > 10 {
                    set(order.nope, "X")
                }
                """));
        assertThat(d).anyMatch(x -> x.getMessage().contains("Unknown property 'nope'"));
        List<Diagnostic> readOnly = analyzer.diagnose(doc("""
                where order.amount > 10 {
                    set(order.amount, 1)
                }
                """));
        assertThat(readOnly).anyMatch(x -> x.getMessage().contains("not writable"));
    }

    @Test
    void readingReadOnlyPropertyInConditionIsAllowed() {
        // amount is read-only, but reading it in a condition is fine.
        assertThat(analyzer.diagnose(doc("set(order.status, \"X\") where order.amount > 10"))).isEmpty();
    }

    // ----- completion -----

    @Test
    void completesTopLevelFunctionsAndKeywords() {
        List<String> labels = labels("se");
        assertThat(labels).contains("set");
    }

    @Test
    void completesRootProperties() {
        List<String> labels = labels("set(order.");
        assertThat(labels).containsExactlyInAnyOrder("id", "amount", "status", "priority", "customer");
    }

    @Test
    void completesNestedProperties() {
        List<String> labels = labels("set(order.customer.");
        assertThat(labels).containsExactlyInAnyOrder("name", "tier");
    }

    @Test
    void completionCarriesTypeAndWritability() {
        CompletionItem amount = complete("set(order.").stream()
                .filter(i -> i.getLabel().equals("amount")).findFirst().orElseThrow();
        assertThat(amount.getDetail()).contains("BigDecimal").contains("read-only");
    }

    // ----- hover -----

    @Test
    void hoverOnPropertyShowsTypeInfo() {
        String rule = "set(order.amount, 1)";
        Hover h = analyzer.hover(doc(rule), new Position(1, rule.indexOf("amount") + 1));
        assertThat(h).isNotNull();
        String md = h.getContents().getRight().getValue();
        assertThat(md).contains("amount").contains("BigDecimal").contains("Writable:** no");
    }

    @Test
    void hoverOnFunctionShowsSignature() {
        String rule = "set(order.status, \"X\")";
        Hover h = analyzer.hover(doc(rule), new Position(1, 1));
        assertThat(h).isNotNull();
        assertThat(h.getContents().getRight().getValue()).contains("set(");
    }

    // ----- signature help -----

    @Test
    void signatureHelpInsideSetShowsActiveParameter() {
        String rule = "set(order.status, ";
        SignatureHelp help = analyzer.signatureHelp(doc(rule), endOf(rule));
        assertThat(help.getSignatures()).hasSize(1);
        assertThat(help.getSignatures().get(0).getLabel()).contains("set(");
        assertThat(help.getActiveParameter()).isEqualTo(1);
    }

    // ----- helpers -----

    private List<CompletionItem> complete(String rule) {
        return analyzer.complete(doc(rule), endOf(rule)).getItems();
    }

    private List<String> labels(String rule) {
        return complete(rule).stream().map(CompletionItem::getLabel).collect(Collectors.toList());
    }
}
