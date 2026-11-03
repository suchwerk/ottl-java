<template>
    <q-page padding class="help-page">
        <div class="help-sheet">
            <div class="row items-center q-mb-md">
                <div>
                    <div class="text-h5">Syntax</div>
                    <div class="text-body2 text-grey-7">
                        The <code># context: Type</code> line at the top of the editor picks the Java type.
                        It is not part of the rule that runs.
                    </div>
                </div>
                <q-space />
                <q-btn flat color="primary" icon="edit" label="Editor" to="/" />
            </div>

            <q-tabs v-model="tab" align="left" active-color="primary" indicator-color="primary" narrow-indicator>
                <q-tab name="ottl" label="OTTL" />
                <q-tab name="extended" label="Extended" />
            </q-tabs>
            <q-separator />

            <q-tab-panels v-model="tab" animated>
                <q-tab-panel name="ottl">
                    <q-markdown :src="ottl" />
                </q-tab-panel>
                <q-tab-panel name="extended">
                    <q-markdown :src="extended" />
                </q-tab-panel>
            </q-tab-panels>
        </div>
    </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import ottl from '../help/ottl.md?raw';
import extended from '../help/extended.md?raw';

const tab = ref<'ottl' | 'extended'>('ottl');
</script>

<style scoped>
.help-sheet {
    max-width: 52rem;
    margin: 0 auto;
}
</style>
