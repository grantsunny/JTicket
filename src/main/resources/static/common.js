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
    fetch(`/api/venues/${venueId}/svg`)
        .then(response => response.text())
        .then(svg => {
            svgContainer.innerHTML = svg;
        });
}

export function drawSeats(eventId, areaId, seatsContainer) {
    fetch(`/api/events/${eventId}/areas/${areaId}/seats`)
        .then(response => response.json())
        .then(seats => {
            cleanUpContainer(seatsContainer);
            const table = document.createElement('table');
            table.className = 'seats-table'; // Add class for styling

            // Organize seats by rows
            const seatRows = seats.reduce((rows, seat) => {
                if (!rows[seat.row]) rows[seat.row] = [];
                rows[seat.row].push(seat);
                return rows;
            }, {});

            // Create table rows and cells
            Object.keys(seatRows).sort((a, b) => a - b).forEach(row => {
                const tr = table.insertRow();
                seatRows[row].sort((a, b) => a.column - b.column).forEach(seat => {
                    const td = tr.insertCell();
                    td.textContent = seat.name;
                    td.dataset.seatid = seat.id;
                    td.dataset.selected = "false";
                    td.className = seat.available ? 'available-seat' : 'unavailable-seat';

                    // Store the original border for restoration later
                    const originalBorder = td.style.border;

                    // Add click event listener for seat selection
                    if (seat.available) {
                        td.addEventListener('click', function () {
                            this.dataset.selected = this.dataset.selected === "false" ? "true" : "false";
                            this.style.border = this.dataset.selected === "true" ? "3px solid red" : originalBorder;
                        });
                    }
                });
            });
            seatsContainer.appendChild(table);
        });
}

export function drawEventVenueEx(eventId, svgContainer, onAreaClick) {
     let areaNames = {};
     fetch(`/api/events/${eventId}/areas`)
        .then(response => response.json())
        .then(areas => {
            areaNames = {}; // Reset the areaNames object
            areas.forEach(area => {
                areaNames[area.id] = area.name; // Map 'id' from JSON to 'areaId'
            });
        });

    svgContainer.addEventListener('mouseover', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            const name = areaNames[areaId];
            if (name) {
                const tooltip = document.createElement('div');
                tooltip.textContent = name;
                tooltip.id="divToolTip";
                tooltip.style.cssText = `
                    position: absolute;
                    background-color: #fff;
                    border: 1px solid #000;
                    padding: 5px;
                    z-index: 10;
                    pointer-events: none; /* This ensures the tooltip doesn't interfere with other mouse events */
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
            const tooltip = document.querySelector('#divToolTip');
            if (tooltip) {
                tooltip.remove();
            }
        }
    });

    // Event delegation for click on SVG rect elements
    svgContainer.addEventListener('click', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            if (onAreaClick)
                onAreaClick(eventId, areaId);
        }
    });
    drawEventVenue(eventId, svgContainer);
}

export function drawEventVenue(eventId, svgContainer) {
    fetch(`/api/events/${eventId}/venue/svg`)
        .then(response => response.text())
        .then(svg => {
            svgContainer.innerHTML = svg;
        });
}