<template>
    <div class="column" style="height: 100vh">
        <!-- Toolbar -->
        <q-toolbar class="bg-primary text-white col-auto">
            <q-toolbar-title class="row items-center q-gutter-sm">
                <q-icon name="rule" />
                <span>OTTL Rule Editor</span>
            </q-toolbar-title>

            <q-chip
                :color="statusColor"
                text-color="white"
                :icon="lspStatus === 'connected' ? 'cloud_done' : 'cloud_off'"
                dense
                data-testid="lsp-status"
            >
                LSP: {{ lspStatus }}
            </q-chip>

            <q-separator vertical dark inset class="q-mx-sm" />

            <q-btn flat dense icon="fact_check" label="Validate" :disable="!loaded" @click="validate" />
            <q-btn flat dense icon="play_arrow" label="Test" :disable="!loaded" @click="scrollToTest" />
            <q-btn flat dense icon="save" label="Save &amp; Activate" :disable="!loaded" data-testid="activate-btn" @click="activate" />
            <q-btn flat dense icon="undo" label="Revert" :disable="!loaded" @click="revert" />

            <q-separator vertical dark inset class="q-mx-sm" />

            <q-btn flat round dense :icon="isDark ? 'light_mode' : 'dark_mode'" @click="toggleDark">
                <q-tooltip>Toggle light / dark</q-tooltip>
            </q-btn>
        </q-toolbar>

        <!-- 3-panel layout -->
        <q-splitter v-model="leftSplit" :limits="[12, 40]" class="col">
            <template #before>
                <rule-list
                    :rules="rules"
                    :selected-key="selectedKey"
                    @select="selectRule"
                    @refresh="loadRules"
                />
            </template>

            <template #after>
                <q-splitter v-model="rightSplit" :limits="[30, 85]" class="full-height">
                    <template #before>
                        <editor-pane
                            v-if="loaded"
                            ref="editor"
                            :initial-text="initialText"
                            @status="lspStatus = $event"
                            @change="onEditorChange"
                        />
                        <div v-else class="flex flex-center full-height">
                            <q-spinner size="40px" color="primary" />
                        </div>
                    </template>
                    <template #after>
                        <test-panel
                            :context="context"
                            :context-names="contextNames"
                            :source="source"
                            @update:context="setContext"
                        />
                    </template>
                </q-splitter>
            </template>
        </q-splitter>
    </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { Dark, useQuasar } from 'quasar';
import EditorPane from './components/EditorPane.vue';
import RuleList from './components/RuleList.vue';
import TestPanel from './components/TestPanel.vue';
import { editorApi, type ContextDto, type FunctionDto, type RuleDto } from './api/editor';

const $q = useQuasar();

const leftSplit = ref(20);
const rightSplit = ref(62);

const contexts = ref<ContextDto[]>([]);
const functions = ref<FunctionDto[]>([]);
const rules = ref<RuleDto[]>([]);
const loaded = ref(false);

const context = ref('Order');
const selectedKey = ref('');
const initialText = ref('');
const editorText = ref('');

const editor = ref<InstanceType<typeof EditorPane> | null>(null);

const isDark = computed(() => Dark.isActive);
const contextNames = computed(() => contexts.value.map((c) => c.name));
const source = computed(() => stripDirective(editorText.value));
const statusColor = computed(() =>
    lspStatus.value === 'connected' ? 'positive' : lspStatus.value === 'connecting' ? 'warning' : 'negative'
);

const lspStatus = ref<'connecting' | 'connected' | 'disconnected'>('connecting');

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

function toggleDark() {
    Dark.toggle();
}

async function loadRules() {
    rules.value = await editorApi.rules();
}

onMounted(async () => {
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
});
</script>
