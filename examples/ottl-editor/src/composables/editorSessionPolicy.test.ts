import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import {
    chooseRule,
    contextDraftKey,
    decideActivation,
    initialSelection,
    isDirty,
    revertToActive,
    ruleKey,
    sessionAfterActivation,
    switchContext,
    type RuleSnapshot,
    type SessionSnapshot
} from './editorSessionPolicy.ts';

const orderA: RuleSnapshot = {
    context: 'Order',
    ruleId: 'order-rules',
    source: 'set(order.status, "NEW")',
    version: 3
};
const orderB: RuleSnapshot = {
    context: 'Order',
    ruleId: 'audit-rules',
    source: 'set(order.priority, "LOW")',
    version: 1
};
const customer: RuleSnapshot = {
    context: 'Customer',
    ruleId: 'customer-rules',
    source: 'set(customer.tier, "STANDARD")',
    version: 2
};
const rules = [orderA, orderB, customer];

function sessionOn(rule: RuleSnapshot, source = rule.source): SessionSnapshot {
    const key = ruleKey(rule);
    return {
        context: rule.context,
        selectedKey: key,
        source,
        drafts: {},
        selectionByContext: { [rule.context]: key }
    };
}

describe('editor context and rule selection', () => {
    it('starts on the first rule', () => {
        const session = initialSelection(rules, ['Order', 'Customer']);
        assert.equal(session.context, 'Order');
        assert.equal(session.selectedKey, 'Order/order-rules');
        assert.equal(isDirty(rules, session.context, session.selectedKey, session.source), false);
    });

    it('switches from Order to Customer and back without mixing rule ids', () => {
        const onOrder = chooseRule(sessionOn(orderA), orderB);
        assert.equal(onOrder.selectedKey, 'Order/audit-rules');

        const onCustomer = switchContext(onOrder, rules, 'Customer');
        assert.equal(onCustomer.context, 'Customer');
        assert.equal(onCustomer.selectedKey, 'Customer/customer-rules');
        assert.equal(onCustomer.source, customer.source);

        const decision = decideActivation(onCustomer, rules);
        assert.equal(decision.ok, true);
        if (decision.ok) {
            assert.equal(decision.context, 'Customer');
            assert.equal(decision.ruleId, 'customer-rules');
        }

        const back = switchContext(onCustomer, rules, 'Order');
        assert.equal(back.context, 'Order');
        assert.equal(back.selectedKey, 'Order/audit-rules');
        assert.equal(back.source, orderB.source);
    });

    it('refuses to activate a rule id under a different context', () => {
        const mixed: SessionSnapshot = {
            ...sessionOn(orderA),
            context: 'Customer'
        };
        const decision = decideActivation(mixed, rules);
        assert.equal(decision.ok, false);
        if (!decision.ok) {
            assert.match(decision.message, /order-rules/);
            assert.match(decision.message, /Customer/);
        }
    });

    it('refuses activation when nothing is selected', () => {
        const empty = initialSelection([], ['Order']);
        const decision = decideActivation(empty, []);
        assert.deepEqual(decision, { ok: false, message: 'Select a rule before activating.' });
    });

    it('keeps unsaved source when leaving a context and restores it on return', () => {
        const edited = sessionOn(orderA, 'set(order.priority, "HIGH")');
        assert.equal(isDirty(rules, edited.context, edited.selectedKey, edited.source), true);

        const onCustomer = switchContext(edited, rules, 'Customer');
        assert.equal(onCustomer.source, customer.source);
        assert.equal(isDirty(rules, onCustomer.context, onCustomer.selectedKey, onCustomer.source), false);

        const back = switchContext(onCustomer, rules, 'Order');
        assert.equal(back.source, 'set(order.priority, "HIGH")');
        assert.equal(isDirty(rules, back.context, back.selectedKey, back.source), true);
    });

    it('reverts the active rule of the current context and drops the draft', () => {
        const edited = sessionOn(orderA, 'set(order.priority, "HIGH")');
        const reverted = revertToActive(edited, rules);
        assert.equal(reverted.ok, true);
        if (reverted.ok) {
            assert.equal(reverted.session.source, orderA.source);
            assert.equal(reverted.session.selectedKey, 'Order/order-rules');
            assert.equal(isDirty(rules, reverted.session.context, reverted.session.selectedKey, reverted.session.source), false);
        }
    });

    it('does not revert a selection that belongs to another context', () => {
        const mixed: SessionSnapshot = { ...sessionOn(orderA), context: 'Customer' };
        const reverted = revertToActive(mixed, rules);
        assert.equal(reverted.ok, false);
    });

    it('leaves the snapshot unchanged when activation fails', () => {
        const edited = sessionOn(orderA, 'set(order.status, )');
        const after = sessionAfterActivation(edited, { ok: false });
        assert.equal(after, edited);
        assert.equal(after.source, 'set(order.status, )');
        assert.equal(after.selectedKey, 'Order/order-rules');
    });

    it('selects the activated rule and clears the draft when the editor matches', () => {
        const edited = sessionOn(orderA, 'set(order.priority, "URGENT")');
        const updated: RuleSnapshot = { ...orderA, source: edited.source, version: 4 };
        const after = sessionAfterActivation(edited, { ok: true, rule: updated });
        assert.equal(after.selectedKey, 'Order/order-rules');
        assert.equal(after.context, 'Order');
        assert.equal(after.drafts['Order/order-rules'], undefined);
        assert.equal(updated.version, 4);
        assert.equal(isDirty([updated, orderB, customer], after.context, after.selectedKey, after.source), false);
    });

    it('keeps keystrokes made while activation was in flight', () => {
        const updated: RuleSnapshot = { ...orderA, source: 'set(order.priority, "URGENT")', version: 4 };
        const raced = sessionOn(orderA, 'set(order.priority, "LATER")');
        const after = sessionAfterActivation(raced, { ok: true, rule: updated });
        assert.equal(after.source, 'set(order.priority, "LATER")');
        assert.equal(after.drafts['Order/order-rules'], 'set(order.priority, "LATER")');
        assert.equal(isDirty([updated], after.context, after.selectedKey, after.source), true);
    });

    it('clears the selection when the context has no rules and restores a context draft', () => {
        const edited = sessionOn(orderA, 'set(order.priority, "HIGH")');
        const empty = switchContext(edited, [orderA], 'Customer');
        assert.equal(empty.context, 'Customer');
        assert.equal(empty.selectedKey, '');
        assert.equal(decideActivation(empty, [orderA]).ok, false);

        const typed = { ...empty, source: 'set(customer.tier, "GOLD")' };
        const elsewhere = switchContext(typed, [orderA], 'Shipment');
        const back = switchContext(elsewhere, [orderA], 'Customer');
        assert.equal(back.selectedKey, '');
        assert.equal(back.source, 'set(customer.tier, "GOLD")');
        assert.equal(back.drafts[contextDraftKey('Customer')], 'set(customer.tier, "GOLD")');
    });

    it('uses an empty editor when every context is empty', () => {
        const session = initialSelection([], []);
        assert.equal(session.selectedKey, '');
        assert.equal(session.source, '');
        const moved = switchContext(session, [], 'Order');
        assert.equal(moved.context, 'Order');
        assert.equal(moved.selectedKey, '');
    });
});
