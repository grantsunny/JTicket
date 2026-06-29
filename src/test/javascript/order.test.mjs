import assert from 'node:assert/strict';
import test from 'node:test';

class TestElement {
    constructor(tagName, preserveCase = false) {
        this.tagName = preserveCase ? tagName : tagName.toUpperCase();
        this.children = [];
        this.dataset = {};
        this.className = "";
        this.events = {};
        this.attributes = {};
        this.style = {
            setProperty: (name, value) => {
                this.style[name] = value;
            }
        };
        this.textContent = "";
        this.value = "";
        this.id = "";
        this.href = "";
        this.title = "";
        this.classList = {
            toggle: (className, enabled) => {
                const classes = new Set(this.className.split(/\s+/).filter(Boolean));
                if (enabled) classes.add(className);
                else classes.delete(className);
                this.className = Array.from(classes).join(" ");
            }
        };
    }

    appendChild(child) {
        this.children.push(child);
        child.parentNode = this;
        return child;
    }

    append(child) {
        if (typeof child === "string") {
            this.textContent += child;
            return;
        }
        this.appendChild(child);
    }

    addEventListener(eventName, listener) {
        if (!this.events[eventName]) this.events[eventName] = [];
        this.events[eventName].push(listener);
    }

    dispatchEvent(event) {
        if (!event.target) event.target = this;
        (this.events[event.type] || []).forEach(listener => listener.call(this, event));
        if (event.bubbles && this.parentNode)
            this.parentNode.dispatchEvent(event);
    }

    insertRow() {
        return this.appendChild(new TestElement("tr"));
    }

    insertCell() {
        return this.appendChild(new TestElement("td"));
    }

    hasChildNodes() {
        return this.children.length > 0;
    }

    removeChild(child) {
        this.children = this.children.filter(candidate => candidate !== child);
        child.parentNode = null;
        return child;
    }

    remove() {
        if (this.parentNode)
            this.parentNode.removeChild(this);
    }

    prepend(child) {
        this.children.unshift(child);
        child.parentNode = this;
        return child;
    }

    insertBefore(newChild, referenceChild) {
        const index = this.children.indexOf(referenceChild);
        if (index >= 0)
            this.children.splice(index, 0, newChild);
        else
            this.children.push(newChild);
        newChild.parentNode = this;
        return newChild;
    }

    replaceChild(newChild, oldChild) {
        const index = this.children.indexOf(oldChild);
        if (index >= 0) {
            this.children[index] = newChild;
            newChild.parentNode = this;
            oldChild.parentNode = null;
            if (newChild.id && this.elements)
                this.elements.set(newChild.id, newChild);
        }
        return oldChild;
    }

    cloneNode(withChildren = false) {
        const clone = new TestElement(this.tagName, this.tagName === this.tagName.toLowerCase());
        clone.dataset = {...this.dataset};
        clone.className = this.className;
        clone.attributes = {...this.attributes};
        clone.style = {
            ...this.style,
            setProperty: (name, value) => {
                clone.style[name] = value;
            }
        };
        clone.textContent = this.textContent;
        clone.value = this.value;
        clone.id = this.id;
        clone.href = this.href;
        clone.title = this.title;
        if (withChildren)
            this.children.forEach(child => clone.appendChild(child.cloneNode(true)));
        return clone;
    }

    get firstChild() {
        return this.children[0] || null;
    }

    get nextSibling() {
        if (!this.parentNode) return null;
        const siblings = this.parentNode.children;
        return siblings[siblings.indexOf(this) + 1] || null;
    }

    set innerHTML(value) {
        this.children = [];
        this.textContent = value;
        if (this.tagName === "defs" || this.tagName === "DEFS") return;

        if (typeof value === "string" && value.includes("<svg")) {
            this.textContent = "";
            const svg = new TestElement("svg", true);
            this.appendChild(svg);
            const rectPattern = /<rect\b([^>]*)>/g;
            let match;
            while ((match = rectPattern.exec(value)) !== null) {
                const rect = new TestElement("rect", true);
                const attrPattern = /([a-zA-Z:-]+)="([^"]*)"/g;
                let attr;
                while ((attr = attrPattern.exec(match[1])) !== null)
                    rect.setAttribute(attr[1], attr[2]);
                svg.appendChild(rect);
            }
        }
    }

    setAttribute(name, value) {
        this.attributes[name] = String(value);
        if (name === "class")
            this.className = String(value);
    }

    getAttribute(name) {
        return this.attributes[name] || null;
    }

    getBoundingClientRect() {
        return {left: 0, top: 0, width: 10, height: 10, bottom: 10};
    }

    querySelector(selector) {
        return querySelectorAll(this, selector)[0] || null;
    }

    querySelectorAll(selector) {
        return querySelectorAll(this, selector);
    }
}

