import {apiFetch, drawVenue, enforceNumericInput} from "./common.js";

window.jticket = {
    ...window.jticket,
    uploadTemplate,
    fetchVenues
}

let areaNames = {};

function uploadTemplate(form) {
    const formData = new FormData(form);

    apiFetch('/api/template', {
        method: 'POST',
        body: formData
    })
        .then(response => {
            if (response.ok) {
                alert('Upload successful!');
                window.location.href = '/';
            } else {
                throw new Error(`Server returned status code ${response.status}`);
            }
        })
        .catch(error => {
            console.error('Error:', error);
            alert('Something unexpected happened. Please try again.');
        });
}

function fetchVenues() {
    const dropdownContainer = document.querySelector('.venue-dropdown-list-container');
    dropdownContainer.innerHTML = '';

    const dropdown = document.createElement('select');
    dropdown.name = 'venue';

    const defaultOption = document.createElement('option');
    defaultOption.textContent = '--- choose one venue ---';
    defaultOption.value = '';
    dropdown.appendChild(defaultOption);

    dropdown.addEventListener('change', function() {
        const selectedVenueId = this.value;
        if (selectedVenueId) {
            fetchAreaNames(selectedVenueId).then(() => {
                attachEventListeners(selectedVenueId);
            });
            drawVenue(selectedVenueId, document.querySelector('.svg-container'));
        }
    });

    dropdownContainer.appendChild(dropdown);

    apiFetch('/api/venues')
        .then(response => response.json())
        .then(data => {
            data.forEach(venue => {
                const option = document.createElement('option');
                option.value = venue.id;
                option.textContent = venue.name;
                dropdown.appendChild(option);
            });
        });
}

function fetchAreaNames(venueId) {
    return apiFetch(`/api/venues/${venueId}/areas`)
        .then(response => response.json())
        .then(areas => {
            areaNames = {};
            areas.forEach(area => {
                areaNames[area.id] = area.name;
            });
        });
}

function attachEventListeners(venueId) {
    document.querySelector('.svg-container').addEventListener('mouseover', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            showAreaName(areaId, event.target);
        }
    });

    document.querySelector('.svg-container').addEventListener('mouseout', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            hideAreaName();
        }
    });

    document.querySelector('.svg-container').addEventListener('click', function(event) {
        if (event.target.tagName === 'rect' && event.target.getAttribute('areaid')) {
            const areaId = event.target.getAttribute('areaid');
            fetchSeatsForArea(venueId, areaId);
        }
    });
}


function fetchSeatsForArea(venueId, areaId) {
    apiFetch(`/api/venues/${venueId}/areas/${areaId}/seats`)
        .then(response => response.json())
        .then(seats => {
            displaySeats(seats);
        });
}

function displaySeats(seats) {
    const seatsContainer = document.getElementById('seatsContainer');
    seatsContainer.innerHTML = '';

    const table = document.createElement('table');
    table.className = 'seats-table';

    const seatRows = seats.reduce((rows, seat) => {
        if (!rows[seat.row]) rows[seat.row] = [];
        rows[seat.row].push(seat);
        return rows;
    }, {});

    Object.keys(seatRows).sort((a, b) => a - b).forEach(row => {
        const tr = table.insertRow();
        seatRows[row].sort((a, b) => a.col - b.col).forEach(seat => {
            const td = tr.insertCell();
            td.textContent = `${seat.row}-${seat.col}`;
            td.dataset.seatid = seat.id;
            td.dataset.row = seat.row;
            td.dataset.col = seat.col;
            td.dataset.selected = "false";
            td.className = seat.available ? 'available-seat' : 'unavailable-seat';

            const originalBorder = td.style.border;

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
    const orderSeatsButton = document.createElement('button');
    orderSeatsButton.id = 'orderSeatsButton';
    orderSeatsButton.textContent = 'View Seat Details';
    orderSeatsButton.disabled = true;
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
        return `Seat ID: ${seat.dataset.seatid}, Row: ${seat.dataset.row}, Column: ${seat.dataset.col}`;
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
