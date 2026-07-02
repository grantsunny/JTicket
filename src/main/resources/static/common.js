
window.jticket = {
    ...window.jticket,
    showCurrentUser
}

export function apiFetch(resource, options = {}) {
    return fetch(resource, {
        ...options,
        credentials: options.credentials || 'same-origin'
    }).then(response => {
        if (response.status === 401) {
            return redirectToOidcLogin();
        }
        return response;
    });
}

export function showCurrentUser(container) {
    return apiFetch('/api/auth/userinfo')
        .then(response => {
            if (!response.ok) {
                return redirectToOidcLogin();
            }
            return response.text();
        })
        .then(data => {
            container.textContent = data;
        })
        .catch(() => redirectToOidcLogin());
}

function redirectToOidcLogin() {
    window.location.href = '/oauth2/authorization/jticket';
    return new Promise(() => {});
}

export function cleanUpContainer(container) {
    while (container.hasChildNodes())
        container.removeChild(container.firstChild);

    return container;
}

export function enforceNumericInput(textBox) {
    textBox.value = textBox.value
        .replace(/[^0-9.]/g, '') // Remove non-numeric characters
        .replace(/(\..*)\./g, '$1') // Allow only one dot
        .replace(/(\.\d{2})./g, '$1'); // Allow up to two decimal places after the dot
}

export function drawVenue(venueId, svgContainer) {
    apiFetch(`/api/venues/${venueId}/svg`)
        .then(response => response.text())
        .then(svg => {
            svgContainer.innerHTML = svg;
        });
}

export function drawSeats(eventId, areaId, seatsContainer, options = {}) {
    Promise.all([
        apiFetch(`/api/events/${eventId}/areas/${areaId}/pricing`)
            .then(response => response.ok ? response.json() : null)
            .catch(() => null),
        apiFetch(`/api/events/${eventId}/areas/${areaId}/seats`)
            .then(response => response.json())
    ])
        .then(([areaPricing, seats]) => {
            const areaPriceName = effectivePricingName(areaPricing);
            const selectedSeatIds = new Set(options.selectedSeatIds || seatsContainer.selectedSeats || []);
            if (options.displayMode)
                seatsContainer.dataset.displayMode = options.displayMode;
            cleanUpContainer(seatsContainer);
            seatsContainer.dataset.displayMode = seatsContainer.dataset.displayMode || "rowCol";

            const displayModeSwitch = createSeatDisplayModeSwitch(seatsContainer);
            seatsContainer.appendChild(displayModeSwitch);

            const table = document.createElement('table');
            table.className = 'seats-table pricing-seats-table';

            // Organize seats by rows
            const seatRows = seats.reduce((rows, seat) => {
                if (!rows[seat.row]) rows[seat.row] = [];
                rows[seat.row].push(seat);
                return rows;
            }, {});

            // Create table rows and cells
            Object.keys(seatRows).sort((a, b) => a - b).forEach(row => {
                const tr = table.insertRow();
                seatRows[row].sort((a, b) => a.col - b.col).forEach(seat => {
                    const td= tr.insertCell();
                    const rowColText = `${seat.row}-${seat.col}`;
                    td.dataset.seatid = seat.id;
                    td.dataset.selected = selectedSeatIds.has(seat.id) ? "true" : "false";
                    td.dataset.rowcol = rowColText;
                    td.dataset.pricing = seat.priceName || areaPriceName || "";
                    td.dataset.price = seat.price || "";
                    td.dataset.sold = seat.sold ? "true" : "false";
                    td.dataset.availabilityreason = ticketingAvailabilityReason(seat);
                    td.className = pricingSeatClass(seat);
                    renderSeatText(td, seatsContainer.dataset.displayMode);

                    // Add click event listener for seat selection
                    if (isPricingSeatSelectable(seat)) {
                        td.addEventListener('click', function () {
                            this.dataset.selected = this.dataset.selected === "false" ? "true" : "false";
                            markSelectedSeats(seatsContainer);
                        });

                        apiFetch(`/api/events/${eventId}/seats/${seat.id}/pricing`)
                            .then(response => {
                                if (response.ok)
                                    response.json().then (pricing => {
                                        td.dataset.pricing = effectivePricingName(pricing);
                                        renderSeatText(td, seatsContainer.dataset.displayMode);
                                    })
                            })
                    }
                });

           });
           seatsContainer.appendChild(table);
           markSelectedSeats(seatsContainer);
           enableSeatToolTips(seatsContainer);
        });
}