function createDocument() {
    const elements = new Map();
    const root = new TestElement("document");
    root.elements = elements;

    return {
        createElement(tagName) {
            return new TestElement(tagName);
        },
        createElementNS(_namespace, tagName) {
            return new TestElement(tagName, true);
        },
        createTextNode(text) {
            const node = new TestElement("#text");
            node.textContent = text;
            return node;
        },
        getElementById(id) {
            if (!elements.has(id)) {
                const element = new TestElement('div');
                element.id = id;
                elements.set(id, element);
                root.appendChild(element);
            }
            return elements.get(id);
        },
        querySelector(selector) {
            return querySelectorAll(root, selector)[0] || null;
        },
        querySelectorAll(selector) {
            return querySelectorAll(root, selector);
        }
    };
}

function querySelectorAll(root, selector) {
    const results = [];

    function visit(element) {
        if (matches(element, selector))
            results.push(element);
        element.children.forEach(visit);
    }

    root.children.forEach(visit);
    return results;
}

function matches(element, selector) {
    if (selector === "svg")
        return element.tagName === "svg";
    if (selector === "defs")
        return element.tagName === "defs";
    if (selector === "line")
        return element.tagName === "line";
    if (selector === ".overlay")
        return element.className === "overlay";
    if (selector === "tr")
        return element.tagName === "TR";
    if (selector === "td")
        return element.tagName === "TD";
    if (selector === "#orderSeatsContainer td")
        return element.tagName === "TD" && hasAncestor(element, "orderSeatsContainer");
    if (selector === "#orderList tr")
        return element.tagName === "TR" && hasAncestor(element, "orderList");
    if (selector.startsWith("rect[areaid="))
        return element.tagName === "rect" && element.getAttribute("areaid") === selector.match(/"([^"]+)"/)[1];
    if (selector.startsWith("#orderVenueContainer rect"))
        return element.tagName === "rect"
            && hasAncestor(element, "orderVenueContainer")
            && (!selector.includes("areaid=") || element.getAttribute("areaid") === selector.match(/"([^"]+)"/)[1]);
    return false;
}

