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

function wsUrl(): string {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/ottl/lsp`;
}

onMounted(async () => {
    if (!host.value) {
        return;
    }
    handle = await startOttlEditor({
        container: host.value,
        initialCode: props.initialText,
        wsUrl: wsUrl(),
        onStatus: (s) => emit('status', s)
    });
    handle.onContentChanged((t) => emit('change', t));
});

onBeforeUnmount(async () => {
    await handle?.dispose();
    handle = undefined;
});

defineExpose({
    setText: (text: string) => handle?.setText(text),
    getText: () => handle?.getText() ?? ''
});
</script>

<style scoped>
.ottl-editor-host {
    width: 100%;
    height: 100%;
    min-height: 0;
}
</style>