function createSeatDisplayModeSwitch(seatsContainer) {
    const wrapper = document.createElement("label");
    wrapper.className = "seat-display-switch";

    const rowColLabel = document.createElement("span");
    rowColLabel.textContent = "Row-Col";

    const input = document.createElement("input");
    input.type = "checkbox";
    input.checked = seatsContainer.dataset.displayMode === "price";
    input.setAttribute("aria-label", "Show seat price on grid");

    const slider = document.createElement("span");
    slider.className = "seat-display-slider";

    const priceLabel = document.createElement("span");
    priceLabel.textContent = "Price";

    input.addEventListener("change", function () {
        seatsContainer.dataset.displayMode = this.checked ? "price" : "rowCol";
        seatsContainer.querySelectorAll("td").forEach(td => renderSeatText(td, seatsContainer.dataset.displayMode));
    });

    wrapper.append(rowColLabel, input, slider, priceLabel);
    return wrapper;
}

function renderSeatText(td, displayMode) {
    const rowCol = td.dataset.rowcol || "";
    const pricing = td.dataset.pricing || "";
    td.textContent = displayMode === "price" ? priceInitial(pricing) || rowCol : rowCol;
    td.title = displayMode === "price" ? rowCol : pricing;
}

function priceInitial(priceName) {
    const value = (priceName || "").trim();
    return value ? value.charAt(0).toUpperCase() : "";
}

function effectivePricingName(pricingState) {
    return pricingState?.effectivePricing?.name || "";
}

function pricingSeatClass(seat) {
    if (seat.sold)
        return "sold-seat";
    const reason = ticketingAvailabilityReason(seat);
    if (reason === "unpriced")
        return "unpriced-seat";
    if (seat.available === false)
        return "unavailable-seat";
    return "available-seat";
}

function isPricingSeatSelectable(seat) {
    return !seat.sold && (seat.available !== false || ticketingAvailabilityReason(seat) === "unpriced");
}

function ticketingAvailabilityReason(seat) {
    return seat.metadata?.ticketingAvailabilityReason || "";
}

function enableSeatToolTips(seatContainer) {
    seatContainer.onmouseover = function(event) {
        if (event.target.tagName.toLowerCase() === 'td' && event.target.getAttribute('data-pricing')) {
            const tooltipText = seatTooltipText(event.target, seatContainer.dataset.displayMode);
            if (tooltipText) {
                const tooltip = document.createElement('div');
                tooltip.textContent = tooltipText;
                tooltip.id="seatToolTip";
                tooltip.style.cssText = `
                    position: absolute;
                    background-color: #fff;
                    border: 1px solid #000;
                    padding: 5px;
                    z-index: 10;
                    pointer-events: none;
                `;
                const rectBox = event.target.getBoundingClientRect();
                tooltip.style.left = `${rectBox.left + window.scrollX}px`;
                tooltip.style.top = `${rectBox.top + rectBox.height + window.scrollY}px`;
                seatContainer.appendChild(tooltip);
            }
        }
    };

    // Event delegation for mouseout on SVG rect elements
    seatContainer.onmouseout = function(event) {
        if (event.target.tagName.toLowerCase() === 'td' && event.target.getAttribute('data-pricing')) {
            const tooltip = seatContainer.querySelector('#seatToolTip');
            if (tooltip) {
                tooltip.remove();
            }
        }
    };
}

function seatTooltipText(td, displayMode) {
    if (displayMode === "price")
        return td.dataset.rowcol || "";
    return td.dataset.pricing || "";
}

function markSelectedSeats(seatsContainer) {
    let selectedSeatIds = [];
    const selectedSeats = Array.from(seatsContainer.querySelectorAll('td[data-selected="true"]'));
    const selectedSeatsInfo = selectedSeats.map(seat => {
        selectedSeatIds.push(seat.dataset.seatid);
    });
    seatsContainer.selectedSeats = selectedSeatIds;
}

