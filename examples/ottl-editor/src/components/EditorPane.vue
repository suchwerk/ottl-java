<template>
    <div ref="host" class="ottl-editor-host"></div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { startOttlEditor, type LspStatus, type OttlEditorHandle } from '../ottl/editorClient';

const props = defineProps<{ initialText: string }>();
const emit = defineEmits<{
    (e: 'status', status: LspStatus): void;
    (e: 'change', text: string): void;
}>();

const host = ref<HTMLElement | null>(null);
let handle: OttlEditorHandle | undefined;
let cancelled = false;
let queuedText: string | undefined;

function wsUrl(): string {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/ottl/lsp`;
}

onMounted(() => {
    const container = host.value;
    if (!container) {
        return;
    }
    void (async () => {
        const started = await startOttlEditor({
            container,
            initialCode: queuedText ?? props.initialText,
            wsUrl: wsUrl(),
            onStatus: (status) => emit('status', status)
        });
        if (cancelled) {
            await started.dispose();
            return;
        }
        started.onContentChanged((text) => emit('change', text));
        if (queuedText !== undefined) {
            started.setText(queuedText);
            queuedText = undefined;
        }
        handle = started;
        if (cancelled) {
            handle = undefined;
            await started.dispose();
        }
    })();
});

onBeforeUnmount(() => {
    cancelled = true;
    const current = handle;
    handle = undefined;
    void current?.dispose();
});

defineExpose({
    setText: (text: string) => {
        if (handle) {
            handle.setText(text);
        } else {
            queuedText = text;
        }
    },
    getText: () => handle?.getText() ?? queuedText ?? props.initialText
});
</script>

<style scoped>
.ottl-editor-host {
    width: 100%;
    height: 100%;
    min-height: 0;
}
</style>
