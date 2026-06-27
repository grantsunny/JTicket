import assert from 'node:assert/strict';
import test from 'node:test';

class TestElement {
    constructor(tagName) {
        this.tagName = tagName.toUpperCase();
        this.children = [];
        this.dataset = {};
        this.textContent = "";
        this.value = "";
        this.id = "";
    }

    appendChild(child) {
        this.children.push(child);
        child.parentNode = this;
        return child;
    }

    hasChildNodes() {
        return this.children.length > 0;
    }

    removeChild(child) {
        this.children = this.children.filter(candidate => candidate !== child);
        return child;
    }

    get firstChild() {
        return this.children[0] || null;
    }

    set innerHTML(value) {
        this.children = [];
        this.textContent = value;
    }
}

function createDocument() {
    const elements = new Map();

    return {
        createElement(tagName) {
            return new TestElement(tagName);
        },
        getElementById(id) {
            if (!elements.has(id)) {
                const element = new TestElement('div');
                element.id = id;
                elements.set(id, element);
            }
            return elements.get(id);
        }
    };
}

function response(body, status = 200) {
    return {
        ok: status >= 200 && status < 300,
        status,
        json: () => Promise.resolve(body),
        text: () => Promise.resolve(String(body))
    };
}

test('order page renders mocked event orders', async () => {
    globalThis.window = {};
    globalThis.document = createDocument();

    const calls = [];
    globalThis.fetch = (resource) => {
        calls.push(resource);
        if (resource === '/api/events') {
            return Promise.resolve(response([
                { id: 'event-1', name: 'Spring Concert' }
            ]));
        }
        if (resource === '/api/events/event-1/orders') {
            return Promise.resolve(response([
                {
                    id: '12345678-1111-2222-3333-444444444444',
                    userId: 'operator-viewed-user',
                    timestamp: '2026-06-28T01:00:00Z',
                    paymentChannel: 'paypal',
                    paymentTransactionId: 'txn-1',
                    paymentAmount: 2599,
                    seats: [
                        { row: 1, col: 2 },
                        { row: 1, col: 3 }
                    ]
                },
                {
                    id: '87654321-1111-2222-3333-444444444444',
                    userId: 'pending-user',
                    timestamp: '2026-06-28T02:00:00Z',
                    seats: [
                        { id: 'seat-without-row-col' }
                    ]
                }
            ]));
        }
        return Promise.resolve(response([], 404));
    };

    await import('../../main/resources/static/order.js');
    await window.stoneticket.refreshOrderEventList();
    await new Promise(resolve => setTimeout(resolve, 0));

    const eventSelect = document.getElementById('orderEventId');
    const summary = document.getElementById('orderSummary');
    const orderList = document.getElementById('orderList');

    assert.deepEqual(calls, ['/api/events', '/api/events/event-1/orders']);
    assert.equal(eventSelect.children.length, 2);
    assert.equal(eventSelect.children[0].textContent, 'Select Event');
    assert.equal(eventSelect.children[1].textContent, 'Spring Concert');
    assert.equal(summary.textContent, '2 orders');
    assert.equal(orderList.children.length, 2);

    const paidOrderCells = orderList.children[0].children.map(cell => cell.textContent);
    assert.deepEqual(paidOrderCells, [
        '12345678',
        'operator-viewed-user',
        new Date('2026-06-28T01:00:00Z').toLocaleString(),
        'paypal 25.99',
        '1-2, 1-3'
    ]);

    const unpaidOrderCells = orderList.children[1].children.map(cell => cell.textContent);
    assert.deepEqual(unpaidOrderCells, [
        '87654321',
        'pending-user',
        new Date('2026-06-28T02:00:00Z').toLocaleString(),
        'Unpaid',
        'seat-wit'
    ]);
});
