import {apiFetch, cleanUpContainer, drawEventVenueEx} from "./common.js";

const orderState = {
    eventId: "",
    sessionId: "",
    areaId: "",
    seatId: "",
    orderId: "",
    pendingSeatId: "",
    events: [],
    sessions: [],
    areas: [],
    orders: [],
    seats: []
};

window.stoneticket = {
    ...window.stoneticket,
    refreshOrderEventList,
    fetchEventOrders,
    selectOrderEvent,
    selectOrderSession,
    focusOrderSeat,
    highlightOrderSeat
}

function refreshOrderEventList() {
    const eventSelect = document.getElementById("orderEventId");
    if (!eventSelect) return Promise.resolve();

    resetPage();
    cleanUpContainer(eventSelect);
    eventSelect.appendChild(option("", "Select Event"));

    return apiFetch('/api/events')
        .then(response => response.json())
        .then(events => {
            orderState.events = events || [];
            orderState.events.forEach(event => {
                eventSelect.appendChild(option(event.id, event.name));
            });

            if (orderState.events.length > 0) {
                eventSelect.value = orderState.events[0].id;
                return selectOrderEvent(orderState.events[0].id);
            }
        })
        .catch(error => {
            console.error('Error fetching events:', error);
            setOrderSummary("Unable to load events.");
        });
}

function selectOrderEvent(eventId) {
    orderState.eventId = eventId || "";
    orderState.sessionId = "";
    orderState.areaId = "";
    orderState.seatId = "";
    orderState.orderId = "";
    orderState.pendingSeatId = "";
    orderState.sessions = [];
    orderState.areas = [];
    orderState.orders = [];
    orderState.seats = [];

    cleanUpContainer(document.getElementById("orderList"));
    cleanUpContainer(document.getElementById("orderSeatsContainer"));
    const venueContainer = resetVenueContainer();
    populateSessions([]);
    setText("orderSelectedArea", "Select an area on the venue map");
    setText("orderEventStatistics", eventId ? "Loading event..." : "Select an event");
    setText("orderSessionStatistics", "Select a session");
    setText("orderAreaStatistics", "Select an area on the venue map");
    setText("orderSelectionDetails", "No seat selected");
    setOrderSummary("");

    if (!eventId) return Promise.resolve();

    drawEventVenueEx(eventId, venueContainer, function (_eventId, areaId) {
        selectOrderArea(areaId);
    });

    return Promise.all([
        fetchEventOrders(eventId),
        fetchEventStatistics(eventId),
        fetchAreas(eventId),
        fetchSessions(eventId)
    ]);
}

function selectOrderSession(sessionId) {
    orderState.sessionId = sessionId || "";
    orderState.seatId = "";
    orderState.orderId = "";
    orderState.pendingSeatId = "";
    orderState.seats = [];

    cleanUpContainer(document.getElementById("orderSeatsContainer"));
    setText("orderSelectionDetails", "No seat selected");
    setText("orderAreaStatistics", orderState.areaId ? "Select a session" : "Select an area on the venue map");
    renderOrders();

    if (!sessionId) {
        setText("orderSessionStatistics", "Select a session");
        return Promise.resolve();
    }

    const work = [fetchSessionStatistics(orderState.eventId, sessionId)];
    if (orderState.areaId)
        work.push(selectOrderArea(orderState.areaId));
    return Promise.all(work);
}

function fetchEventOrders(eventId) {
    if (!eventId) {
        orderState.orders = [];
        renderOrders();
        return Promise.resolve();
    }

    setOrderSummary("Loading orders...");
    return apiFetch(`/api/events/${eventId}/orders`)
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
            if (orderState.eventId !== eventId) return;
            orderState.orders = orders || [];
            renderOrders();
            updateEventPaidAmount();
            updateSessionPaidAmount();
        })
        .catch(error => {
            console.error('Error fetching orders:', error);
            setOrderSummary("Unable to load orders.");
        });
}

function fetchEventStatistics(eventId) {
    return apiFetch(`/api/events/${eventId}/statistics`)
        .then(response => response.ok ? response.json() : null)
        .then(statistics => {
            if (orderState.eventId !== eventId) return;
            renderStatistics("orderEventStatistics", statistics, paidAmount(orderState.orders));
        })
        .catch(error => {
            console.error('Error fetching event statistics:', error);
            setText("orderEventStatistics", "Unable to load event statistics.");
        });
}

