/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.analysis;

import io.ottl.lang.OttlDialect;
import io.ottl.lang.ast.Block;
import io.ottl.lang.ast.IfStatement;
import io.ottl.lang.ast.OttlScript;
import io.ottl.lang.ast.ScriptItem;
import io.ottl.lang.ast.Statement;
import io.ottl.lang.ast.WhereBlock;
import io.ottl.lang.parser.Lexer;
import io.ottl.lang.parser.OttlParseException;
import io.ottl.lang.parser.Parser;
import io.ottl.lang.parser.Token;
import io.ottl.lang.parser.TokenType;
import io.ottl.spi.EnumConstantRegistry;
import io.ottl.lsp.metadata.FunctionInfo;
import io.ottl.lsp.metadata.OttlMetadata;
import io.ottl.lsp.metadata.PropertyInfo;
import io.ottl.lsp.metadata.TypeInfo;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.ParameterInformation;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureInformation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The semantic brain of the Language Server. It reuses the real OTTL {@link Lexer} and {@link Parser}
 * for syntax, and the shared {@link OttlMetadata} (same registries as the engine) for property and
 * function checks, so diagnostics and completion always match what the engine will accept.
 */
public final class Analyzer {

    private static final Set<String> KEYWORDS =
            Set.of("where", "and", "or", "not", "true", "false", "nil");
    private static final List<String> COMPLETION_KEYWORDS =
            List.of("where", "and", "or", "not", "true", "false", "nil");

    private final OttlMetadata metadata;

    public Analyzer(OttlMetadata metadata) {
        this.metadata = metadata;
    }

    public OttlMetadata metadata() {
        return metadata;
    }

    /** Resolve the effective context type for a document (directive, else the first registered type). */
    public TypeInfo context(TextDocument doc) {
        String declared = doc.declaredContext();
        if (declared != null) {
            return metadata.context(declared).orElse(null);
        }
        List<TypeInfo> all = metadata.contexts();
        return all.isEmpty() ? null : all.get(0);
    }

    // =====================================================================================
    // Diagnostics
    // =====================================================================================

    public List<Diagnostic> diagnose(TextDocument doc) {
        String text = doc.text();
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<Token> tokens;
        try {
            tokens = Lexer.tokenize(text);
        } catch (OttlParseException e) {
            diagnostics.add(error(LspText.pointRange(e.line(), e.column(), 1), e.getMessage()));
            return diagnostics;
        }

        OttlScript script;
        try {
            script = Parser.parse(text, metadata.dialect(), metadata.maxNesting());
        } catch (OttlParseException e) {
            diagnostics.add(error(LspText.pointRange(e.line(), e.column(), 1), stripPrefix(e.getMessage())));
            return diagnostics; // cannot do semantic analysis on unparseable source
        }

        Set<Long> editorPositions = new java.util.HashSet<>();
        collectEditors(script.statements(), editorPositions);
        semanticDiagnostics(tokens, context(doc), editorPositions, diagnostics);
        return diagnostics;
    }

    private static void collectEditors(List<ScriptItem> items, Set<Long> out) {
        for (ScriptItem item : items) {
            collectEditors(item, out);
        }
    }

    private static void collectEditors(ScriptItem item, Set<Long> out) {
        switch (item) {
            case Statement s -> out.add(posKey(s.line(), s.column()));
            case WhereBlock block -> collectEditors(block.body(), out);
            case Block block -> collectEditors(block.statements(), out);
            case IfStatement branch -> {
                collectEditors(branch.thenBlock().statements(), out);
                branch.elseBranch().ifPresent(other -> collectEditors(other, out));
            }
        }
    }

    private static long posKey(int line, int col) {
        return ((long) line << 20) | (col & 0xFFFFF);
    }

    /** One call frame of an open function invocation while scanning tokens. */
    private static final class Frame {
        final String name;
        final boolean editor; // a top-level (statement) editor call
        int argIndex;
        boolean sawArg;

        Frame(String name, boolean editor) {
            this.name = name;
            this.editor = editor;
        }
    }