function hasAncestor(element, id) {
    let parent = element.parentNode;
    while (parent) {
        if (parent.id === id)
            return true;
        parent = parent.parentNode;
    }
    return false;
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
    globalThis.document.visibilityState = "visible";
    globalThis.MouseEvent = function MouseEvent(type, options = {}) {
        return {type, ...options};
    };
    let liveRefreshCallback;
    let liveRefreshDelay;
    globalThis.setInterval = (callback, delay) => {
        liveRefreshCallback = callback;
        liveRefreshDelay = delay;
        return { unref() {} };
    };
    globalThis.clearInterval = () => {};

    const calls = [];
    let eventOneOrderFetchCount = 0;
    globalThis.fetch = (resource) => {
        calls.push(resource);
        if (resource === '/api/events') {
            return Promise.resolve(response([
                { id: 'event-1', name: 'Spring Concert' },
                { id: 'event-no-session', name: 'No Session Festival' }
            ]));
        }
        if (resource === '/api/events/event-1/orders') {
            eventOneOrderFetchCount += 1;
            const paidAmount = eventOneOrderFetchCount > 1 ? 4099 : 2599;
            return Promise.resolve(response([
                {
                    id: '12345678-1111-2222-3333-444444444444',
                    eventId: 'event-1',
                    sessionId: 'session-1',
                    userId: 'operator-viewed-user',
                    timestamp: '2026-06-28T01:00:00Z',
                    paymentChannel: 'paypal',
                    paymentTransactionId: 'txn-1',
                    paymentAmount: paidAmount,
                    seats: [
                        { id: 'seat-1', areaId: 'area-1', row: 1, col: 2 },
                        { id: 'seat-2', areaId: 'area-1', row: 1, col: 3 }
                    ]
                },
                {
                    id: '87654321-1111-2222-3333-444444444444',
                    eventId: 'event-1',
                    sessionId: 'session-1',
                    userId: 'pending-user',
                    timestamp: '2026-06-28T02:00:00Z',
                    seats: [
                        { id: 'seat-without-row-col' }
                    ]
                }
            ]));
        }
        if (resource === '/api/events/event-no-session/orders') {
            return Promise.resolve(response([
                {
                    id: '99999999-1111-2222-3333-444444444444',
                    eventId: 'event-no-session',
                    userId: 'legacy-event-user',
                    timestamp: '2026-06-28T04:00:00Z',
                    seats: []
                }
            ]));
        }
        if (resource === '/api/events/event-1/statistics') {
            return Promise.resolve(response({
                eventId: 'event-1',
                totalSeats: 10,
                orderedSeats: 3,
                checkedInSeats: 1,
                uncheckedInSeats: 2
            }));
        }
        if (resource === '/api/events/event-no-session/statistics') {
            return Promise.resolve(response({
                eventId: 'event-no-session',
                totalSeats: 5,
                orderedSeats: 1,
                checkedInSeats: 0,
                uncheckedInSeats: 1
            }));
        }
        if (resource === '/api/events/event-1/sessions') {
            return Promise.resolve(response([
                { id: 'session-1', name: 'Evening' }
            ]));
        }
        if (resource === '/api/events/event-no-session/sessions') {
            return Promise.resolve(response([]));
        }
        if (resource === '/api/events/event-1/sessions/session-1/statistics') {
            return Promise.resolve(response({
                eventId: 'event-1',
                sessionId: 'session-1',
                totalSeats: 10,
                orderedSeats: 3,
                checkedInSeats: 1,
                uncheckedInSeats: 2
            }));
        }
        if (resource === '/api/events/event-1/areas') {
            return Promise.resolve(response([
                { id: 'area-1', name: 'Front Left' }
            ]));
        }
        if (resource === '/api/events/event-no-session/areas') {
            return Promise.resolve(response([
                { id: 'area-2', name: 'Field' }
            ]));
        }
        if (resource === '/api/events/event-1/sessions/session-1/areas/area-1/seats') {
            return Promise.resolve(response([
                {
                    id: 'seat-1',
                    areaId: 'area-1',
                    row: 1,
                    col: 2,
                    status: 'booked',
                    orderId: '12345678-1111-2222-3333-444444444444',
                    userId: 'operator-viewed-user',
                    priceName: 'Standard',
                    price: 2599
                },
                {
                    id: 'seat-2',
                    areaId: 'area-1',
                    row: 1,
                    col: 3,
                    status: 'checkedIn',
                    orderId: '12345678-1111-2222-3333-444444444444',
                    userId: 'operator-viewed-user',
                    checkedInTimestamp: '2026-06-28T03:00:00Z'
                },
                {
                    id: 'seat-3',
                    areaId: 'area-1',
                    row: 1,
                    col: 4,
                    status: 'open'
                }
            ]));
        }
        if (resource === '/api/events/event-1/venue/svg') {
            return Promise.resolve(response('<svg><rect areaid="area-1" x="0" y="0" width="10" height="10"></rect></svg>'));
        }
        if (resource === '/api/events/event-no-session/venue/svg') {
            return Promise.resolve(response('<svg><rect areaid="area-2" x="0" y="0" width="10" height="10"></rect></svg>'));
        }
        return Promise.resolve(response([], 404));
    };

    await import('../../main/resources/static/order.js');
    await window.jticket.refreshOrderEventList();
    await new Promise(resolve => setTimeout(resolve, 0));

    const eventSelect = document.getElementById('orderEventId');
    const sessionSelect = document.getElementById('orderSessionId');
    const summary = document.getElementById('orderSummary');
    const orderList = document.getElementById('orderList');
    const eventStatistics = document.getElementById('orderEventStatistics');
    const sessionStatistics = document.getElementById('orderSessionStatistics');

    assert(calls.includes('/api/events'));
    assert(calls.includes('/api/events/event-1/orders'));
    assert(calls.includes('/api/events/event-1/statistics'));
    assert(calls.includes('/api/events/event-1/sessions'));
    assert(calls.includes('/api/events/event-1/sessions/session-1/statistics'));
    assert(calls.includes('/api/events/event-1/areas'));
    assert.equal(eventSelect.children.length, 3);
    assert.equal(eventSelect.children[0].textContent, 'Select Event');
    assert.equal(eventSelect.children[1].textContent, 'Spring Concert');
    assert.equal(sessionSelect.children.length, 2);
    assert.equal(sessionSelect.children[1].textContent, 'Evening');
    assert.equal(summary.textContent, '2 orders');
    assert.equal(orderList.children.length, 2);
    assert(eventStatistics.children.map(row => row.textContent).includes('Paid amount: 25.99'));
    assert(sessionStatistics.children.map(row => row.textContent).includes('Paid amount: 25.99'));

    const paidOrderCells = orderList.children[0].children.map(cell => cell.textContent);
    assert.deepEqual(paidOrderCells, [
        '12345678',
        'operator-viewed-user',
        new Date('2026-06-28T01:00:00Z').toLocaleString(),
        'paypal 25.99',
        ''
    ]);
    assert.deepEqual(orderList.children[0].children[4].children
        .filter(child => child.tagName === 'A')
        .map(link => link.textContent), [
        'Front Left 1-2',
        'Front Left 1-3'
    ]);

    const unpaidOrderCells = orderList.children[1].children.map(cell => cell.textContent);
    assert.deepEqual(unpaidOrderCells, [
        '87654321',
        'pending-user',
        new Date('2026-06-28T02:00:00Z').toLocaleString(),
        'Unpaid',
        ''
    ]);

    orderList.children[0].children[4].children
        .find(child => child.tagName === 'A')
        .dispatchEvent({type: 'click', preventDefault() {}});
    await new Promise(resolve => setTimeout(resolve, 0));

    const areaStatistics = document.getElementById('orderAreaStatistics');
    const selectionDetails = document.getElementById('orderSelectionDetails');
    const seatCells = document.querySelectorAll('#orderSeatsContainer td');

    assert(calls.includes('/api/events/event-1/sessions/session-1/areas/area-1/seats'));
    assert.equal(liveRefreshDelay, 10000);
    assert.deepEqual(areaStatistics.children.map(row => row.textContent), [
        'Total seats: 3',
        'Booked seats: 2',
        'Checked in: 1'
    ]);
    assert.equal(seatCells.length, 3);
    assert.equal(seatCells[0].className, 'booked-seat selected-order-seat');
    assert.equal(seatCells[1].className, 'checked-in-seat');
    assert.equal(seatCells[2].className, 'open-seat');
    assert(selectionDetails.children.map(row => row.textContent).includes('Seat: Front Left 1-2'));
    assert.equal(orderList.children[0].className, 'selected-order-row');

    await window.jticket.selectOrderSession("");
    assert.equal(document.getElementById('orderSummary').textContent, '0 orders');
    assert.equal(document.getElementById('orderList').children.length, 0);
    await window.jticket.selectOrderSession("session-1");

    document.querySelector('#orderVenueContainer rect[areaid="area-1"]')
        .dispatchEvent({type: 'click', bubbles: true});
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(document.getElementById('orderSelectedArea').textContent, 'Area: Front Left');
    assert.equal(document.querySelectorAll('#orderSeatsContainer td').length, 3);
    assert.equal(document.querySelectorAll('#orderVenueContainer rect[areaid="area-1"]').length, 1);
    assert.equal(document.querySelectorAll('line').length, 4);

    const areaSeatCallCount = calls
        .filter(call => call === '/api/events/event-1/sessions/session-1/areas/area-1/seats')
        .length;
    await liveRefreshCallback();
    assert.equal(calls
        .filter(call => call === '/api/events/event-1/sessions/session-1/areas/area-1/seats')
        .length, areaSeatCallCount + 1);
    assert(eventStatistics.children.map(row => row.textContent).includes('Paid amount: 40.99'));
    assert(sessionStatistics.children.map(row => row.textContent).includes('Paid amount: 40.99'));
    assert.equal(document.getElementById('orderSummary').textContent, '2 orders');

    await window.jticket.selectOrderEvent("event-no-session");
    await new Promise(resolve => setTimeout(resolve, 0));
    assert.equal(document.getElementById('orderSummary').textContent, '0 orders');
    assert.equal(document.getElementById('orderList').children.length, 0);
    assert.equal(document.getElementById('orderSessionStatistics').textContent, 'No sessions for this event.');
});
