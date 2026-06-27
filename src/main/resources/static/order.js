import {apiFetch, cleanUpContainer} from "./common.js";

window.stoneticket = {
    ...window.stoneticket,
    refreshOrderEventList,
    fetchEventOrders
}

function refreshOrderEventList() {
    const eventSelect = document.getElementById("orderEventId");
    if (!eventSelect) return;

    cleanUpContainer(eventSelect);
    eventSelect.appendChild(option("", "Select Event"));
    cleanUpContainer(document.getElementById("orderList"));
    setOrderSummary("");

    apiFetch('/api/events')
        .then(response => response.json())
        .then(events => {
            events.forEach(event => {
                eventSelect.appendChild(option(event.id, event.name));
            });

            if (events.length > 0) {
                eventSelect.value = events[0].id;
                fetchEventOrders(events[0].id);
            }
        })
        .catch(error => {
            console.error('Error fetching events:', error);
            setOrderSummary("Unable to load events.");
        });
}

function fetchEventOrders(eventId) {
    const orderList = document.getElementById("orderList");
    if (!orderList) return;

    cleanUpContainer(orderList);
    if (!eventId) {
        setOrderSummary("");
        return;
    }

    setOrderSummary("Loading orders...");
    apiFetch(`/api/events/${eventId}/orders`)
        .then(response => {
            if (response.status === 404) {
                setOrderSummary("Event not found.");
                return [];
            }
            if (!response.ok) {
                setOrderSummary(`Unable to load orders. Server returned ${response.status}.`);
                return [];
            }
            return response.json();
        })
        .then(orders => {
            renderOrders(orders || []);
        })
        .catch(error => {
            console.error('Error fetching orders:', error);
            setOrderSummary("Unable to load orders.");
        });
}

function renderOrders(orders) {
    const orderList = document.getElementById("orderList");
    cleanUpContainer(orderList);

    setOrderSummary(`${orders.length} order${orders.length === 1 ? "" : "s"}`);
    orders.forEach(order => {
        const row = document.createElement("tr");
        row.appendChild(cell(shortId(order.id)));
        row.appendChild(cell(order.userId || ""));
        row.appendChild(cell(formatDateTime(order.timestamp)));
        row.appendChild(cell(paymentText(order)));
        row.appendChild(cell(seatsText(order.seats || [])));
        orderList.appendChild(row);
    });
}

function setOrderSummary(text) {
    const summary = document.getElementById("orderSummary");
    if (summary) summary.textContent = text;
}

function cell(text) {
    const td = document.createElement("td");
    td.textContent = text;
    return td;
}

function option(value, text) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = text;
    return option;
}

function paymentText(order) {
    if (!order.paymentTransactionId)
        return "Unpaid";

    const amount = Number.isFinite(order.paymentAmount) ? (order.paymentAmount / 100).toFixed(2) : "";
    const channel = order.paymentChannel || "Paid";
    return amount ? `${channel} ${amount}` : channel;
}

function seatsText(seats) {
    if (seats.length === 0)
        return "";

    return seats
        .map(seat => {
            const label = [seat.row, seat.col].filter(value => value !== undefined && value !== null).join("-");
            return label || shortId(seat.id);
        })
        .join(", ");
}

function shortId(id) {
    return id ? id.substring(0, 8) : "";
}

function formatDateTime(value) {
    if (!value) return "";

    const date = new Date(value);
    if (Number.isNaN(date.getTime()))
        return value;

    return date.toLocaleString();
}
