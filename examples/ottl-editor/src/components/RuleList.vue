<template>
    <div class="column full-height">
        <q-toolbar class="bg-primary text-white">
            <q-toolbar-title class="text-subtitle1">Rules</q-toolbar-title>
            <q-btn flat round dense icon="refresh" @click="$emit('refresh')">
                <q-tooltip>Reload rules</q-tooltip>
            </q-btn>
        </q-toolbar>
        <q-list separator class="col scroll">
            <q-item
                v-for="rule in rules"
                :key="rule.context + '/' + rule.ruleId"
                clickable
                :active="selectedKey === rule.context + '/' + rule.ruleId"
                active-class="bg-blue-1 text-primary"
                :data-testid="'rule-' + rule.context + '-' + rule.ruleId"
                @click="$emit('select', rule)"
            >
                <q-item-section>
                    <q-item-label>{{ rule.ruleId }}</q-item-label>
                    <q-item-label caption>{{ rule.context }}</q-item-label>
                    <q-item-label
                        v-if="dirty && selectedKey === rule.context + '/' + rule.ruleId"
                        caption
                        class="text-orange-9"
                    >
                        Unsaved edits
                    </q-item-label>
                </q-item-section>
                <q-item-section side>
                    <q-badge color="secondary" :label="'v' + rule.version" />
                </q-item-section>
            </q-item>
        </q-list>
    </div>
</template>

<script setup lang="ts">
import type { RuleDto } from '../api/editor';

defineProps<{ rules: RuleDto[]; selectedKey: string; dirty: boolean }>();
defineEmits<{
    (e: 'select', rule: RuleDto): void;
    (e: 'refresh'): void;
}>();
</script>