export function drawEventVenueEx(eventId, svgContainer, onAreaClick) {

     let areaNames = {};
     let areaPrices = {};

     apiFetch(`/api/events/${eventId}/areas`)
        .then(response => response.json())
        .then(areas => {
            areas.forEach(area => {
                areaNames[area.id] = area.name; // Map 'id' from JSON to 'areaId'
                apiFetch(`/api/events/${eventId}/areas/${area.id}/pricing`)
                    .then(response => {
                        if (response.ok)
                            response.json().then (pricing => {
                                areaPrices[area.id] = effectivePricingName(pricing);
                            })
                    })
            });
        });

    svgContainer.addEventListener('mouseover', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            const name = areaNames[areaId];
            const pricing = areaPrices[areaId];
            if (name) {
                const tooltip = document.createElement('div');
                tooltip.textContent = pricing ? `${name} (${pricing})` : name;
                tooltip.id="areaToolTip";
                tooltip.style.cssText = `
                    position: absolute;
                    background-color: #fff;
                    border: 1px solid #000;
                    padding: 5px;
                    z-index: 10;
                    pointer-events: none;
                `;
                const rectBox = event.target.getBoundingClientRect();
                tooltip.style.left = `${rectBox.left + window.scrollX}px`;
                tooltip.style.top = `${rectBox.top + rectBox.height + window.scrollY}px`;
                svgContainer.appendChild(tooltip);
            }
        }
    });

    // Event delegation for mouseout on SVG rect elements
    svgContainer.addEventListener('mouseout', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const tooltip = svgContainer.querySelector('#areaToolTip');
            if (tooltip) {
                tooltip.remove();
            }
        }
    });

    svgContainer.addEventListener('click', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            drawSelectedArea(svgContainer, eventId, areaId, onAreaClick);
        }
    });

    drawEventVenue(eventId, svgContainer, onAreaClick);

}

function drawSelectedArea(svgContainer, eventId, areaId, onAreaClick) {
    clearSelectedAreas(svgContainer);
    applySelectedAreaShadow(svgContainer, areaId);
    drawSelectedAreaBorder(svgContainer, areaId);
    svgContainer.selectedAreaId = areaId;

    if (onAreaClick)
        onAreaClick(eventId, areaId);
}


function clearSelectedAreas(svgContainer) {
    svgContainer.querySelectorAll('line').forEach(line => {
        line.remove();
    });
    svgContainer.querySelectorAll('.overlay').forEach(overlay => {
        overlay.remove();
    });
}

function applySelectedAreaColor(svgContainer, areaId) {
    svgContainer.querySelectorAll(`rect[areaid="${areaId}"]`).forEach(rect => {
        let overlay = rect.cloneNode(true);
        overlay.style.setProperty('fill', 'rgba(255, 0, 0, 0.2)');
        overlay.style.pointerEvents = 'none';
        overlay.setAttribute('filter', 'url(#shadow)'); // Apply the shadow filter
        overlay.setAttribute('class', 'overlay');
        rect.parentNode.insertBefore(overlay, rect.nextSibling);
    });
}

function drawSelectedAreaBorder(svgContainer, areaId) {
    let edgeCounts = new Map();
    svgContainer.querySelectorAll(`rect[areaid="${areaId}"]`).forEach(rect => {
        const x = parseFloat(rect.getAttribute('x'));
        const y = parseFloat(rect.getAttribute('y'));
        const width = parseFloat(rect.getAttribute('width'));
        const height = parseFloat(rect.getAttribute('height'));

        addEdge(edgeCounts, x, y, x + width, y); // Top edge
        addEdge(edgeCounts,x + width, y, x + width, y + height); // Right edge
        addEdge(edgeCounts,x + width, y + height, x, y + height); // Bottom edge
        addEdge(edgeCounts, x, y + height, x, y); // Left edge
    });

    // Draw edges that are not shared, i.e., count is 1
    edgeCounts.forEach((count, edgeKey) => {
        if (count === 1) { // Edge is not shared
            const [x1, y1, x2, y2] = edgeKey.split('-').map(Number);
            const svg = svgContainer.querySelector('svg');

            const line = document.createElementNS("http://www.w3.org/2000/svg", "line");
            line.setAttribute("x1", x1);
            line.setAttribute("y1", y1);
            line.setAttribute("x2", x2);
            line.setAttribute("y2", y2);

            // Set line style (customize as needed)
            line.setAttribute("stroke", "red"); // Line color
            line.setAttribute("stroke-width", "3"); // Line thickness

            // Append the line to the SVG container
            svg.appendChild(line);
        }
    });
}

