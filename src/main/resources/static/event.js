import {cleanUpContainer, drawEventVenueEx, drawSeats, enforceNumericInput} from "./common.js";

window.stoneticket = {
    ...window.stoneticket,
    setupEventMetadata,
    setupEventPricing,
    setupEventSessions,
    enforceNumericInput,
    submitNewPricingForm,
    changeEventVenue,
    updateEventVenue,
    deleteEvent,
    updateSession,
    deleteSession,
    setAreaPrice,
    setSeatPrice,
    addNewEvent,
    addNewSession,
    refreshFormEventCopyFromList,
    refreshFormEventVenueList,
    submitEventMetadata,
}

function updateSession(container, eventId, sessionId) {
    if ((!sessionId) || (!eventId)) return;

    fetch(`/api/events/${eventId}/sessions/${sessionId}`, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        }
    })
        .then(response => response.json())
        .then(session => {
            const selectedSession = container.querySelector('input[name=\'selectedSession[]\']:checked');
            if (selectedSession !== null) {
                const selectedIndex= Array.from(container.querySelectorAll('input[name=\'selectedSession[]\']')).indexOf(selectedSession);
                if (selectedIndex !== -1) {
                    const sessionName = selectedSession.value;
                    const sessionStartTime = formatDateTime(new Date(container.querySelectorAll('input[name="selectedSessionStartTime[]"]')[selectedIndex].value));
                    const sessionEndTime = formatDateTime(new Date(container.querySelectorAll('input[name="selectedSessionEndTime[]"]')[selectedIndex].value));

                    session.name = sessionName;
                    session.startTime = sessionStartTime;
                    session.sessionEndTime = sessionEndTime;
                } else
                    return;
            } else
                return;

            fetch(`/api/events/${eventId}/sessions/${sessionId}`, {
                method: 'PUT',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify(session)
            })
                .then(response => {
                    if (response.ok) {
                        refreshEventSessions(container, eventId);
                    }
                })
                .catch(error => {
                    console.error('Error updating session:', error);
                });
        })
        .catch(error => {
            console.error('Error loading session:', error);
        });
}

