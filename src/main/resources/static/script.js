let areaNames = {}; // This object will store the mapping of areaId to area names

document.addEventListener('DOMContentLoaded', function() {
    // Fetch and display venues when the page loads
    fetchVenues();
});

function fetchVenues() {
    // Replace with your actual API endpoint
    fetch('/api/venues')
        .then(response => response.json())
        .then(data => {
            const radioContainer = document.querySelector('.radio-container');
            radioContainer.innerHTML = ''; // Clear previous radio buttons
            data.forEach(venue => {
                // Create radio button for each venue
                let radioButton = document.createElement('input');
                radioButton.type = 'radio';
                radioButton.id = venue.id;
                radioButton.name = 'venue';
                radioButton.value = venue.id;

                // Fetch areas and preview venue when a venue is selected
                radioButton.addEventListener('change', function() {
                    fetchAreaNames(venue.id).then(() => {
                        // Attach hover events for the new SVG
                        attachEventListeners();
                    });
                    previewVenue(venue.id);
                });

                // Create label for each radio button
                let label = document.createElement('label');
                label.htmlFor = venue.id;
                label.textContent = venue.name;

                // Append radio button and label to the radio container
                radioContainer.appendChild(radioButton);
                radioContainer.appendChild(label);
            });
        });
}

function fetchAreaNames(venueId) {
    // Replace with your actual API endpoint, use the venueId to fetch areas
    return fetch(`/api/venues/${venueId}/areas`)
        .then(response => response.json())
        .then(areas => {
            areaNames = {}; // Reset the areaNames object
            areas.forEach(area => {
                areaNames[area.id] = area.name; // Map 'id' from JSON to 'areaId'
            });
        });
}

function attachEventListeners() {
    // Event delegation for mouseover on SVG rect elements
    document.querySelector('.svg-container').addEventListener('mouseover', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            showAreaName(areaId, event.target);
        }
    });

    // Event delegation for mouseout on SVG rect elements
    document.querySelector('.svg-container').addEventListener('mouseout', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            hideAreaName();
        }
    });

    // Event delegation for click on SVG rect elements
    document.querySelector('.svg-container').addEventListener('click', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            fetchSeatsForArea(areaId);
        }
    });
}


function fetchSeatsForArea(areaId) {
    const venueId = document.querySelector('input[name="venue"]:checked').value;
    fetch(`http://localhost:8080/api/venues/${venueId}/areas/${areaId}/seats`)
        .then(response => response.json())
        .then(seats => {
            displaySeats(seats);
        });
}

function displaySeats(seats) {
    const seatsContainer = document.getElementById('seatsContainer');
    seatsContainer.innerHTML = ''; // Clear previous content

    // Create a table
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
                    updateOrderButtonState();
                });
            }
        });
    });

    seatsContainer.appendChild(table);
    // Create and append the button
    const orderSeatsButton = document.createElement('button');
    orderSeatsButton.id = 'orderSeatsButton';
    orderSeatsButton.textContent = 'Place Order';
    orderSeatsButton.disabled = true; // Initially disabled
    orderSeatsButton.addEventListener('click', handleOrderButtonClick);
    seatsContainer.appendChild(orderSeatsButton);
}

function updateOrderButtonState() {
    const selectedSeats = document.querySelectorAll('#seatsContainer td[data-selected="true"]');
    const confirmButton = document.getElementById('orderSeatsButton');
    confirmButton.disabled = selectedSeats.length === 0;
}

function handleOrderButtonClick() {
    const selectedSeats = Array.from(document.querySelectorAll('#seatsContainer td[data-selected="true"]'));
    const selectedSeatsInfo = selectedSeats.map(seat => {
        return `Seat ID: ${seat.dataset.seatid}, Row: ${seat.parentNode.rowIndex + 1}, Column: ${seat.cellIndex + 1}`;
    }).join('\n');

    alert(`Selected Seats:\n${selectedSeatsInfo}`);
}
function showAreaName(areaId, rect) {
    const name = areaNames[areaId];
    if (name) {
        const tooltip = document.createElement('div');
        tooltip.textContent = name;
        tooltip.className = 'tooltip';
        const rectBox = rect.getBoundingClientRect();
        tooltip.style.left = `${rectBox.left + window.scrollX}px`;
        tooltip.style.top = `${rectBox.top + rectBox.height + window.scrollY}px`;
        document.body.appendChild(tooltip);
    }
}

function hideAreaName() {
    const tooltip = document.querySelector('.tooltip');
    if (tooltip) {
        tooltip.remove();
    }
}

function previewVenue(venueId) {
    fetch(`/api/venues/${venueId}/svg`)
        .then(response => response.text())
        .then(svg => {
            const svgContainer = document.querySelector('.svg-container');
            svgContainer.innerHTML = svg;
        });
}
