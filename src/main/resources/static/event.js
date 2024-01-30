// Function to fetch existing events from the API
// Function to fetch existing events from the API
import {cleanUpContainer, enforceNumericInput, drawEventVenueEx, drawSeats} from "./common.js";

window.stoneticket = {
    setupEventMetadata,
    setupEventPricing,
    enforceNumericInput,
    submitNewPricingForm,
    changeEventVenue,
    updateEventVenue,
    deleteEvent
}

function fetchEvents() {
    // Define the API endpoint URL for getting events
    const apiUrl = '/api/events'; // Replace with the actual endpoint URL

    // Make a GET request to the API
    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            // Display existing events with venue information in the table
            const eventList = document.getElementById("eventList");
            eventList.innerHTML = "";

            data.forEach(event => {
                // Access the venueId from the event
                const venueId = event.venueId;

                // Get the name of the venue associated with the event's venueId
                const selectedVenueOption = document.querySelector(`#venueId option[value="${venueId}"]`);
                const venueName = selectedVenueOption ? selectedVenueOption.textContent : 'Unknown Venue';

                // Display event and venue information in the table row
                const row = document.createElement("tr");
                row.innerHTML = `
                    <td>${event.name}</td>
                    <td><a href="#" onclick="stoneticket.changeEventVenue('${event.name}','${event.id}', '${event.venueId}')">${venueName || 'Unknown Venue'}</td> <!-- Display venue name or 'Unknown Venue' if not found -->
                    <td>${event.startTime}</td>
                    <td>${event.endTime}</td>
                    <td><button onclick="stoneticket.deleteEvent('${event.id}')">Delete</button></td>
                    <td><button onclick="stoneticket.setupEventPricing('${event.name}','${event.id}')">Pricing</button></td>
                    <td><button onclick="stoneticket.setupEventMetadata('${event.id}')">Metadata</button></td>
                `;
                eventList.appendChild(row);
            });
        })
        .catch(error => {
            console.error('Error fetching events:', error);
        });
}

function submitNewPricingForm(eventId, newPricingForm) {
    const apiUrl = `/api/events/${eventId}/prices`;
    const priceName = newPricingForm.querySelector("#newPriceName").value;
    const price = newPricingForm.querySelector("#newPrice").value;

    fetch(apiUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(
            {
                "eventId": eventId,
                "name": priceName,
                "price": price * 100
            }
        )
    })
        .then(response => {
            if (response.ok) {
                reloadEventPricing(eventId, newPricingForm.parentElement.parentElement.querySelector("#modalEventPricingList"));
                newPricingForm.querySelector("#newPriceName").value = "";
                newPricingForm.querySelector("#newPrice").value = "";
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error creating event price:', error);
            // Error handling for network issues
        });
}

function reloadEventPricing(eventId, container) {
    const apiUrl = `/api/events/${eventId}/prices`; // Replace with the actual endpoint URL
    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            container.innerHTML = ''; // Clear existing content
            
            let tablePriceList = document.createElement("table");
            tablePriceList.align = "center";
            data.forEach(price => {
                const row = document.createElement("tr");
                row.innerHTML = `
                    <td align="left"><input type="radio" id="${price.id}" name="radioEventPrice">${price.name} (${price.price / 100}) </input></td>
                `;
                tablePriceList.appendChild(row);
            });

            container.appendChild(document.createElement("hr"));
            let table = document.createElement("table");
            table.style.width = "100%";
            container.appendChild(table);

            let tr = document.createElement("tr");
            table.appendChild(tr);

            let tdTablePriceList = document.createElement("td");
            tdTablePriceList.style.align="left";
            tdTablePriceList.style.width = "30%";
            tr.appendChild(tdTablePriceList);
            tdTablePriceList.appendChild(tablePriceList);

            let tdArea = document.createElement("td");
            tdArea.style.align = "right";
            tdArea.style.width = "30%";
            tr.appendChild(tdArea);

            let containerArea = document.createElement("div");
            containerArea.style.align = "center";
            containerArea.id = "containerPricingArea";
            tdArea.appendChild(containerArea);

            let tdSeats = document.createElement("td");
            tdSeats.style.width = "40%";
            tr.appendChild(tdSeats);

            let containerSeats = document.createElement("div");
            containerArea.id = "containerPricingSeats";
            tdSeats.appendChild(containerSeats);

            let trButton = document.createElement("tr");
            trButton.innerHTML = `
                    <td></td>
                    <td align="right"><button>Price selected area</button></td>
                    <td align="right"><button>Price selected seats</button></td>
                `;
            table.appendChild(trButton);

            drawEventVenueEx(eventId, containerArea, function (eventId, areaId) {
                drawSeats(eventId, areaId, containerSeats);
            });

        })
        .catch(error => {
            console.error('Error fetching prices:', error);
        });
}


function setupEventPricing(eventName, eventId) {
    var modal = document.getElementById("modelSetupEventPricing");
    var modalEventPricingList = modal.querySelector("#modalEventPricingList");

    modal.eventId = eventId;
    modal.querySelector("#modelSetupEventPricingTitle").textContent = "Event Pricing for " + eventName;
    modal.querySelector("#newPriceName").value = "";
    modal.querySelector("#newPrice").value = "";

    cleanUpContainer(modalEventPricingList);
    reloadEventPricing(eventId, modalEventPricingList);

    modal.style.display = "block";
}

