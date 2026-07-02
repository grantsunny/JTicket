import {apiFetch, cleanUpContainer, drawEventVenueEx, drawSeats, enforceNumericInput} from "./common.js";

window.jticket = {
    ...window.jticket,
    setupEventMetadata,
    setupEventPricing,
    setupEventSessions,
    enforceNumericInput,
    submitNewPricingForm,
    showAddPricingModal,
    deleteSelectedPricing,
    changeEventVenue,
    updateEventVenue,
    deleteEvent,
    updateSession,
    deleteSession,
    setAreaPrice,
    setSeatPrice,
    clearAreaPrice,
    clearSeatPrice,
    addNewEvent,
    addNewSession,
    showAddEventModal,
    refreshFormEventCopyFromList,
    refreshFormEventVenueList,
    submitEventMetadata,
}

function updateSession(container, eventId, sessionId) {
    if ((!sessionId) || (!eventId)) return;

    apiFetch(`/api/events/${eventId}/sessions/${sessionId}`, {
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
                    const sessionName = container.querySelectorAll('input[name="selectedSessionName[]"]')[selectedIndex].value;
                    const sessionStartTime = formatDateTime(new Date(container.querySelectorAll('input[name="selectedSessionStartTime[]"]')[selectedIndex].value));
                    const sessionEndTime = formatDateTime(new Date(container.querySelectorAll('input[name="selectedSessionEndTime[]"]')[selectedIndex].value));

                    session.name = sessionName;
                    session.startTime = sessionStartTime;
                    session.endTime = sessionEndTime;
                } else
                    return;
            } else
                return;

            apiFetch(`/api/events/${eventId}/sessions/${sessionId}`, {
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

    apiFetch(`/api/events/${eventId}/sessions/${sessionId}`, {
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
        apiFetch(apiUrl, {
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
    apiFetch(apiUrl)
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
                    <td><a href="#" onclick="jticket.changeEventVenue('${event.name}','${event.id}', '${event.venueId}')">${venueName || 'Unknown Venue'}</td> <!-- Display venue name or 'Unknown Venue' if not found -->
                    <td><button onclick="jticket.setupEventPricing('${event.name}','${event.id}')">Pricing</button></td>
                    <td><button onclick="jticket.setupEventSessions('${event.name}','${event.id}')">Sessions</button></td>
                    <td><button onclick="jticket.setupEventMetadata('${event.id}')">Metadata</button></td>
                    <td><button onclick="jticket.deleteEvent('${event.id}')">Delete</button></td>
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
    const priceName = newPricingForm.querySelector("#newPriceName").value.trim();
    const price = newPricingForm.querySelector("#newPrice").value;
    const priceInCents = Math.round(Number(price) * 100);

    if (!priceName || !Number.isFinite(priceInCents)) {
        alert("Please enter a price name and amount.");
        return;
    }

    apiFetch(apiUrl, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify(
            {
                "eventId": eventId,
                "name": priceName,
                "price": priceInCents
            }
        )
    })
        .then(response => {
            if (response.ok) {
                const pricingContainer = document.querySelector("#modalEventPricingList");
                const selectedAreaId = pricingContainer?.querySelector("#containerPricingArea")?.selectedAreaId || null;
                const createdPriceId = response.headers.get("Location")?.split("/").pop() || "";
                newPricingForm.querySelector("#newPriceName").value = "";
                newPricingForm.querySelector("#newPrice").value = "";
                newPricingForm.closest("#modalAddPricing").style.display = "none";
                reloadEventPricing(eventId, pricingContainer, selectedAreaId, createdPriceId);
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error creating event price:', error);
            // Error handling for network issues
        });
}

function showAddPricingModal(eventId) {
    const modal = document.getElementById("modalAddPricing");
    modal.eventId = eventId;
    modal.querySelector("#newPriceName").value = "";
    modal.querySelector("#newPrice").value = "";
    modal.style.display = "block";
    modal.querySelector("#newPriceName").focus();
}

function deleteSelectedPricing(eventId) {
    const pricingContainer = document.querySelector("#modalEventPricingList");
    const selectedPrice = pricingContainer?.querySelector('input[name="radioEventPrice"]:checked');
    if (!selectedPrice) {
        alert("Please select a price first.");
        return;
    }

    const selectedLabel = selectedPrice.closest("label")?.textContent?.trim() || "the selected price";
    if (!confirm(`Delete ${selectedLabel}?`)) {
        return;
    }

    capturePricingUiState(pricingContainer);
    const selectedAreaId = pricingContainer.querySelector("#containerPricingArea")?.selectedAreaId || null;
    apiFetch(`/api/events/${eventId}/prices/${selectedPrice.id}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.status === 204 || response.ok) {
                pricingContainer.dataset.selectedPriceId = "";
                reloadEventPricing(eventId, pricingContainer, selectedAreaId, "");
            } else if (response.status === 304 || response.status === 409) {
                alert("This price is already used and cannot be deleted.");
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error deleteSelectedPricing:', error);
        });
}

function reloadEventPricing(eventId, container, selectedAreaId, selectedPriceId = container.dataset.selectedPriceId || "") {

    const apiUrl = `/api/events/${eventId}/prices`; // Replace with the actual endpoint URL
    Promise.all([
        apiFetch(apiUrl).then(response => response.ok ? response.json() : []),
        apiFetch(`/api/events/${eventId}/areas`).then(response => response.ok ? response.json() : [])
    ])
        .then(([data, areas]) => {
            const selectedPricingId = selectedPriceId || data[0]?.id || "";
            const selectedPricingAreaId = selectedAreaId || areas[0]?.id || null;
            container.innerHTML = ''; // Clear existing content
            container.dataset.selectedPriceId = selectedPricingId;

            let tablePriceList = document.createElement("table");
            tablePriceList.className = "pricing-price-list";
            data.forEach(price => {
                const row = document.createElement("tr");
                const td = document.createElement("td");
                const label = document.createElement("label");
                label.className = "pricing-price-option";

                const radio = document.createElement("input");
                radio.type = "radio";
                radio.id = price.id;
                radio.name = "radioEventPrice";
                radio.checked = price.id === selectedPricingId;
                radio.addEventListener("change", function () {
                    container.dataset.selectedPriceId = this.id;
                });

                const text = document.createElement("span");
                text.textContent = `${price.name} (${price.price / 100})`;

                label.append(radio, text);
                td.appendChild(label);
                row.appendChild(td);
                tablePriceList.appendChild(row);
            });
            const workspace = document.createElement("div");
            workspace.className = "pricing-workspace";
            container.appendChild(workspace);

            const pricePanel = document.createElement("div");
            pricePanel.className = "pricing-price-panel pricing-side-panel";
            workspace.appendChild(pricePanel);
            const managementActions = document.createElement("div");
            managementActions.className = "pricing-management-actions";
            managementActions.innerHTML = `
                <button type="button" onclick="jticket.showAddPricingModal('${eventId}')">Add Price</button>
                <button type="button" onclick="jticket.deleteSelectedPricing('${eventId}')">Delete Price</button>
            `;
            pricePanel.appendChild(managementActions);
            pricePanel.appendChild(tablePriceList);
            tablePriceList.querySelector('input[name="radioEventPrice"]:checked')?.focus();

            const venuePanel = document.createElement("div");
            venuePanel.className = "pricing-venue-panel pricing-side-panel";
            workspace.appendChild(venuePanel);
            let containerArea = document.createElement("div");
            containerArea.id = "containerPricingArea";
            containerArea.selectedAreaId = selectedPricingAreaId;
            venuePanel.appendChild(containerArea);

            const seatPanel = document.createElement("div");
            seatPanel.className = "pricing-seat-panel";
            workspace.appendChild(seatPanel);

            let containerSeats = document.createElement("div");
            containerSeats.id = "containerPricingSeats";
            seatPanel.appendChild(containerSeats);

            const actions = document.createElement("div");
            actions.className = "pricing-actions";
            actions.innerHTML = `
                <span></span>
                <div class="pricing-area-actions">
                    <button onclick="jticket.setAreaPrice('${eventId}',
                        this.closest('#modalEventPricingList').querySelector('input[name=\\'radioEventPrice\\']:checked')?.id,
                        this.closest('#modalEventPricingList').querySelector('#containerPricingArea').selectedAreaId)">set area price</button>
                    <button onclick="jticket.clearAreaPrice('${eventId}',
                        this.closest('#modalEventPricingList').querySelector('#containerPricingArea').selectedAreaId)">clear area price</button>
                </div>
                <div class="pricing-seat-actions">
                    <button onclick="jticket.setSeatPrice('${eventId}',
                        this.closest('#modalEventPricingList').querySelector('input[name=\\'radioEventPrice\\']:checked')?.id,
                        this.closest('#modalEventPricingList').querySelector('#containerPricingArea').selectedAreaId,
                        this.closest('#modalEventPricingList').querySelector('#containerPricingSeats').selectedSeats)">set seat price</button>
                    <button onclick="jticket.clearSeatPrice('${eventId}',
                        this.closest('#modalEventPricingList').querySelector('#containerPricingArea').selectedAreaId,
                        this.closest('#modalEventPricingList').querySelector('#containerPricingSeats').selectedSeats)">clear seat price</button>
                </div>
            `;
            container.appendChild(actions);

            const statisticsContainer = document.createElement("div");
            statisticsContainer.className = "pricing-statistics";
            container.appendChild(statisticsContainer);
            renderPricingStatistics(eventId, data, statisticsContainer);

            const selectedSeatIds = container.dataset.selectedSeatIds
                ? container.dataset.selectedSeatIds.split(',').filter(Boolean)
                : [];
            const displayMode = container.dataset.displayMode || "rowCol";
            drawEventVenueEx(eventId,
                containerArea,
                function (eventId, areaId) {
                    containerArea.selectedAreaId = areaId;
                    container.dataset.selectedSeatIds = "";
                    drawSeats(eventId, areaId, containerSeats, {
                        displayMode: seatsContainerDisplayMode(container, containerSeats)
                    });
                });
            if (selectedPricingAreaId) {
                drawSeats(eventId, selectedPricingAreaId, containerSeats, {displayMode, selectedSeatIds});
            }

        })
        .catch(error => {
            console.error('Error fetching prices:', error);
        });
}

function renderPricingStatistics(eventId, prices, container) {
    container.textContent = "Loading pricing statistics...";

    apiFetch(`/api/events/${eventId}/areas`)
        .then(response => response.json())
        .then(areas => Promise.all(areas.map(area =>
            apiFetch(`/api/events/${eventId}/areas/${area.id}/seats`)
                .then(response => response.json())
                .then(seats => seats.map(seat => ({...seat, areaName: area.name})))
        )))
        .then(areaSeats => {
            const seats = areaSeats.flat();
            const stats = calculatePricingStatistics(seats, prices);
            renderPricingStatisticsSummary(container, stats);
        })
        .catch(error => {
            console.error('Error loading pricing statistics:', error);
            container.textContent = "Pricing statistics unavailable.";
        });
}

function calculatePricingStatistics(seats, prices) {
    const byPrice = new Map(prices.map(price => [price.name, {
        name: price.name,
        price: price.price || 0,
        count: 0
    }]));
    const stats = {
        totalSeats: seats.length,
        pricedSeats: 0,
        unpricedSeats: 0,
        soldSeats: 0,
        totalSellablePrice: 0,
        byPrice
    };

    seats.forEach(seat => {
        const hasPrice = seat.price !== null && seat.price !== undefined;
        if (hasPrice) {
            stats.pricedSeats++;
            const priceName = seat.priceName || "Unnamed";
            if (!stats.byPrice.has(priceName)) {
                stats.byPrice.set(priceName, {
                    name: priceName,
                    price: seat.price || 0,
                    count: 0
                });
            }
            stats.byPrice.get(priceName).count++;
        } else {
            stats.unpricedSeats++;
        }

        if (seat.sold) {
            stats.soldSeats++;
        }
        if (seat.available !== false && !seat.sold && hasPrice) {
            stats.totalSellablePrice += seat.price || 0;
        }
    });

    return stats;
}

function renderPricingStatisticsSummary(container, stats) {
    const priceRows = Array.from(stats.byPrice.values())
        .map(price => `
            <tr>
                <td>${price.name}</td>
                <td>${price.count}</td>
                <td>${formatMoney(price.price)}</td>
            </tr>
        `)
        .join("");

    container.innerHTML = `
        <div class="pricing-statistics-summary">
            <div><strong># of seats</strong><span>${stats.totalSeats}</span></div>
            <div><strong>Priced / Unpriced</strong><span>${stats.pricedSeats} / ${stats.unpricedSeats}</span></div>
            <div><strong>Sold out</strong><span>${stats.soldSeats}</span></div>
            <div><strong>Total sellable prices</strong><span>${formatMoney(stats.totalSellablePrice)}</span></div>
        </div>
        <table class="pricing-statistics-table">
            <thead>
                <tr><th>Price</th><th># seats</th><th>Unit</th></tr>
            </thead>
            <tbody>${priceRows}</tbody>
        </table>
        <div class="pricing-seat-legend">
            <span><span class="seat-legend-swatch available-seat"></span>Available</span>
            <span><span class="seat-legend-swatch unpriced-seat"></span>No price</span>
            <span><span class="seat-legend-swatch sold-seat"></span>Sold out</span>
            <span><span class="seat-legend-swatch unavailable-seat"></span>Physically unavailable</span>
        </div>
    `;
}

function formatMoney(priceInCents) {
    return ((priceInCents || 0) / 100).toFixed(2);
}

function setAreaPrice(eventId, priceId, areaId) {
    if (!priceId || !areaId) {
        alert("Please select a price and an area first.");
        return;
    }
    const container = document.querySelector('#modalEventPricingList');
    capturePricingUiState(container);
    apiFetch(`/api/events/${eventId}/areas/${areaId}/pricing`, {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ "priceId": priceId })
    })
        .then(response => {
            if (response.ok) {
                reloadEventPricing(eventId, container, areaId, priceId);
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error setAreaPrice:', error);
        });
}

function setSeatPrice(eventId, priceId, areaId, seatIds) {
    if (!priceId) {
        alert("Please select a price first.");
        return;
    }
    if (!areaId) {
        alert("Please select an area first.");
        return;
    }
    if (!seatIds || seatIds.length === 0) {
        alert("Please select at least one seat first.");
        return;
    }
    if (!confirm(`Set the selected price for ${seatIds.length} seat${seatIds.length === 1 ? "" : "s"}?`)) {
        return;
    }

    const container = document.querySelector('#modalEventPricingList');
    capturePricingUiState(container);
    let apiPromises = [];
    seatIds.forEach(seatId => {
        apiPromises.push(
            apiFetch(`/api/events/${eventId}/seats/${seatId}/pricing`, {
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
            reloadEventPricing(eventId, container, areaId, priceId);
        });
}

function clearAreaPrice(eventId, areaId) {
    if (!areaId) {
        alert("Please select an area first.");
        return;
    }
    if (!confirm("Clear the area price? Seats without a seat-level price may become unpriced.")) {
        return;
    }

    const container = document.querySelector('#modalEventPricingList');
    capturePricingUiState(container);
    apiFetch(`/api/events/${eventId}/areas/${areaId}/pricing`, {
        method: 'PATCH',
        headers: {
            'Content-Type': 'application/json',
        },
        body: JSON.stringify({ "priceId": null })
    })
        .then(response => {
            if (response.ok || response.status === 304) {
                reloadEventPricing(eventId, container, areaId, container.dataset.selectedPriceId || "");
            } else {
                console.error('Server returned ' + response.status);
            }
        })
        .catch(error => {
            console.error('Error clearAreaPrice:', error);
        });
}

function clearSeatPrice(eventId, areaId, seatIds) {
    if (!areaId) {
        alert("Please select an area first.");
        return;
    }
    if (!seatIds || seatIds.length === 0) {
        alert("Please select at least one seat first.");
        return;
    }
    if (!confirm(`Clear seat-level price for ${seatIds.length} seat${seatIds.length === 1 ? "" : "s"}?`)) {
        return;
    }

    const container = document.querySelector('#modalEventPricingList');
    capturePricingUiState(container);
    const apiPromises = seatIds.map(seatId =>
        apiFetch(`/api/events/${eventId}/seats/${seatId}/pricing`, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ "priceId": null })
        })
    );

    Promise.allSettled(apiPromises)
        .then(results => {
            results.forEach(result => {
                if (result.status === 'rejected') {
                    console.log('Failure:', result.reason);
                }
            });
            reloadEventPricing(eventId, container, areaId, container.dataset.selectedPriceId || "");
        });
}

function capturePricingUiState(container) {
    if (!container) return;

    const seatsContainer = container.querySelector('#containerPricingSeats');
    if (seatsContainer) {
        container.dataset.displayMode = seatsContainer.dataset.displayMode || container.dataset.displayMode || "rowCol";
        const selectedSeats = seatsContainer.selectedSeats || [];
        container.dataset.selectedSeatIds = selectedSeats.join(',');
    }

    const selectedPrice = container.querySelector('input[name="radioEventPrice"]:checked');
    if (selectedPrice) {
        container.dataset.selectedPriceId = selectedPrice.id;
    }
}

function seatsContainerDisplayMode(container, seatsContainer) {
    return seatsContainer?.dataset.displayMode || container?.dataset.displayMode || "rowCol";
}


function setupEventPricing(eventName, eventId) {
    var modal = document.getElementById("modelSetupEventPricing");
    var modalEventPricingList = modal.querySelector("#modalEventPricingList");

    modal.eventId = eventId;
    modal.querySelector("#modelSetupEventPricingTitle").textContent = "Event Pricing for " + eventName;
    modalEventPricingList.dataset.selectedPriceId = "";
    modalEventPricingList.dataset.selectedSeatIds = "";
    modalEventPricingList.dataset.displayMode = "rowCol";

    cleanUpContainer(modalEventPricingList);
    reloadEventPricing(eventId, modalEventPricingList, null);

    modal.style.display = "block";
}

function refreshEventSessions(container, eventId) {
    let tableSessions = container.querySelector("#tableEventSessions");
    cleanUpContainer(tableSessions);

    apiFetch(`/api/events/${eventId}/sessions`, {
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
                    radioSelectedSession.value = session.id;

                    row.appendChild(document.createElement("td")).append(radioSelectedSession);
                    let inputSessionName = document.createElement("input");
                    inputSessionName.type = 'text';
                    inputSessionName.id = 'selectedSessionName';
                    inputSessionName.name = "selectedSessionName[]";
                    inputSessionName.value = session.name;
                    row.appendChild(document.createElement("td")).append(inputSessionName);

                    sessionNameWidth = Math.max(
                        sessionNameWidth,
                        radioSelectedSession.offsetWidth + inputSessionName.offsetWidth);

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
    apiFetch(`/api/events/${eventId}`, {
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

    apiFetch(`/api/events/${eventId}`, {
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

    apiFetch(apiUrl)
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
    apiFetch(`/api/events/${eventId}`, {
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
        apiFetch(apiUrl, {
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

    apiFetch(`/api/events/${eventId}/sessions`, {
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

function showAddEventModal() {
    const modal = document.getElementById("modalAddEvent");
    modal.querySelector("#eventName").value = "";
    modal.querySelector("#venueId").value = "";
    modal.querySelector("#copyEventFromVenue").innerHTML = '<option value="">Select Event</option>';
    modal.style.display = "block";
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

    submitEvent(eventData, copyEventFrom)
        .then(created => {
            if (!created) return;
            form.querySelector("#eventName").value = "";
            form.querySelector("#venueId").value = "";
            form.querySelector("#copyEventFromVenue").innerHTML = '<option value="">Select Event</option>';
            form.closest("#modalAddEvent").style.display = "none";
        });
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
    return apiFetch(apiUrl, {
        method: 'POST',
        headers: headers,
        body: JSON.stringify(eventData)
    })
        .then(response => {
            if (response.status === 201) {
                // Event created successfully, refresh the event list
                fetchEvents();
                return true;
            } else {
                console.error('Error creating event. Status:', response.status);
                return false;
            }
        })
        .catch(error => {
            console.error('Error creating event:', error);
            return false;
        });
}

// Function to fetch venues from the API
function refreshFormEventVenueList() {
    // Define the API endpoint URL for getting venues
    const apiUrl = '/api/venues'; // Replace with the actual endpoint URL

    // Make a GET request to the API
    apiFetch(apiUrl)
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