function deleteSession(container, eventId, sessionId) {
    if ((!sessionId) || (!eventId)) return;

    fetch(`/api/events/${eventId}/sessions/${sessionId}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.status === 204) {
                // Event deleted successfully, refresh the event list
                refreshEventSessions(container, eventId);
            } else {
                console.error('Error deleting session. Status:', response.status);
            }
        })
        .catch(error => {
            console.error('Error deleting session:', error);
        });
}


function refreshFormEventCopyFromList(selectContainer, venueId) {
    if (venueId) {
        const apiUrl = `/api/events?venueId=${venueId}`; // Replace with the actual endpoint URL
        // Make a GET request to the API
        fetch(apiUrl, {
            method: 'GET'
        })
            .then(response => response.json())
            .then(data => {
                selectContainer.innerHTML = '<option value="">Select Event</option>'; // Add an empty option
                data.forEach(event => {
                    const option = document.createElement("option");
                    option.value = event.id;
                    option.textContent = event.name;
                    selectContainer.appendChild(option);
                });
            })
            .catch(error => {
                console.error('Error fetching venues:', error);
            });
    }
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
                    <td><button onclick="stoneticket.setupEventPricing('${event.name}','${event.id}')">Pricing</button></td>
                    <td><button onclick="stoneticket.setupEventSessions('${event.name}','${event.id}')">Sessions</button></td>
                    <td><button onclick="stoneticket.setupEventMetadata('${event.id}')">Metadata</button></td>
                    <td><button onclick="stoneticket.deleteEvent('${event.id}')">Delete</button></td>
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
                reloadEventPricing(eventId, newPricingForm.parentElement.parentElement.querySelector("#modalEventPricingList"), null);
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

function reloadEventPricing(eventId, container, selectedAreaId) {

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
            containerArea.selectedAreaId = selectedAreaId;
            tdArea.appendChild(containerArea);

            let tdSeats = document.createElement("td");
            tdSeats.style.width = "40%";
            tr.appendChild(tdSeats);

            let containerSeats = document.createElement("div");
            containerSeats.id = "containerPricingSeats";
            tdSeats.appendChild(containerSeats);

            let trButton = document.createElement("tr");
            trButton.innerHTML = `
                    <td></td>
                    <td align="right"><button onclick="stoneticket.setAreaPrice('${eventId}',
                        this.closest('#modalEventPricingList').querySelector('input[name=\\'radioEventPrice\\']:checked')?.id,
                        this.closest('#modalEventPricingList').querySelector('#containerPricingArea').selectedAreaId)">set area price</button></td>
                    <td align="right"><button onclick="stoneticket.setSeatPrice('${eventId}',
                        this.closest('#modalEventPricingList').querySelector('input[name=\\'radioEventPrice\\']:checked')?.id,
                        this.closest('#modalEventPricingList').querySelector('#containerPricingArea').selectedAreaId,
                        this.closest('#modalEventPricingList').querySelector('#containerPricingSeats').selectedSeats)">set seat price</button></td>
                `;

            table.appendChild(trButton);
            drawEventVenueEx(eventId,
                containerArea,
                function (eventId, areaId) {
                    drawSeats(eventId, areaId, containerSeats);
                });

        })
        .catch(error => {
            console.error('Error fetching prices:', error);
        });
}

function setAreaPrice(eventId, priceId, areaId) {
    fetch(`/api/events/${eventId}/areas/${areaId}/pricing`, {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ "priceId": priceId })
    })
        .then(response => {
            if (response.ok) {
                reloadEventPricing(eventId, document.querySelector('#modalEventPricingList'), areaId);
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error setAreaPrice:', error);
        });
}

function setSeatPrice(eventId, priceId, areaId, seatIds) {
    let apiPromises = [];
    seatIds.forEach(seatId => {
        apiPromises.push(
            fetch(`/api/events/${eventId}/seats/${seatId}/pricing`, {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({"priceId": priceId})
            })
        )
    });

    Promise.allSettled(apiPromises)
        .then(results => {
            results.forEach((result) => {
                if (result.status === 'fulfilled') {
                    console.log('Success:', result.value);
                } else {
                    console.log('Failure:', result.reason);
                }
            });
            reloadEventPricing(eventId, document.querySelector('#modalEventPricingList'), areaId);
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
    reloadEventPricing(eventId, modalEventPricingList, null);

    modal.style.display = "block";
}

function refreshEventSessions(container, eventId) {
    let tableSessions = container.querySelector("#tableEventSessions");
    cleanUpContainer(tableSessions);

    fetch(`/api/events/${eventId}/sessions`, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        }
    })
        .then(response => response.json())
        .then(data => {
            let sessionNameWidth = 65;
            if (data.length > 0) {
                container.querySelector("#editSessionButtons").style.display = "block";
                data.forEach(session => {
                    let row = tableSessions.appendChild(document.createElement("tr"));
                    let radioSelectedSession = document.createElement("input");
                    radioSelectedSession.type = 'radio';
                    radioSelectedSession.id = session.id;
                    radioSelectedSession.name = 'selectedSession[]';
                    radioSelectedSession.value = session.name;

                    row.appendChild(document.createElement("td")).append(radioSelectedSession);
                    let labelSession = document.createElement("label");
                    labelSession.textContent = session.name;
                    row.appendChild(document.createElement("td")).append(labelSession);

                    sessionNameWidth = Math.max(
                        sessionNameWidth,
                        radioSelectedSession.offsetWidth + labelSession.offsetWidth);

                    let inputSessionFrom = document.createElement("input");
                    inputSessionFrom.type = 'datetime-local';
                    inputSessionFrom.id = 'selectedSessionStartTime';
                    inputSessionFrom.name = "selectedSessionStartTime[]";
                    inputSessionFrom.value = session.startTime;
                    row.appendChild(document.createElement("td")).append(inputSessionFrom);

                    let inputSessionTo = document.createElement("input");
                    inputSessionTo.type = 'datetime-local';
                    inputSessionTo.id = 'selectedSessionEndTime';
                    inputSessionTo.name = "selectedSessionEndTime[]";
                    inputSessionTo.value = session.endTime;
                    row.appendChild(document.createElement("td")).append(inputSessionTo);
                });
            } else
                container.querySelector("#editSessionButtons").style.display = "none";

            container.querySelector("#newSessionName").style.width = `${sessionNameWidth}px`;
            container.querySelector("#newSessionName").value = "";
            container.querySelector("#newSessionStartTime").value = new Date().toISOString().slice(0,19);
            container.querySelector("#newSessionEndTime").value = new Date().toISOString().slice(0,19);

        })
        .catch(error => {
            console.error('Error updating sessions:', error);
            // Error handling for network issues
        });

}

function setupEventSessions(eventName, eventId) {
    let modal = document.getElementById("modalSetupEventSessions");
    modal.querySelector("#modalSetupEventSessionsTitle").textContent = "Sessions of Event: " + eventName;
    modal.style.display = "inline-block";
    modal.dataset.eventId = eventId;

    refreshEventSessions(modal, eventId);
}

function setupEventMetadata(eventId) {
    let modal = document.getElementById("modalSetupEventMetadata");
    let jsonEditorContainer = modal.querySelector("#jsonEditor");

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

            jsonEditorContainer.editor = new JSONEditor(jsonEditorContainer, options, metadata);
            modal.dataset.event = JSON.stringify(data);
            modal.style.display = "block";

        })
        .catch(error => {
            console.error('Error updating event:', error);
            // Error handling for network issues
        });
}

export function submitEventMetadata(metadata, modal, closeModal = false) {
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
    if (confirm("Please confirm: delete event will clean up all price settings. And removing will be denied for event with orders placed.")) {
        const apiUrl = `/api/events/${eventId}`; // Replace with the actual endpoint URL

        // Make a POST request to the API
        fetch(apiUrl, {
            method: 'DELETE',
        })
            .then(response => {
                if (response.status === 204) {
                    // Event deleted successfully, refresh the event list
                    fetchEvents();
                } else {
                    console.error('Error deleting event. Status:', response.status);
                }
            })
            .catch(error => {
                console.error('Error deleting event:', error);
            });
    }
}

function addNewSession(form) {
    const modal = form.closest("#modalSetupEventSessions");
    const eventId = modal.dataset.eventId;
    const sessionName = form.querySelector("#newSessionName").value;
    const sessionStartTime = formatDateTime(new Date(form.querySelector("#newSessionStartTime").value));
    const sessionEndTime = formatDateTime(new Date(form.querySelector("#newSessionEndTime").value));

    const sessionPayload = {
        name: sessionName,
        eventId: eventId,
        startTime: sessionStartTime,
        endTime: sessionEndTime
    };

    fetch(`/api/events/${eventId}/sessions`, {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(sessionPayload)
    })
        .then(response => {
            if (response.status === 201) {
                refreshEventSessions(modal, eventId);
            } else {
                console.error('Error creating session. Status:', response.status);
            }
        })
        .catch(error => {
            console.error('Error creating session:', error);
        });
}

// Function to handle the form submission
function addNewEvent(form) {

    // Get input values
    const eventName = form.querySelector("#eventName").value;
    const venueId = form.querySelector("#venueId").value;
    const copyEventFrom = form.querySelector("#copyEventFromVenue").value;

    // Create event data object
    const eventData = {
        name: eventName,
        venueId: venueId,
    };

    // Call the submitEvent function with the event data
    submitEvent(eventData, copyEventFrom);

    // Clear the form inputs
    document.getElementById("eventName").value = "";
    document.getElementById("venueId").value = "";
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
function submitEvent(eventData, copyEventFrom) {
    // Define the API endpoint URL for creating events
    const apiUrl = '/api/events'; // Replace with the actual endpoint URL
    let headers = new Headers({'Content-Type': 'application/json'});
    if (copyEventFrom) headers.append('X-Copy-From-Id', copyEventFrom);

    // Make a POST request to the API
    fetch(apiUrl, {
        method: 'POST',
        headers: headers,
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
function refreshFormEventVenueList() {
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

