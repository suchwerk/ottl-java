<template>
    <q-page class="column no-wrap overflow-hidden" :style-fn="fillPage">
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
                            :ref="bindEditor"
                            :initial-text="editorText"
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
    </q-page>
</template>

<script setup lang="ts">
import { ref, type ComponentPublicInstance } from 'vue';
import EditorPane from '../components/EditorPane.vue';
import RuleList from '../components/RuleList.vue';
import TestPanel from '../components/TestPanel.vue';
import { useEditorSession, type EditorPaneApi } from '../composables/useEditorSession';

const leftSplit = ref(20);
const rightSplit = ref(62);

const {
    rules,
    loaded,
    context,
    contextNames,
    selectedKey,
    editorText,
    source,
    editor,
    lspStatus,
    onEditorChange,
    selectRule,
    setContext,
    loadRules
} = useEditorSession();

function bindEditor(instance: Element | ComponentPublicInstance | null) {
    editor.value = instance as EditorPaneApi | null;
}

/** Fill the area under the QLayout header instead of growing with content. */
function fillPage(offset: number) {
    const height = offset > 0 ? `calc(100vh - ${offset}px)` : '100vh';
    return { height, minHeight: height, maxHeight: height };
}
</script>