    private void semanticDiagnostics(List<Token> tokens, TypeInfo context,
                                     Set<Long> editorPositions, List<Diagnostic> out) {
        Deque<Frame> frames = new ArrayDeque<>();
        int i = 0;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            switch (t.type()) {
                case EOF -> {
                    return;
                }
                case LPAREN -> {
                    frames.push(new Frame(null, false)); // a grouping paren
                    i++;
                }
                case RPAREN -> {
                    if (!frames.isEmpty()) {
                        frames.pop();
                    }
                    i++;
                }
                case COMMA -> {
                    if (!frames.isEmpty()) {
                        frames.peek().argIndex++;
                    }
                    i++;
                }
                case IDENT -> {
                    markArg(frames);
                    if (next(tokens, i).type() == TokenType.LPAREN) {
                        // function call: validate name, push a frame for it
                        if (!metadata.isFunction(t.text()) && !KEYWORDS.contains(t.text())) {
                            out.add(error(LspText.tokenRange(t), "Unknown or disallowed function: '" + t.text() + "'"));
                        }
                        // A write target only exists for the statement editor (recorded by the parser).
                        boolean editor = editorPositions.contains(posKey(t.line(), t.column()));
                        frames.push(new Frame(t.text(), editor));
                        i += 2; // consume IDENT and LPAREN
                    } else if (KEYWORDS.contains(t.text()) || isControlKeyword(t.text())) {
                        i++;
                    } else {
                        boolean write = isWriteTarget(frames);
                        i = validatePath(tokens, i, context, write, out);
                    }
                }
                default -> i++;
            }
        }
    }

    /** Collect a dotted/keyed path starting at index {@code i}; validate it; return the next index. */
    private int validatePath(List<Token> tokens, int i, TypeInfo context, boolean write, List<Diagnostic> out) {
        List<Token> segTokens = new ArrayList<>();
        List<Boolean> isKey = new ArrayList<>();
        segTokens.add(tokens.get(i));
        isKey.add(false);
        i++;
        while (i < tokens.size()) {
            Token t = tokens.get(i);
            if (t.type() == TokenType.DOT && next(tokens, i).type() == TokenType.IDENT) {
                segTokens.add(tokens.get(i + 1));
                isKey.add(false);
                i += 2;
            } else if (t.type() == TokenType.LBRACKET) {
                // skip over a key access: [ key ]
                int j = i + 1;
                Token key = j < tokens.size() ? tokens.get(j) : null;
                segTokens.add(key);
                isKey.add(true);
                while (j < tokens.size() && tokens.get(j).type() != TokenType.RBRACKET
                        && tokens.get(j).type() != TokenType.EOF) {
                    j++;
                }
                i = (j < tokens.size() && tokens.get(j).type() == TokenType.RBRACKET) ? j + 1 : j;
            } else {
                break;
            }
        }

        if (reportEnum(segTokens, isKey, write, out)) {
            return i;
        }
        if (context == null) {
            return i; // no context selected: cannot validate properties
        }

        int start = 0;
        if (!segTokens.isEmpty() && !isKey.get(0)
                && segTokens.get(0).text().equals(metadata.contextVariable(context))) {
            start = 1;
        }
        TypeInfo curType = context;
        for (int s = start; s < segTokens.size(); s++) {
            if (isKey.get(s)) {
                return i; // dynamic keyed access; stop validating
            }
            if (curType == null) {
                return i; // navigated into a dynamic/leaf type
            }
            Token segTok = segTokens.get(s);
            String name = segTok.text();
            PropertyInfo prop = curType.property(name);
            if (prop == null) {
                out.add(error(LspText.tokenRange(segTok),
                        "Unknown property '" + name + "' on type " + curType.simpleName()));
                return i;
            }
            boolean isLast = s == segTokens.size() - 1;
            if (isLast && write && !prop.writable()) {
                out.add(error(LspText.tokenRange(segTok),
                        "Property '" + name + "' on type " + curType.simpleName() + " is not writable"));
                return i;
            }
            if (isLast) {
                return i;
            }
            Optional<TypeInfo> nextType = metadata.navigate(curType, name);
            if (nextType.isEmpty()) {
                if (metadata.isDynamicTypeName(prop.typeFqn())) {
                    return i; // navigating into a Map/JSON/Object is allowed dynamically
                }
                out.add(error(LspText.tokenRange(segTok),
                        "Cannot navigate into non-accessible type '" + prop.typeFqn() + "' via '" + name + "'"));
                return i;
            }
            curType = nextType.get();
        }
        return i;
    }

    /** @return true when the path names an allowlisted enum and any problem has been reported */
    private boolean reportEnum(List<Token> segTokens, List<Boolean> isKey, boolean write, List<Diagnostic> out) {
        List<String> names = new ArrayList<>();
        for (int s = 0; s < segTokens.size(); s++) {
            if (isKey.get(s) || segTokens.get(s) == null) {
                return false;
            }
            names.add(segTokens.get(s).text());
        }
        if (names.size() == 1) {
            Optional<EnumConstantRegistry.EnumType> type = metadata.enumType(names.get(0));
            if (type.isPresent() && !type.get().constants().isEmpty()) {
                out.add(error(LspText.tokenRange(segTokens.get(0)),
                        "Enum '" + type.get().simpleName() + "' needs a constant, for example "
                                + type.get().simpleName() + "." + type.get().constants().get(0)));
                return true;
            }
        }
        Optional<EnumConstantRegistry.Match> match = metadata.enumReference(names);
        if (match.isEmpty()) {
            return false;
        }
        EnumConstantRegistry.Match ref = match.get();
        if (ref.propertyAccess()) {
            out.add(error(LspText.tokenRange(segTokens.get(segTokens.size() - 1)),
                    "Enum " + ref.typeName() + " constants are values, not property bags"));
            return true;
        }
        if (ref.value() == null) {
            Token constant = segTokens.get(segTokens.size() - 1);
            out.add(error(LspText.tokenRange(constant),
                    "Unknown enum constant '" + ref.constant() + "' on " + ref.typeName()));
            return true;
        }
        if (write) {
            out.add(error(LspText.tokenRange(segTokens.get(segTokens.size() - 1)),
                    "Enum constant '" + ref.typeName() + "." + ref.constant() + "' is not writable"));
        }
        return true;
    }

    // =====================================================================================
    // Completion
    // =====================================================================================

    public CompletionList complete(TextDocument doc, Position pos) {
        String text = doc.text();
        int offset = LspText.offsetAt(text, pos);
        TypeInfo context = context(doc);

        int prefixStart = offset;
        while (prefixStart > 0 && isIdentPart(text.charAt(prefixStart - 1))) {
            prefixStart--;
        }
        String prefix = text.substring(prefixStart, offset);
        boolean member = prefixStart > 0 && text.charAt(prefixStart - 1) == '.';

        List<CompletionItem> items = new ArrayList<>();
        if (member) {
            List<String> baseSegs = collectBaseSegments(text, prefixStart - 1);
            Optional<EnumConstantRegistry.EnumType> enumType = metadata.enumType(String.join(".", baseSegs));
            if (enumType.isPresent()) {
                for (String constant : enumType.get().constants()) {
                    if (startsWith(constant, prefix)) {
                        items.add(enumConstantItem(enumType.get().simpleName(), constant));
                    }
                }
                return new CompletionList(false, items);
            }
            TypeInfo target = resolvePathType(context, baseSegs);
            if (target != null) {
                for (PropertyInfo p : target.properties()) {
                    if (startsWith(p.name(), prefix)) {
                        items.add(propertyItem(p));
                    }
                }
            }
        } else {
            if (context != null) {
                String var = metadata.contextVariable(context);
                if (startsWith(var, prefix)) {
                    items.add(contextItem(var, context));
                }
            }
            for (FunctionInfo fn : metadata.functions()) {
                if (startsWith(fn.name(), prefix)) {
                    items.add(functionItem(fn));
                }
            }
            for (String kw : completionKeywords()) {
                if (startsWith(kw, prefix)) {
                    items.add(keywordItem(kw));
                }
            }
            for (EnumConstantRegistry.EnumType type : metadata.enums()) {
                if (startsWith(type.simpleName(), prefix)) {
                    items.add(enumTypeItem(type));
                }
            }
        }
        return new CompletionList(false, items);
    }

    private TypeInfo resolvePathType(TypeInfo context, List<String> baseSegs) {
        if (context == null) {
            return null;
        }
        int start = 0;
        if (!baseSegs.isEmpty() && baseSegs.get(0).equals(metadata.contextVariable(context))) {
            start = 1;
        }
        TypeInfo cur = context;
        for (int s = start; s < baseSegs.size(); s++) {
            if (cur == null) {
                return null;
            }
            cur = metadata.navigate(cur, baseSegs.get(s)).orElse(null);
        }
        return cur;
    }

    // =====================================================================================
    // Hover
    // =====================================================================================

    public Hover hover(TextDocument doc, Position pos) {
        String text = doc.text();
        int offset = LspText.offsetAt(text, pos);
        List<Token> tokens;
        try {
            tokens = Lexer.tokenize(text);
        } catch (OttlParseException e) {
            return null;
        }
        int idx = tokenIndexAt(text, tokens, offset);
        if (idx < 0) {
            return null;
        }
        Token t = tokens.get(idx);
        if (t.type() != TokenType.IDENT) {
            return null;
        }
        boolean fieldName = idx > 0 && tokens.get(idx - 1).type() == TokenType.DOT;
        if (!fieldName) {
            String keyword = keywordDoc(t.text());
            if (keyword != null) {
                return markdownHover(keyword, LspText.tokenRange(t));
            }
            if (KEYWORDS.contains(t.text())) {
                return null;
            }
        }
        // Function?
        if (next(tokens, idx).type() == TokenType.LPAREN) {
            Optional<FunctionInfo> fn = metadata.function(t.text());
            if (fn.isPresent()) {
                return markdownHover(functionHoverText(fn.get()), LspText.tokenRange(t));
            }
            return null;
        }
        // Property path, or an enum constant (which is not a context type).
        TypeInfo context = context(doc);
        int tokenStart = LspText.offsetAt(text, new Position(t.line() - 1, t.column() - 1));
        List<String> baseSegs = collectBaseSegments(text, tokenStart);
        List<String> chain = new ArrayList<>(baseSegs);
        chain.add(t.text());

        Hover enumHover = enumHover(chain, t);
        if (enumHover != null) {
            return enumHover;
        }
        if (context == null) {
            return null;
        }

        int start = 0;
        if (!chain.isEmpty() && chain.get(0).equals(metadata.contextVariable(context))) {
            start = 1;
        }
        if (start >= chain.size()) {
            return markdownHover("Context variable `" + metadata.contextVariable(context)
                    + "` : **" + context.simpleName() + "**", LspText.tokenRange(t));
        }
        TypeInfo cur = context;
        for (int s = start; s < chain.size() - 1; s++) {
            if (cur == null) {
                return null;
            }
            cur = metadata.navigate(cur, chain.get(s)).orElse(null);
        }
        if (cur == null) {
            return null;
        }
        PropertyInfo prop = cur.property(t.text());
        if (prop == null) {
            return null;
        }
        return markdownHover(propertyHoverText(prop), LspText.tokenRange(t));
    }

    // =====================================================================================
    // Signature help
    // =====================================================================================

    public SignatureHelp signatureHelp(TextDocument doc, Position pos) {
        String text = doc.text();
        int offset = LspText.offsetAt(text, pos);

        int depth = 0;
        int activeParam = 0;
        int i = offset - 1;
        while (i >= 0) {
            char c = text.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth == 0) {
                    break;
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                activeParam++;
            }
            i--;
        }
        if (i < 0) {
            return null;
        }
        // identifier immediately before '('
        int nameEnd = i;
        while (nameEnd > 0 && Character.isWhitespace(text.charAt(nameEnd - 1))) {
            nameEnd--;
        }
        int nameStart = nameEnd;
        while (nameStart > 0 && isIdentPart(text.charAt(nameStart - 1))) {
            nameStart--;
        }
        String name = text.substring(nameStart, nameEnd);
        Optional<FunctionInfo> fnOpt = metadata.function(name);
        if (fnOpt.isEmpty()) {
            return null;
        }
        FunctionInfo fn = fnOpt.get();

        SignatureInformation sig = new SignatureInformation(fn.signatureLabel());
        List<ParameterInformation> params = new ArrayList<>();
        for (FunctionInfo.ParamInfo p : fn.parameters()) {
            String label = (p.typeSimpleName() == null || p.typeSimpleName().isBlank())
                    ? p.name() : p.typeSimpleName() + " " + p.name();
            params.add(new ParameterInformation(label));
        }
        sig.setParameters(params);
        if (!fn.documentation().isBlank()) {
            sig.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, fn.documentation()));
        }

        SignatureHelp help = new SignatureHelp();
        help.setSignatures(List.of(sig));
        help.setActiveSignature(0);
        help.setActiveParameter(params.isEmpty() ? 0 : Math.min(activeParam, params.size() - 1));
        return help;
    }

    // =====================================================================================
    // Helpers
    // =====================================================================================

    private static void markArg(Deque<Frame> frames) {
        if (!frames.isEmpty()) {
            frames.peek().sawArg = true;
        }
    }

    private static boolean isWriteTarget(Deque<Frame> frames) {
        Frame top = frames.peek();
        return top != null && top.editor && top.name != null && top.argIndex == 0;
    }

    /** Collect preceding dotted identifier segments ending just before index {@code dotIndex}. */
    private List<String> collectBaseSegments(String text, int dotIndex) {
        // dotIndex points at a '.' (completion) or at a token start (hover, exclusive end of base)
        List<String> rev = new ArrayList<>();
        int p = dotIndex;
        // if p points at '.', move to the char before it
        if (p >= 0 && p < text.length() && text.charAt(p) == '.') {
            // ok, walk segments before the dot
        } else {
            // hover case: p is the start offset of the hovered token; the base ends at the '.' before it
            if (p > 0 && text.charAt(p - 1) == '.') {
                p = p - 1;
            } else {
                return List.of();
            }
        }
        while (true) {
            int e = p; // exclusive end (position of '.')
            int s = e;
            while (s > 0 && isIdentPart(text.charAt(s - 1))) {
                s--;
            }
            if (s == e) {
                break; // no identifier before the dot (e.g. ']' or number)
            }
            rev.add(text.substring(s, e));
            if (s > 0 && text.charAt(s - 1) == '.') {
                p = s - 1;
            } else {
                break;
            }
        }
        java.util.Collections.reverse(rev);
        return rev;
    }

    private static int tokenIndexAt(String text, List<Token> tokens, int offset) {
        for (int k = 0; k < tokens.size(); k++) {
            Token t = tokens.get(k);
            if (t.type() == TokenType.EOF) {
                continue;
            }
            int startOffset = LspText.offsetAt(text, new Position(t.line() - 1, t.column() - 1));
            int endOffset = startOffset + Math.max(1, t.text().length());
            if (offset >= startOffset && offset <= endOffset) {
                return k;
            }
        }
        return -1;
    }

    private static Token next(List<Token> tokens, int i) {
        return i + 1 < tokens.size() ? tokens.get(i + 1) : tokens.get(tokens.size() - 1);
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static boolean startsWith(String candidate, String prefix) {
        return prefix.isEmpty() || candidate.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static Diagnostic error(Range range, String message) {
        Diagnostic d = new Diagnostic(range, message);
        d.setSeverity(DiagnosticSeverity.Error);
        d.setSource("ottl");
        return d;
    }

    private static String stripPrefix(String message) {
        // Parser messages are prefixed with "[line:col] "; drop it since the range carries position.
        int close = message.indexOf("] ");
        return close > 0 && message.startsWith("[") ? message.substring(close + 2) : message;
    }

    private Hover enumHover(List<String> chain, Token token) {
        Optional<EnumConstantRegistry.Match> match = metadata.enumReference(chain);
        if (match.isEmpty()) {
            Optional<EnumConstantRegistry.EnumType> type = chain.size() == 1
                    ? metadata.enumType(chain.get(0)) : Optional.empty();
            if (type.isEmpty()) {
                return null;
            }
            return markdownHover(enumTypeHoverText(type.get()), LspText.tokenRange(token));
        }
        EnumConstantRegistry.Match ref = match.get();
        if (ref.propertyAccess() || ref.value() == null) {
            return null;
        }
        Optional<EnumConstantRegistry.EnumType> type = metadata.enumType(ref.typeName());
        String fqn = type.map(EnumConstantRegistry.EnumType::fqn).orElse(ref.typeName());
        return markdownHover("**Enum constant:** `" + ref.typeName() + "." + ref.constant() + "`\n\n"
                + "**Declared in:** " + fqn, LspText.tokenRange(token));
    }

    private static String enumTypeHoverText(EnumConstantRegistry.EnumType type) {
        return "**Enum:** `" + type.simpleName() + "`\n\n"
                + "Constants are values, not properties.\n\n"
                + "**Constants:** `" + String.join("`, `", type.constants()) + "`\n\n"
                + "**Declared in:** " + type.fqn();
    }

    private static CompletionItem enumTypeItem(EnumConstantRegistry.EnumType type) {
        CompletionItem item = new CompletionItem(type.simpleName());
        item.setKind(CompletionItemKind.Enum);
        item.setDetail("enum");
        item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, enumTypeHoverText(type)));
        return item;
    }

    private static CompletionItem enumConstantItem(String typeName, String constant) {
        CompletionItem item = new CompletionItem(constant);
        item.setKind(CompletionItemKind.EnumMember);
        item.setDetail(typeName);
        return item;
    }

    private CompletionItem propertyItem(PropertyInfo p) {
        CompletionItem item = new CompletionItem(p.name());
        item.setKind(CompletionItemKind.Field);
        item.setDetail(p.typeSimpleName() + (p.writable() ? " (writable)" : " (read-only)"));
        item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, propertyHoverText(p)));
        return item;
    }

    private CompletionItem functionItem(FunctionInfo fn) {
        CompletionItem item = new CompletionItem(fn.name());
        item.setKind(CompletionItemKind.Function);
        item.setDetail(fn.signatureLabel());
        item.setInsertText(fn.name() + "($0)");
        item.setInsertTextFormat(InsertTextFormat.Snippet);
        item.setDocumentation(new MarkupContent(MarkupKind.MARKDOWN, functionHoverText(fn)));
        return item;
    }

    private static boolean isControlKeyword(String word) {
        return "if".equals(word) || "else".equals(word);
    }

    private List<String> completionKeywords() {
        List<String> keywords = new ArrayList<>(COMPLETION_KEYWORDS);
        if (metadata.dialect() == OttlDialect.EXTENDED) {
            keywords.add("if");
            keywords.add("else");
        }
        return keywords;
    }

    private static String keywordDoc(String word) {
        return switch (word) {
            case "where" -> "Guard. `statement where condition` runs one editor. "
                    + "`where condition { ... }` evaluates the condition once, then runs the block.";
            case "and", "or" -> "Boolean operator. `and` and `or` short-circuit.";
            case "not" -> "Negates a condition.";
            case "if" -> "Extended dialect. `if condition { ... } else if condition { ... } else { ... }` "
                    + "runs only the first matching branch. Requires `OttlDialect.EXTENDED`.";
            case "else" -> "Extended dialect. Follows an `if` block, either as `else { ... }` or `else if`.";
            default -> null;
        };
    }

    private static CompletionItem keywordItem(String kw) {
        CompletionItem item = new CompletionItem(kw);
        item.setKind(CompletionItemKind.Keyword);
        return item;
    }

    private CompletionItem contextItem(String var, TypeInfo context) {
        CompletionItem item = new CompletionItem(var);
        item.setKind(CompletionItemKind.Variable);
        item.setDetail(context.simpleName() + " (context)");
        return item;
    }

    private static Hover markdownHover(String markdown, Range range) {
        Hover hover = new Hover();
        hover.setContents(new MarkupContent(MarkupKind.MARKDOWN, markdown));
        hover.setRange(range);
        return hover;
    }

    private static String propertyHoverText(PropertyInfo p) {
        return "**Property:** `" + p.name() + "`\n\n"
                + "**Type:** " + p.typeSimpleName() + "\n\n"
                + "**Readable:** " + (p.readable() ? "yes" : "no") + "\n\n"
                + "**Writable:** " + (p.writable() ? "yes" : "no") + "\n\n"
                + "**Declared in:** " + p.declaringType();
    }

    private static String functionHoverText(FunctionInfo fn) {
        StringBuilder sb = new StringBuilder();
        sb.append("**Function:** `").append(fn.signatureLabel()).append("`\n\n");
        sb.append("**Category:** ").append(fn.category()).append("\n\n");
        if (fn.maxArgs() == Integer.MAX_VALUE) {
            sb.append("**Arguments:** ").append(fn.minArgs()).append("+\n\n");
        } else {
            sb.append("**Arguments:** ").append(fn.minArgs()).append("–").append(fn.maxArgs()).append("\n\n");
        }
        if (!fn.documentation().isBlank()) {
            sb.append(fn.documentation());
        }
        return sb.toString();
    }
}