function addEdge(edgeCounts, x1, y1, x2, y2) {
    const edgeKey = `${Math.min(x1, x2)}-${Math.min(y1, y2)}-${Math.max(x1, x2)}-${Math.max(y1, y2)}`;
    // Update edge count
    if (edgeCounts.has(edgeKey)) {
        // Edge is shared, increase its count
        edgeCounts.set(edgeKey, edgeCounts.get(edgeKey) + 1);
    } else {
        // First time seeing this edge, set its count to 1
        edgeCounts.set(edgeKey, 1);
    }
}

function applySelectedAreaShadow(svgContainer, areaId) {
    // Calculate the bounding box of all rectangles with the given areaId
    let minX = Infinity, minY = Infinity, maxX = 0, maxY = 0;
    svgContainer.querySelectorAll(`rect[areaid="${areaId}"]`).forEach(rect => {
        const x = parseFloat(rect.getAttribute('x'));
        const y = parseFloat(rect.getAttribute('y'));
        const width = parseFloat(rect.getAttribute('width'));
        const height = parseFloat(rect.getAttribute('height'));
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x + width);
        maxY = Math.max(maxY, y + height);
    });

    // Create a transparent overlay
    const overlay = document.createElementNS("http://www.w3.org/2000/svg", "rect");
    overlay.setAttribute('x', minX);
    overlay.setAttribute('y', minY);
    overlay.setAttribute('width', maxX - minX);
    overlay.setAttribute('height', maxY - minY);
    overlay.setAttribute('fill', 'none'); // Make the fill transparent
    overlay.setAttribute('stroke', 'none'); // No stroke needed, just the shadow
    overlay.setAttribute('filter', 'url(#shadow)'); // Apply the shadow filter

    overlay.setAttribute('class', 'overlay');

    overlay.style.setProperty('fill', 'rgba(255, 0, 0, 0.2)');
    overlay.style.pointerEvents = 'none'; // Ensure it doesn't capture mouse events

    // Append the overlay to the SVG
    const svg = svgContainer.querySelector('svg');
    svg.insertBefore(overlay, svg.firstChild);
}


export function drawEventVenue(eventId, svgContainer, onAreaClick) {
    apiFetch(`/api/events/${eventId}/venue/svg`)
        .then(response => response.text())
        .then(svgHtml => {
            svgContainer.innerHTML = svgHtml;

            //Enable shadow filter to the SVG.
            const svgNs = "http://www.w3.org/2000/svg";
            const svg = svgContainer.querySelector('svg');
            if (!svg) return;

            let defs = svg.querySelector('defs') || document.createElementNS(svgNs, 'defs');
            if (!svg.querySelector('defs')) {
                svg.prepend(defs); // Only append if it was newly created
                defs.innerHTML =
                    `<defs>
                        <filter id="shadow" x="-20%" y="-20%" width="140%" height="140%">
                            <feGaussianBlur in="SourceAlpha" stdDeviation="3" result="blur"></feGaussianBlur>
                            <feOffset in="blur" dx="2" dy="2" result="offsetBlur"></feOffset>
                            <feMerge>
                                <feMergeNode in="offsetBlur"></feMergeNode>
                                <feMergeNode in="SourceGraphic"></feMergeNode>
                            </feMerge>
                        </filter>
                    </defs>`;
            }

            if (svgContainer.selectedAreaId) {
                drawSelectedArea(svgContainer, eventId, svgContainer.selectedAreaId, onAreaClick);
            }
        });
}
