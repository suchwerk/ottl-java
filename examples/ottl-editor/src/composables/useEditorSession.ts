import { computed, ref } from 'vue';
import { useQuasar } from 'quasar';
import { editorApi, type ContextDto, type FunctionDto, type RuleDto } from '../api/editor';

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

const contextNames = computed(() => contexts.value.map((c) => c.name));
const source = computed(() => stripDirective(editorText.value));
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

function onEditorChange(text: string) {
    editorText.value = text;
}

function selectRule(rule: RuleDto) {
    selectedKey.value = `${rule.context}/${rule.ruleId}`;
    context.value = rule.context;
    const text = buildEditorText(rule.context, rule.source);
    editorText.value = text;
    editor.value?.setText(text);
}

function setContext(next: string) {
    context.value = next;
    const text = buildEditorText(next, source.value);
    editorText.value = text;
    editor.value?.setText(text);
}

function currentRule(): RuleDto | undefined {
    return rules.value.find((r) => `${r.context}/${r.ruleId}` === selectedKey.value);
}

async function loadRules() {
    rules.value = await editorApi.rules();
}

async function load() {
    const metadata = await editorApi.contexts();
    contexts.value = metadata.contexts;
    functions.value = metadata.functions;
    await loadRules();

    const first = rules.value[0];
    if (first) {
        context.value = first.context;
        selectedKey.value = `${first.context}/${first.ruleId}`;
        initialText.value = buildEditorText(first.context, first.source);
    } else if (contexts.value[0]) {
        context.value = contexts.value[0].name;
        initialText.value = buildEditorText(context.value, '');
    }
    editorText.value = initialText.value;
    loaded.value = true;
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
        const rule = currentRule();
        if (!rule) {
            return;
        }
        try {
            const updated = await editorApi.activate(context.value, rule.ruleId, source.value);
            $q.notify({ type: 'positive', message: `Activated ${updated.ruleId} (v${updated.version}).` });
            await loadRules();
            selectedKey.value = `${updated.context}/${updated.ruleId}`;
        } catch (e) {
            $q.notify({ type: 'negative', message: (e as Error).message });
        }
    }

    function revert() {
        const rule = currentRule();
        if (!rule) {
            return;
        }
        const text = buildEditorText(rule.context, rule.source);
        editorText.value = text;
        editor.value?.setText(text);
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