function fetchSessionStatistics(eventId, sessionId) {
    return apiFetch(`/api/events/${eventId}/sessions/${sessionId}/statistics`)
        .then(response => response.ok ? response.json() : null)
        .then(statistics => {
            if (orderState.eventId !== eventId || orderState.sessionId !== sessionId) return;
            renderStatistics("orderSessionStatistics", statistics, paidAmount(sessionOrders()));
        })
        .catch(error => {
            console.error('Error fetching session statistics:', error);
            setText("orderSessionStatistics", "Unable to load session statistics.");
        });
}

function fetchSessions(eventId) {
    return apiFetch(`/api/events/${eventId}/sessions`)
        .then(response => response.ok ? response.json() : [])
        .then(sessions => {
            if (orderState.eventId !== eventId) return;
            orderState.sessions = sessions || [];
            populateSessions(orderState.sessions);
            if (orderState.sessions.length > 0) {
                const sessionSelect = document.getElementById("orderSessionId");
                sessionSelect.value = orderState.sessions[0].id;
                return selectOrderSession(orderState.sessions[0].id);
            }
        })
        .catch(error => {
            console.error('Error fetching sessions:', error);
            populateSessions([]);
            setText("orderSessionStatistics", "Unable to load sessions.");
        });
}

function fetchAreas(eventId) {
    return apiFetch(`/api/events/${eventId}/areas`)
        .then(response => response.ok ? response.json() : [])
        .then(areas => {
            if (orderState.eventId !== eventId) return;
            orderState.areas = areas || [];
            renderSelectedAreaLabel();
            renderOrders();
        })
        .catch(error => {
            console.error('Error fetching areas:', error);
            orderState.areas = [];
        });
}

function selectOrderArea(areaId) {
    orderState.areaId = areaId || "";
    orderState.seatId = "";
    orderState.orderId = "";
    orderState.seats = [];

    const area = findArea(areaId);
    setText("orderSelectedArea", area ? `Area: ${area.name}` : "Selected area");
    cleanUpContainer(document.getElementById("orderSeatsContainer"));
    setText("orderAreaStatistics", "Loading area...");
    setText("orderSelectionDetails", "No seat selected");

    if (!areaId) {
        setText("orderAreaStatistics", "Select an area on the venue map");
        return Promise.resolve();
    }
    if (!orderState.eventId || !orderState.sessionId) {
        setText("orderAreaStatistics", "Select a session to view area seats.");
        return Promise.resolve();
    }

    const requestEventId = orderState.eventId;
    const requestSessionId = orderState.sessionId;
    return apiFetch(`/api/events/${requestEventId}/sessions/${requestSessionId}/areas/${areaId}/seats`)
        .then(response => response.ok ? response.json() : [])
        .then(seats => {
            if (orderState.eventId !== requestEventId
                || orderState.sessionId !== requestSessionId
                || orderState.areaId !== areaId) return;
            orderState.seats = seats || [];
            renderAreaStatistics(orderState.seats);
            renderSeats(orderState.seats);
            if (orderState.pendingSeatId) {
                const pendingSeatId = orderState.pendingSeatId;
                orderState.pendingSeatId = "";
                highlightOrderSeat(pendingSeatId);
            }
        })
        .catch(error => {
            console.error('Error fetching area seats:', error);
            orderState.seats = [];
            setText("orderAreaStatistics", "Unable to load area seats.");
        });
}

function renderOrders() {
    const orderList = document.getElementById("orderList");
    if (!orderList) return;

    cleanUpContainer(orderList);
    const orders = sessionOrders();
    setOrderSummary(`${orders.length} order${orders.length === 1 ? "" : "s"}`);

    orders.forEach(order => {
        const row = document.createElement("tr");
        row.dataset.orderid = order.id || "";
        row.appendChild(cell(shortId(order.id)));
        row.appendChild(cell(order.userId || ""));
        row.appendChild(cell(formatDateTime(order.timestamp)));
        row.appendChild(cell(paymentText(order)));
        row.appendChild(orderSeatsCell(order));
        orderList.appendChild(row);
    });

    highlightOrderRow(orderState.orderId);
}

