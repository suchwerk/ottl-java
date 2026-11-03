<template>
    <!-- view: fixed header, content offset below it. See quasar.dev/layout/layout -->
    <q-layout view="hHh lpR fFf">
        <q-header elevated class="bg-primary text-white">
            <q-toolbar>
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

                <q-btn flat dense icon="fact_check" label="Validate" :disable="!loaded || !onEditor" @click="validate" />
                <q-btn flat dense icon="play_arrow" label="Test" :disable="!loaded || !onEditor" @click="scrollToTest" />
                <q-btn
                    flat
                    dense
                    icon="save"
                    label="Save &amp; Activate"
                    :disable="!loaded || !onEditor"
                    data-testid="activate-btn"
                    @click="activate"
                />
                <q-btn flat dense icon="undo" label="Revert" :disable="!loaded || !onEditor" @click="revert" />

                <q-separator vertical dark inset class="q-mx-sm" />

                <q-btn flat dense icon="help_outline" label="Help" to="/help" />

                <q-separator vertical dark inset class="q-mx-sm" />

                <q-btn flat round dense :icon="isDark ? 'light_mode' : 'dark_mode'" @click="toggleDark">
                    <q-tooltip>Toggle light / dark</q-tooltip>
                </q-btn>
            </q-toolbar>
        </q-header>

        <q-page-container>
            <router-view />
        </q-page-container>
    </q-layout>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { Dark } from 'quasar';
import { useRoute } from 'vue-router';
import { useEditorSession } from '../composables/useEditorSession';

const route = useRoute();
const onEditor = computed(() => route.name === 'editor');
const { loaded, lspStatus, statusColor, validate, activate, revert, scrollToTest, load } = useEditorSession();
const isDark = computed(() => Dark.isActive);

function toggleDark() {
    Dark.toggle();
}

onMounted(() => {
    void load();
});
</script>
