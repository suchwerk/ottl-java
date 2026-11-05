<template>
    <div class="column full-height q-pa-md q-gutter-md scroll">
        <div class="text-subtitle1">Test &amp; context</div>

        <q-select
            outlined
            dense
            label="Context type"
            data-testid="context-select"
            :model-value="context"
            :options="contextNames"
            @update:model-value="$emit('update:context', $event)"
        />

        <div class="text-caption text-grey">Sample input (JSON)</div>
        <q-input
            v-model="inputText"
            type="textarea"
            outlined
            dense
            autogrow
            input-class="ottl-mono"
            :rows="8"
        />

        <q-btn
            color="primary"
            icon="play_arrow"
            label="Run rule"
            :loading="running"
            data-testid="run-btn"
            @click="run"
        />

        <q-banner v-if="error" class="bg-red-1 text-red-9" dense rounded data-testid="run-error">
            {{ error }}
        </q-banner>

        <template v-if="result !== null">
            <div class="text-caption text-grey">Result</div>
            <pre class="ottl-result" data-testid="run-result">{{ result }}</pre>
        </template>
    </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { editorApi } from '../api/editor';

const props = defineProps<{
    context: string;
    contextNames: string[];
    source: string;
}>();
defineEmits<{ (e: 'update:context', value: string): void }>();

const inputText = ref<string>('{\n  "id": "o-1",\n  "amount": 1500,\n  "status": "PENDING",\n  "priority": "LOW"\n}');
const result = ref<string | null>(null);
const error = ref<string | null>(null);
const running = ref(false);

// Reset output when the selected context changes.
watch(
    () => props.context,
    () => {
        result.value = null;
        error.value = null;
    }
);

async function run() {
    running.value = true;
    error.value = null;
    result.value = null;
    try {
        const input = JSON.parse(inputText.value);
        const out = await editorApi.test(props.context, props.source, input);
        result.value = JSON.stringify(out, null, 2);
    } catch (e) {
        error.value = e instanceof Error ? e.message : String(e);
    } finally {
        running.value = false;
    }
}
</script>

<style scoped>
.ottl-result {
    white-space: pre-wrap;
    word-break: break-word;
    margin: 0;
    font-family: 'Fira Code', 'Consolas', monospace;
    font-size: 12px;
}
:deep(.ottl-mono) {
    font-family: 'Fira Code', 'Consolas', monospace;
    font-size: 12px;
}
</style>