function renderSeats(seats) {
    const seatsContainer = document.getElementById("orderSeatsContainer");
    cleanUpContainer(seatsContainer);

    if (seats.length === 0) {
        seatsContainer.textContent = "No seats found in this area.";
        return;
    }

    const table = document.createElement("table");
    table.className = "seats-table order-status-seat-table";
    const seatRows = seats.reduce((rows, seat) => {
        if (!rows[seat.row]) rows[seat.row] = [];
        rows[seat.row].push(seat);
        return rows;
    }, {});

    Object.keys(seatRows).sort((a, b) => Number(a) - Number(b)).forEach(row => {
        const tr = table.insertRow();
        seatRows[row].sort((a, b) => a.col - b.col).forEach(seat => {
            const td = tr.insertCell();
            td.textContent = seatLabel(seat);
            td.dataset.seatid = seat.id || "";
            td.dataset.orderid = seat.orderId || "";
            td.dataset.status = seat.status || "open";
            td.className = seatClass(seat);
            td.title = seatTooltip(seat);
            td.addEventListener("click", function () {
                selectSeat(seat);
            });
        });
    });

    seatsContainer.appendChild(table);
}

function selectSeat(seat) {
    orderState.seatId = seat.id || "";
    orderState.orderId = seat.orderId || "";
    markSeatSelection(orderState.seatId);
    highlightOrderRow(orderState.orderId);
    renderSelectionDetails(seat);
}

function focusOrderSeat(areaId, seatId, orderId) {
    orderState.pendingSeatId = seatId || "";
    orderState.orderId = orderId || "";
    highlightOrderRow(orderId);

    if (areaId && areaId !== orderState.areaId) {
        const rect = document.querySelector(`#orderVenueContainer rect[areaid="${areaId}"]`);
        if (rect) {
            rect.dispatchEvent(new MouseEvent("click", {bubbles: true}));
            return;
        }
    }

    if (areaId && areaId === orderState.areaId) {
        highlightOrderSeat(seatId);
        return;
    }

    if (areaId)
        selectOrderArea(areaId);
}

function highlightOrderSeat(seatId) {
    const seat = orderState.seats.find(candidate => candidate.id === seatId);
    if (seat)
        selectSeat(seat);
    else
        markSeatSelection(seatId);
}

function markSeatSelection(seatId) {
    document.querySelectorAll("#orderSeatsContainer td").forEach(td => {
        td.classList.toggle("selected-order-seat", td.dataset.seatid === seatId);
    });
}

function highlightOrderRow(orderId) {
    document.querySelectorAll("#orderList tr").forEach(row => {
        row.classList.toggle("selected-order-row", !!orderId && row.dataset.orderid === orderId);
    });
}

function orderSeatsCell(order) {
    const td = document.createElement("td");
    const seats = order.seats || [];
    if (seats.length === 0) return td;

    seats.forEach((seat, index) => {
        if (index > 0)
            td.appendChild(document.createTextNode(", "));

        const link = document.createElement("a");
        link.href = "#";
        link.textContent = seatLocationText(seat);
        link.addEventListener("click", function (event) {
            event.preventDefault();
            focusOrderSeat(seat.areaId, seat.id, order.id);
        });
        td.appendChild(link);
    });
    return td;
}

function populateSessions(sessions) {
    const sessionSelect = document.getElementById("orderSessionId");
    if (!sessionSelect) return;

    cleanUpContainer(sessionSelect);
    sessionSelect.appendChild(option("", "Select Session"));
    sessions.forEach(session => {
        sessionSelect.appendChild(option(session.id, session.name || shortId(session.id)));
    });
}

function renderStatistics(containerId, statistics, income) {
    if (!statistics) {
        setText(containerId, "No statistics available.");
        return;
    }

    const rows = [
        `Total seats: ${numberText(statistics.totalSeats)}`,
        `Booked seats: ${numberText(statistics.orderedSeats)}`,
        `Checked in: ${numberText(statistics.checkedInSeats)}`
    ];
    if (income !== undefined)
        rows.push(`Paid amount: ${moneyText(income)}`);

    setLines(containerId, rows);
}

function renderAreaStatistics(seats) {
    const total = seats.length;
    const booked = seats.filter(seat => seat.status === "booked" || seat.status === "checkedIn").length;
    const checkedIn = seats.filter(seat => seat.status === "checkedIn").length;
    setLines("orderAreaStatistics", [
        `Total seats: ${total}`,
        `Booked seats: ${booked}`,
        `Checked in: ${checkedIn}`
    ]);
}

