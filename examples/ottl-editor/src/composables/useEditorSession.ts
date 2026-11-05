import { computed, ref } from 'vue';
import { useQuasar } from 'quasar';
import { editorApi, type ContextDto, type FunctionDto, type RuleDto } from '../api/editor';
import {
    chooseRule,
    decideActivation,
    initialSelection,
    isDirty,
    revertToActive,
    ruleKey,
    sessionAfterActivation,
    switchContext,
    type RuleSnapshot,
    type SessionSnapshot
} from './editorSessionPolicy';

/** Methods the layout toolbar can call on the mounted editor page. */
export interface EditorPaneApi {
    setText(text: string): void;
}

const contexts = ref<ContextDto[]>([]);
const functions = ref<FunctionDto[]>([]);
const rules = ref<RuleDto[]>([]);
const loaded = ref(false);

const context = ref('Order');
const selectedKey = ref('');
const initialText = ref('');
const editorText = ref('');
const editor = ref<EditorPaneApi | null>(null);
const lspStatus = ref<'connecting' | 'connected' | 'disconnected'>('connecting');
const activating = ref(false);
const drafts = ref<Record<string, string>>({});
const selectionByContext = ref<Record<string, string>>({});

/** Bumped when the user changes context, rule, or reverts, so a late activation cannot retarget the editor. */
let epoch = 0;

const contextNames = computed(() => contexts.value.map((c) => c.name));
const source = computed(() => stripDirective(editorText.value));
const selectedRule = computed(() =>
    rules.value.find((rule) => ruleKey(rule) === selectedKey.value && rule.context === context.value)
);
const dirty = computed(() => isDirty(rules.value, context.value, selectedKey.value, source.value));
const statusColor = computed(() =>
    lspStatus.value === 'connected' ? 'positive' : lspStatus.value === 'connecting' ? 'warning' : 'negative'
);

function stripDirective(text: string): string {
    return text
        .split('\n')
        .filter((line) => !/^\s*#\s*context\s*:/i.test(line))
        .join('\n')
        .trim();
}

function buildEditorText(ctx: string, src: string): string {
    return `# context: ${ctx}\n${src}`;
}

function snapshot(): SessionSnapshot {
    return {
        context: context.value,
        selectedKey: selectedKey.value,
        source: source.value,
        drafts: drafts.value,
        selectionByContext: selectionByContext.value
    };
}

function publish(next: SessionSnapshot) {
    context.value = next.context;
    selectedKey.value = next.selectedKey;
    drafts.value = { ...next.drafts };
    selectionByContext.value = { ...next.selectionByContext };
    const text = buildEditorText(next.context, next.source);
    editorText.value = text;
    editor.value?.setText(text);
}

function onEditorChange(text: string) {
    editorText.value = text;
}

function selectRule(rule: RuleDto) {
    const current = snapshot();
    const next = chooseRule(current, rule);
    if (next === current) {
        return;
    }
    epoch += 1;
    publish(next);
}

function setContext(nextContext: string) {
    const current = snapshot();
    const next = switchContext(current, rules.value, nextContext);
    if (next === current) {
        return;
    }
    epoch += 1;
    publish(next);
}

async function loadRules() {
    rules.value = await editorApi.rules();
}

async function load() {
    const metadata = await editorApi.contexts();
    contexts.value = metadata.contexts;
    functions.value = metadata.functions;
    await loadRules();

    const initial = initialSelection(
        rules.value,
        contexts.value.map((item) => item.name)
    );
    epoch += 1;
    drafts.value = {};
    selectionByContext.value = { ...initial.selectionByContext };
    context.value = initial.context;
    selectedKey.value = initial.selectedKey;
    const text = buildEditorText(initial.context, initial.source);
    initialText.value = text;
    editorText.value = text;
    editor.value?.setText(text);
    loaded.value = true;
}

function toSnapshot(rule: RuleDto): RuleSnapshot {
    return {
        context: rule.context,
        ruleId: rule.ruleId,
        source: rule.source,
        version: rule.version
    };
}

/**
 * Shared editor session. The layout (toolbar) and the editor page both call this;
 * the refs live at module scope so they stay one session across the router-view boundary.
 */
export function useEditorSession() {
    const $q = useQuasar();

    async function validate() {
        try {
            const res = await editorApi.validate(context.value, source.value);
            if (res.valid) {
                $q.notify({ type: 'positive', message: 'Rule is valid.' });
            } else {
                $q.notify({ type: 'negative', message: res.error ?? 'Invalid rule.' });
            }
        } catch (e) {
            $q.notify({ type: 'negative', message: (e as Error).message });
        }
    }

    async function activate() {
        const decision = decideActivation(snapshot(), rules.value);
        if (!decision.ok) {
            $q.notify({ type: 'negative', message: decision.message });
            return;
        }
        if (activating.value) {
            return;
        }
        const started = epoch;
        activating.value = true;
        try {
            const updated = await editorApi.activate(decision.context, decision.ruleId, decision.source);
            await loadRules();
            if (started === epoch) {
                const next = sessionAfterActivation(snapshot(), { ok: true, rule: toSnapshot(updated) });
                context.value = next.context;
                selectedKey.value = next.selectedKey;
                drafts.value = { ...next.drafts };
                selectionByContext.value = { ...next.selectionByContext };
            }
            $q.notify({ type: 'positive', message: `Activated ${updated.ruleId} (v${updated.version}).` });
        } catch (e) {
            // The previous active version stays in place. Do not retarget the selection.
            $q.notify({ type: 'negative', message: (e as Error).message });
        } finally {
            activating.value = false;
        }
    }

    function revert() {
        const result = revertToActive(snapshot(), rules.value);
        if (!result.ok) {
            $q.notify({ type: 'warning', message: result.message });
            return;
        }
        epoch += 1;
        publish(result.session);
        $q.notify({ message: 'Reverted to the active rule.' });
    }

    function scrollToTest() {
        $q.notify({ message: 'Use the Run button in the right panel to execute the rule.' });
    }

    return {
        rules,
        loaded,
        context,
        contextNames,
        selectedKey,
        selectedRule,
        dirty,
        activating,
        initialText,
        editorText,
        source,
        editor,
        lspStatus,
        statusColor,
        onEditorChange,
        selectRule,
        setContext,
        loadRules,
        load,
        validate,
        activate,
        revert,
        scrollToTest
    };
}
