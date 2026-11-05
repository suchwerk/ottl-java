/**
 * Pure selection rules for the editor session.
 *
 * Invariants:
 * - The selected rule belongs to the active context, or nothing is selected.
 * - Activation uses that pair only. A rule id from another context is refused.
 * - Unsaved source is kept per rule (and per context when nothing is selected).
 * - A failed activation leaves the snapshot unchanged.
 */

export interface RuleSnapshot {
    context: string;
    ruleId: string;
    source: string;
    version: number;
}

export interface SessionSnapshot {
    context: string;
    selectedKey: string;
    /** OTTL source without the `# context:` directive. */
    source: string;
    drafts: Record<string, string>;
    /** Last selected rule key for each context. */
    selectionByContext: Record<string, string>;
}

export type ActivationDecision =
    | { ok: true; context: string; ruleId: string; source: string }
    | { ok: false; message: string };

export type ActivationOutcome = { ok: true; rule: RuleSnapshot } | { ok: false };

export function ruleKey(rule: { context: string; ruleId: string }): string {
    return `${rule.context}/${rule.ruleId}`;
}

export function contextDraftKey(context: string): string {
    return `context:${context}`;
}

export function normalizeSource(source: string): string {
    return source.trim();
}

export function findRule(rules: readonly RuleSnapshot[], key: string): RuleSnapshot | undefined {
    return rules.find((rule) => ruleKey(rule) === key);
}

export function isDirty(
    rules: readonly RuleSnapshot[],
    context: string,
    selectedKey: string,
    source: string
): boolean {
    const rule = findRule(rules, selectedKey);
    if (!rule || rule.context !== context) {
        return normalizeSource(source).length > 0;
    }
    return normalizeSource(rule.source) !== normalizeSource(source);
}

export function initialSelection(rules: readonly RuleSnapshot[], contextNames: readonly string[]): SessionSnapshot {
    const first = rules[0];
    if (first) {
        const key = ruleKey(first);
        return {
            context: first.context,
            selectedKey: key,
            source: first.source,
            drafts: {},
            selectionByContext: { [first.context]: key }
        };
    }
    return {
        context: contextNames[0] ?? '',
        selectedKey: '',
        source: '',
        drafts: {},
        selectionByContext: {}
    };
}

/** Remember the current editor text, then open the new context's own rule. */
export function switchContext(
    session: SessionSnapshot,
    rules: readonly RuleSnapshot[],
    nextContext: string
): SessionSnapshot {
    if (nextContext === session.context) {
        return session;
    }
    const drafts = { ...session.drafts };
    const selectionByContext = { ...session.selectionByContext };
    if (session.selectedKey !== '') {
        drafts[session.selectedKey] = session.source;
        selectionByContext[session.context] = session.selectedKey;
    } else if (session.context !== '') {
        drafts[contextDraftKey(session.context)] = session.source;
    }

    const inContext = rules.filter((rule) => rule.context === nextContext);
    const remembered = selectionByContext[nextContext];
    const selected =
        (remembered ? inContext.find((rule) => ruleKey(rule) === remembered) : undefined) ?? inContext[0];
    if (!selected) {
        return {
            context: nextContext,
            selectedKey: '',
            source: drafts[contextDraftKey(nextContext)] ?? '',
            drafts,
            selectionByContext
        };
    }
    const key = ruleKey(selected);
    return {
        context: nextContext,
        selectedKey: key,
        source: drafts[key] ?? selected.source,
        drafts,
        selectionByContext: { ...selectionByContext, [nextContext]: key }
    };
}

/** Open a rule from the list. The same rule keeps unsaved editor text. */
export function chooseRule(session: SessionSnapshot, rule: RuleSnapshot): SessionSnapshot {
    const key = ruleKey(rule);
    if (key === session.selectedKey && rule.context === session.context) {
        return session;
    }
    const drafts = { ...session.drafts };
    const selectionByContext = { ...session.selectionByContext };
    if (session.selectedKey !== '') {
        drafts[session.selectedKey] = session.source;
        selectionByContext[session.context] = session.selectedKey;
    } else if (session.context !== '') {
        drafts[contextDraftKey(session.context)] = session.source;
    }
    return {
        context: rule.context,
        selectedKey: key,
        source: drafts[key] ?? rule.source,
        drafts,
        selectionByContext: { ...selectionByContext, [rule.context]: key }
    };
}

export function decideActivation(session: SessionSnapshot, rules: readonly RuleSnapshot[]): ActivationDecision {
    if (session.selectedKey === '') {
        return { ok: false, message: 'Select a rule before activating.' };
    }
    const rule = findRule(rules, session.selectedKey);
    if (!rule) {
        return { ok: false, message: 'The selected rule is no longer available.' };
    }
    if (rule.context !== session.context) {
        return {
            ok: false,
            message: `Rule '${rule.ruleId}' belongs to context ${rule.context}, not ${session.context}.`
        };
    }
    return {
        ok: true,
        context: rule.context,
        ruleId: rule.ruleId,
        source: normalizeSource(session.source)
    };
}

export function revertToActive(
    session: SessionSnapshot,
    rules: readonly RuleSnapshot[]
): { ok: true; session: SessionSnapshot } | { ok: false; message: string } {
    const rule = findRule(rules, session.selectedKey);
    if (!rule || rule.context !== session.context) {
        return { ok: false, message: 'No active rule to revert.' };
    }
    const drafts = { ...session.drafts };
    delete drafts[session.selectedKey];
    return {
        ok: true,
        session: {
            ...session,
            context: rule.context,
            source: rule.source,
            drafts
        }
    };
}

/**
 * Apply a finished activation. A failure returns the same snapshot.
 * The editor source is kept: a newer keystroke must not be overwritten by the response.
 */
export function sessionAfterActivation(session: SessionSnapshot, outcome: ActivationOutcome): SessionSnapshot {
    if (!outcome.ok) {
        return session;
    }
    const updated = outcome.rule;
    const key = ruleKey(updated);
    const drafts = { ...session.drafts };
    if (normalizeSource(session.source) === normalizeSource(updated.source)) {
        delete drafts[key];
    } else {
        drafts[key] = session.source;
    }
    return {
        context: updated.context,
        selectedKey: key,
        source: session.source,
        drafts,
        selectionByContext: { ...session.selectionByContext, [updated.context]: key }
    };
}
