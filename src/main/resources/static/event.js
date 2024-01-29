// Function to fetch existing events from the API
// Function to fetch existing events from the API
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
                    <td><a href="#" onclick="changeEventVenue('${event.name}','${event.id}', '${event.venueId}')">${venueName || 'Unknown Venue'}</td> <!-- Display venue name or 'Unknown Venue' if not found -->
                    <td>${event.startTime}</td>
                    <td>${event.endTime}</td>
                    <td><button onclick="deleteEvent('${event.id}')">Delete</button></td>
                    <td><button onclick="setupEventPricing('${event.id}')">Pricing</button></td>
                    <td><button onclick="setupEventMetadata('${event.id}')">Metadata</button></td>
                `;
                eventList.appendChild(row);
            });
        })
        .catch(error => {
            console.error('Error fetching events:', error);
        });
}

function setupEventPricing(eventId) {
    alert('not implemented');
}

function setupEventMetadata(eventId) {
    var modal = document.getElementById("modalSetupEventMetadata");
    var jsonEditorContainer = modal.querySelector("#jsonEditor");
    while (jsonEditorContainer.hasChildNodes())
        jsonEditorContainer.removeChild(jsonEditorContainer.firstChild);

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

function submitEventVenue(eventId, selectedVenueId, modal) {
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