function renderSelectionDetails(seat) {
    const rows = [
        `Seat: ${seatLocationText(seat)}`,
        `Status: ${statusText(seat.status)}`,
        `Price: ${seat.priceName || ""}${seat.price ? " " + moneyText(seat.price) : ""}`.trim()
    ];

    if (seat.orderId)
        rows.push(`Order: ${shortId(seat.orderId)}`);
    if (seat.userId)
        rows.push(`User: ${seat.userId}`);
    if (seat.checkedInTimestamp)
        rows.push(`Checked in: ${formatDateTime(seat.checkedInTimestamp)}`);

    setLines("orderSelectionDetails", rows);
}

function updateEventPaidAmount() {
    const eventStatistics = document.getElementById("orderEventStatistics");
    if (!eventStatistics || !orderState.eventId) return;
    fetchEventStatistics(orderState.eventId);
}

function updateSessionPaidAmount() {
    if (orderState.eventId && orderState.sessionId)
        fetchSessionStatistics(orderState.eventId, orderState.sessionId);
}

function resetPage() {
    orderState.eventId = "";
    orderState.sessionId = "";
    orderState.areaId = "";
    orderState.seatId = "";
    orderState.orderId = "";
    orderState.pendingSeatId = "";
    orderState.events = [];
    orderState.sessions = [];
    orderState.areas = [];
    orderState.orders = [];
    orderState.seats = [];

    cleanUpContainer(document.getElementById("orderList"));
    cleanUpContainer(document.getElementById("orderSeatsContainer"));
    resetVenueContainer();
    populateSessions([]);
    setText("orderEventStatistics", "Select an event");
    setText("orderSessionStatistics", "Select a session");
    setText("orderAreaStatistics", "Select an area on the venue map");
    setText("orderSelectionDetails", "No seat selected");
    setText("orderSelectedArea", "Select an area on the venue map");
    setOrderSummary("");
}

function sessionOrders() {
    if (!orderState.sessionId)
        return orderState.orders;
    return orderState.orders.filter(order => order.sessionId === orderState.sessionId);
}

function paidAmount(orders) {
    return (orders || []).reduce((total, order) => {
        if (Number.isFinite(order.paymentAmount))
            return total + order.paymentAmount;
        return total;
    }, 0);
}

function findArea(areaId) {
    return orderState.areas.find(area => area.id === areaId);
}

function setOrderSummary(text) {
    setText("orderSummary", text);
}

function renderSelectedAreaLabel() {
    const area = findArea(orderState.areaId);
    if (orderState.areaId)
        setText("orderSelectedArea", area ? `Area: ${area.name}` : "Selected area");
}

function resetVenueContainer() {
    const container = document.getElementById("orderVenueContainer");
    if (!container) return container;

    const parent = container.parentNode;
    if (!parent || !container.cloneNode) {
        cleanUpContainer(container);
        return container;
    }

    const replacement = container.cloneNode(false);
    parent.replaceChild(replacement, container);
    return replacement;
}

function setText(id, text) {
    const element = document.getElementById(id);
    if (element) element.textContent = text;
}

function setLines(id, lines) {
    const element = document.getElementById(id);
    if (!element) return;

    cleanUpContainer(element);
    lines.forEach(line => {
        const div = document.createElement("div");
        div.textContent = line;
        element.appendChild(div);
    });
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

    const amount = Number.isFinite(order.paymentAmount) ? moneyText(order.paymentAmount) : "";
    const channel = order.paymentChannel || "Paid";
    return amount ? `${channel} ${amount}` : channel;
}

function seatLocationText(seat) {
    const area = findArea(seat.areaId);
    const areaName = area ? area.name : shortId(seat.areaId);
    const rowCol = seatLabel(seat) || shortId(seat.id);
    return areaName ? `${areaName} ${rowCol}` : rowCol;
}

function seatLabel(seat) {
    return [seat.row, seat.col].filter(value => value !== undefined && value !== null).join("-");
}

function seatTooltip(seat) {
    const lines = [
        seatLocationText(seat),
        statusText(seat.status)
    ];
    if (seat.orderId)
        lines.push(`Order ${shortId(seat.orderId)}`);
    if (seat.userId)
        lines.push(seat.userId);
    return lines.join("\n");
}

function seatClass(seat) {
    const status = seat.status || "open";
    if (status === "checkedIn")
        return "checked-in-seat";
    if (status === "booked")
        return "booked-seat";
    return "open-seat";
}

function statusText(status) {
    if (status === "checkedIn")
        return "Checked in";
    if (status === "booked")
        return "Booked";
    return "Open";
}

function numberText(value) {
    return Number.isFinite(value) ? String(value) : "0";
}

function moneyText(cents) {
    return (cents / 100).toFixed(2);
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