function setupEventMetadata(eventId) {
    var modal = document.getElementById("modalSetupEventMetadata");
    var jsonEditorContainer = modal.querySelector("#jsonEditor");

    cleanUpContainer(jsonEditorContainer);
    fetch(`/api/events/${eventId}`, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        }
    })
        .then(response => response.json())
        .then(data => {
            let eventName = data.name;
            let metadata = data.metadata;

            modal.querySelector("#modalSetupEventMetadataTitle").textContent = 'Metadata of Event ' + eventName;
            const options = {
                "mode": "text",
                "indentation": 2
            };

            let jsonEditor = new JSONEditor(jsonEditorContainer, options, metadata);
            jsonEditorContainer.editor = jsonEditor;
            modal.dataset.event = JSON.stringify(data);
            modal.style.display = "block";

        })
        .catch(error => {
            console.error('Error updating event:', error);
            // Error handling for network issues
        });
}

function submitEventMetadata(metadata, modal, closeModal = false) {
    // Make a POST request to the API

    let eventData = JSON.parse(modal.dataset.event);
    eventData.metadata = metadata;

    let eventId = eventData.id;

    fetch(`/api/events/${eventId}`, {
        method: 'PUT',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(eventData)
    })
        .then(response => {
            if (response.ok) {
                if (closeModal)
                    modal.style.display='none';
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error updating event:', error);
            // Error handling for network issues
        });
}

function changeEventVenue(eventName, eventId, currentVenueId) {
    var modal = document.getElementById("modalChangeEventVenue");
    modal.dataset.eventId = eventId;
    modal.querySelector("#modalChangeEventVenueTitle").textContent = 'Change Venue for Event ' + eventName;

    const apiUrl = '/api/venues'; // Replace with the actual endpoint URL

    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            var venueOptions = modal.querySelector('#modalVenueOptions');
            venueOptions.innerHTML = ''; // Clear existing content
            data.forEach(venue => {
                var radioInput = document.createElement('input');
                radioInput.type = 'radio';
                radioInput.id = venue.id;
                radioInput.name = 'venue';
                radioInput.value = venue.id;
                radioInput.checked = venue.id === currentVenueId;

                // Create label for the radio input
                var label = document.createElement('label');
                label.htmlFor = venue.id;
                label.textContent = venue.name;

                // Append radio input and label to the container
                venueOptions.appendChild(radioInput);
                venueOptions.appendChild(label);
                venueOptions.appendChild(document.createElement('br')); // Line break for readability
            });

        })
        .catch(error => {
            console.error('Error fetching venues:', error);
        });
    modal.style.display = "block";
}

function updateEventVenue(eventId, selectedVenueId, modal) {
    fetch(`/api/events/${eventId}`, {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ venueId: selectedVenueId })
    })
        .then(response => {
            if (response.ok) {
                modal.style.display='none';
                fetchEvents();
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error updating event:', error);
            // Error handling for network issues
        });
}

function deleteEvent(eventId) {
    alert("not implemented");
}

// Function to handle the form submission
function submitFormData() {

    // Get input values
    const eventName = document.getElementById("eventName").value;
    const venueId = document.getElementById("venueId").value;
    const startTime = formatDateTime(new Date(document.getElementById("startTime").value));
    const endTime = formatDateTime(new Date(document.getElementById("endTime").value));

    // Create event data object
    const eventData = {
        name: eventName,
        venueId: venueId,
        startTime: startTime,
        endTime: endTime
    };

    // Call the submitEvent function with the event data
    submitEvent(eventData);

    // Clear the form inputs
    document.getElementById("eventName").value = "";
    document.getElementById("venueId").value = "";
    document.getElementById("startTime").value = "";
    document.getElementById("endTime").value = "";
}

function formatDateTime(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0'); // Adding 1 to month because months are zero-based
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}
// Function to submit a new event to the API
function submitEvent(eventData) {
    // Define the API endpoint URL for creating events
    const apiUrl = '/api/events'; // Replace with the actual endpoint URL

    // Make a POST request to the API
    fetch(apiUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify(eventData)
    })
        .then(response => {
            if (response.status === 201) {
                // Event created successfully, refresh the event list
                fetchEvents();
            } else {
                console.error('Error creating event. Status:', response.status);
            }
        })
        .catch(error => {
            console.error('Error creating event:', error);
        });
}


// Function to fetch venues from the API
function fetchVenues() {
    // Define the API endpoint URL for getting venues
    const apiUrl = '/api/venues'; // Replace with the actual endpoint URL

    // Make a GET request to the API
    fetch(apiUrl)
        .then(response => response.json())
        .then(data => {

            // Populate the venue select dropdown
            const venueSelect = document.getElementById("venueId");
            venueSelect.innerHTML = '<option value="">Select Venue</option>'; // Add an empty option

            data.forEach(venue => {
                const option = document.createElement("option");
                option.value = venue.id;
                option.textContent = venue.name;
                venueSelect.appendChild(option);
            });

            fetchEvents();
        })
        .catch(error => {
            console.error('Error fetching venues:', error);
        });
}

// Call the fetchVenues function to populate the venue select dropdown
fetchVenues();
