import {apiFetch, cleanUpContainer, drawEventVenueEx, drawSeats, enforceNumericInput} from "./common.js";

const SESSION_DURATION_DEFAULT_SECONDS = 90 * 60;
const SESSION_DURATION_MIN_SECONDS = 15 * 60;
const SESSION_DURATION_MAX_SECONDS = 8 * 60 * 60;
const SESSION_DURATION_STEP_SECONDS = 15 * 60;
const SESSION_START_STEP_MINUTES = 15;
const SESSION_START_YEAR_RANGE = 5;

window.jticket = {
    ...window.jticket,
    setupEventMetadata,
    setupEventPricing,
    enforceNumericInput,
    submitNewPricingForm,
    showAddPricingModal,
    deleteSelectedPricing,
    changeEventVenue,
    updateEventVenue,
    deleteEvent,
    uploadEventPoster,
    showPosterPreview,
    showAddSessionModal,
    showEditSessionModal,
    submitSessionModal,
    deleteSelectedSession,
    setAreaPrice,
    setSeatPrice,
    clearAreaPrice,
    clearSeatPrice,
    addNewEvent,
    showAddEventModal,
    refreshFormEventCopyFromList,
    refreshFormEventVenueList,
    submitEventMetadata,
    saveEventMetadata,
    addMetadataRow,
    deleteSelectedMetadataRows,
    collectEventMetadata,
}

function refreshFormEventCopyFromList(selectContainer, venueId) {
    if (venueId) {
        const apiUrl = `/api/events?venueId=${venueId}`;
        apiFetch(apiUrl, {
            method: 'GET'
        })
            .then(response => response.json())
            .then(data => {
                selectContainer.innerHTML = '<option value="">Select Event</option>';
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
    const apiUrl = '/api/events';

    apiFetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            const eventList = document.getElementById("eventList");
            eventList.innerHTML = "";

            data.forEach(event => {
                const venueId = event.venueId;
                const selectedVenueOption = document.querySelector(`#venueId option[value="${venueId}"]`);
                const venueName = selectedVenueOption ? selectedVenueOption.textContent : 'Unknown Venue';

                const row = document.createElement("tr");
                row.className = "event-list-row";
                row.innerHTML = `
                    <td class="event-name-cell"></td>
                    <td class="event-poster-cell"></td>
                    <td class="event-venue-cell"></td>
                    <td class="event-sessions-cell">
                        <div class="event-sessions-panel" data-event-id="${event.id}">
                            <div class="event-session-list"></div>
                            <div class="event-session-actions">
                                <button type="button" class="add-session-button">ADD</button>
                                <button type="button" class="delete-session-button">DELETE</button>
                            </div>
                        </div>
                    </td>
                    <td class="event-actions-column"><div class="event-actions-cell"></div></td>
                `;
                renderEditableEventName(row.querySelector(".event-name-cell"), event);
                renderEventPoster(row.querySelector(".event-poster-cell"), event.id);

                const venueLink = document.createElement("a");
                venueLink.href = "#";
                venueLink.textContent = venueName || 'Unknown Venue';
                venueLink.addEventListener("click", function (clickEvent) {
                    clickEvent.preventDefault();
                    changeEventVenue(event.name, event.id, event.venueId);
                });
                row.querySelector(".event-venue-cell").appendChild(venueLink);

                const actions = row.querySelector(".event-actions-cell");
                actions.append(
                    buildEventActionButton("Pricing", () => setupEventPricing(event.name, event.id)),
                    buildEventActionButton("Metadata", () => setupEventMetadata(event.id)),
                    buildEventActionButton("Delete", () => deleteEvent(event.id))
                );

                const sessionPanel = row.querySelector(".event-sessions-panel");
                row.querySelector(".add-session-button").addEventListener("click", () => showAddSessionModal(event.id));
                row.querySelector(".delete-session-button").addEventListener("click", function () {
                    deleteSelectedSession(sessionPanel);
                });
                eventList.appendChild(row);
                refreshEventSessions(sessionPanel, event.id);
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

    const apiUrl = `/api/events/${eventId}/prices`;
    Promise.all([
        apiFetch(apiUrl).then(response => response.ok ? response.json() : []),
        apiFetch(`/api/events/${eventId}/areas`).then(response => response.ok ? response.json() : [])
    ])
        .then(([data, areas]) => {
            const selectedPricingId = selectedPriceId || data[0]?.id || "";
            const selectedPricingAreaId = selectedAreaId || areas[0]?.id || null;
            container.innerHTML = '';
            container.dataset.selectedPriceId = selectedPricingId;

            const tablePriceList = document.createElement("table");
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
            const containerArea = document.createElement("div");
            containerArea.id = "containerPricingArea";
            containerArea.selectedAreaId = selectedPricingAreaId;
            venuePanel.appendChild(containerArea);

            const seatPanel = document.createElement("div");
            seatPanel.className = "pricing-seat-panel";
            workspace.appendChild(seatPanel);

            const containerSeats = document.createElement("div");
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

function buildEventActionButton(label, action) {
    const button = document.createElement("button");
    button.type = "button";
    button.textContent = label;
    button.addEventListener("click", action);
    return button;
}

function renderEditableEventName(cell, event) {
    cell.textContent = "";
    const nameLink = document.createElement("a");
    nameLink.href = "#";
    nameLink.className = "event-name-link";
    nameLink.textContent = event.name;
    nameLink.addEventListener("click", function (clickEvent) {
        clickEvent.preventDefault();
        showEventNameEditor(cell, event);
    });
    cell.appendChild(nameLink);
}

function showEventNameEditor(cell, event) {
    cell.textContent = "";
    const input = document.createElement("input");
    input.type = "text";
    input.className = "event-name-editor";
    input.value = event.name;
    cell.appendChild(input);
    input.focus();
    input.select();

    let finished = false;
    const finish = (save) => {
        if (finished) return;
        finished = true;
        if (!save) {
            renderEditableEventName(cell, event);
            return;
        }
        const newName = input.value.trim();
        if (!newName || newName === event.name) {
            renderEditableEventName(cell, event);
            return;
        }
        const updatedEvent = {...event, name: newName};
        apiFetch(`/api/events/${event.id}`, {
            method: 'PUT',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify(updatedEvent)
        })
            .then(response => {
                if (response.ok || response.status === 204) {
                    event.name = newName;
                } else {
                    alert("Event name was not updated.");
                }
                renderEditableEventName(cell, event);
            })
            .catch(error => {
                console.error('Error updating event name:', error);
                alert("Event name was not updated.");
                renderEditableEventName(cell, event);
            });
    };

    input.addEventListener("keydown", function (event) {
        if (event.key === "Enter") {
            event.preventDefault();
            finish(true);
        } else if (event.key === "Escape") {
            event.preventDefault();
            finish(false);
        }
    });
    input.addEventListener("blur", () => finish(true));
}

function renderEventPoster(cell, eventId) {
    cell.textContent = "";
    const container = document.createElement("div");
    container.className = "event-poster-panel";

    const missing = document.createElement("div");
    missing.className = "event-poster-empty";
    missing.textContent = "No poster";

    const thumbnail = document.createElement("img");
    thumbnail.className = "event-poster-thumbnail";
    thumbnail.alt = "Event poster";
    thumbnail.src = eventPosterUrl(eventId);
    thumbnail.addEventListener("click", () => showPosterPreview(thumbnail.src));
    thumbnail.addEventListener("load", function () {
        missing.style.display = "none";
        thumbnail.style.display = "block";
    });
    thumbnail.addEventListener("error", function () {
        thumbnail.style.display = "none";
        missing.style.display = "block";
    });

    const fileInput = document.createElement("input");
    fileInput.type = "file";
    fileInput.accept = "image/png,image/jpeg,image/webp";
    fileInput.style.display = "none";
    fileInput.addEventListener("change", function () {
        if (this.files?.[0]) {
            uploadEventPoster(eventId, this.files[0], cell);
            this.value = "";
        }
    });

    const uploadLink = document.createElement("a");
    uploadLink.href = "#";
    uploadLink.textContent = "Upload";
    uploadLink.addEventListener("click", function (event) {
        event.preventDefault();
        fileInput.click();
    });

    container.append(thumbnail, missing, uploadLink, fileInput);
    cell.appendChild(container);
}

function eventPosterUrl(eventId) {
    return `/api/events/${eventId}/poster`;
}

function uploadEventPoster(eventId, file, cell) {
    const supportedTypes = new Set(["image/png", "image/jpeg", "image/webp"]);
    if (!supportedTypes.has(file.type)) {
        alert("Please choose a PNG, JPEG, or WebP image.");
        return;
    }

    apiFetch(eventPosterUrl(eventId), {
        method: 'POST',
        headers: {'Content-Type': file.type},
        body: file
    })
        .then(response => {
            if (response.ok || response.status === 204) {
                renderEventPoster(cell, eventId);
                const thumbnail = cell.querySelector(".event-poster-thumbnail");
                thumbnail.src = `${eventPosterUrl(eventId)}?t=${Date.now()}`;
            } else {
                alert("Poster was not uploaded.");
            }
        })
        .catch(error => {
            console.error('Error uploading event poster:', error);
            alert("Poster was not uploaded.");
        });
}

function showPosterPreview(src) {
    const modal = document.getElementById("modalPosterPreview");
    const image = modal.querySelector("#posterPreviewImage");
    image.src = src;
    modal.style.display = "block";
}

function getSessionPanel(element) {
    return element?.closest?.(".event-sessions-panel") || element;
}

function getSessionEditorModal() {
    const modal = document.getElementById("modalSessionEditor");
    const form = modal.querySelector("#sessionEditorForm");
    populateSessionStartDateOptions(form);
    populateSessionStartTimeOptions(form.querySelector("#sessionEditorStartTime"));
    if (!form.dataset.bound) {
        form.addEventListener("submit", function (event) {
            event.preventDefault();
            submitSessionModal(this);
        });
        form
            .querySelectorAll("#sessionEditorStartYear, #sessionEditorStartMonth, #sessionEditorStartDay")
            .forEach(control => control.addEventListener("change", function () {
                if (control.id !== "sessionEditorStartDay")
                    refreshSessionStartDayOptions(form);
                updateSessionEndPreview(form);
            }));
        form.querySelector("#sessionEditorStartTime").addEventListener("change", function () {
            updateSessionEndPreview(form);
        });
        form.querySelector("#sessionEditorDuration").addEventListener("input", function () {
            updateSessionDurationDisplay(form);
            updateSessionEndPreview(form);
        });
        form.dataset.bound = "true";
    }
    return modal;
}

function populateSessionStartDateOptions(form) {
    const yearSelect = form.querySelector("#sessionEditorStartYear");
    if (yearSelect.dataset.bound) return;

    const thisYear = new Date().getFullYear();
    for (let year = thisYear - SESSION_START_YEAR_RANGE; year <= thisYear + SESSION_START_YEAR_RANGE; year += 1) {
        const option = document.createElement("option");
        option.value = String(year);
        option.textContent = String(year);
        yearSelect.appendChild(option);
    }
    for (let month = 1; month <= 12; month += 1) {
        const option = document.createElement("option");
        option.value = String(month).padStart(2, "0");
        option.textContent = String(month).padStart(2, "0");
        form.querySelector("#sessionEditorStartMonth").appendChild(option);
    }
    yearSelect.dataset.bound = "true";
}

function populateSessionStartTimeOptions(select) {
    if (select.dataset.bound) return;
    for (let hour = 0; hour < 24; hour += 1) {
        for (let minute = 0; minute < 60; minute += SESSION_START_STEP_MINUTES) {
            const value = `${String(hour).padStart(2, "0")}:${String(minute).padStart(2, "0")}`;
            const option = document.createElement("option");
            option.value = value;
            option.textContent = value;
            select.appendChild(option);
        }
    }
    select.dataset.bound = "true";
}


function setupEventPricing(eventName, eventId) {
    const modal = document.getElementById("modelSetupEventPricing");
    const modalEventPricingList = modal.querySelector("#modalEventPricingList");

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
    container = getSessionPanel(container);
    const sessionList = container.querySelector(".event-session-list");
    cleanUpContainer(sessionList);

    apiFetch(`/api/events/${eventId}/sessions`, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        }
    })
        .then(response => response.json())
        .then(data => {
            if (data.length > 0) {
                data.forEach(session => {
                    const item = document.createElement("label");
                    item.className = "event-session-item";
                    item.dataset.sessionId = session.id;

                    const checkbox = document.createElement("input");
                    checkbox.type = "checkbox";
                    checkbox.name = `selectedSession-${eventId}`;
                    checkbox.value = session.id;
                    checkbox.addEventListener("change", function () {
                        if (!this.checked) return;
                        sessionList
                            .querySelectorAll(`input[name="${this.name}"]`)
                            .forEach(candidate => {
                                if (candidate !== this) candidate.checked = false;
                            });
                    });

                    const link = document.createElement("a");
                    link.href = "#";
                    link.className = "event-session-link";
                    link.textContent = formatSessionCaption(session);
                    link.title = session.name || "";
                    link.addEventListener("click", function (event) {
                        event.preventDefault();
                        showEditSessionModal(eventId, session.id);
                    });

                    item.append(checkbox, link);
                    sessionList.appendChild(item);
                });
            } else {
                const empty = document.createElement("div");
                empty.className = "event-session-empty";
                empty.textContent = "No sessions yet";
                sessionList.appendChild(empty);
            }

        })
        .catch(error => {
            console.error('Error updating sessions:', error);
        });

}

function showAddSessionModal(eventId) {
    const modal = getSessionEditorModal();
    modal.dataset.eventId = eventId;
    delete modal.dataset.sessionId;
    delete modal.dataset.session;
    modal.querySelector("#modalSessionEditorTitle").textContent = "Add Session";
    const startTime = nextQuarterDate(new Date());
    setSessionEditorValues(modal, {
        name: "",
        startTime,
        endTime: addSeconds(startTime, SESSION_DURATION_DEFAULT_SECONDS)
    });
    modal.style.display = "block";
    modal.querySelector("#sessionEditorName").focus();
}

function showEditSessionModal(eventId, sessionId) {
    const modal = getSessionEditorModal();
    modal.dataset.eventId = eventId;
    modal.dataset.sessionId = sessionId;
    modal.querySelector("#modalSessionEditorTitle").textContent = "Edit Session";

    apiFetch(`/api/events/${eventId}/sessions/${sessionId}`, {
        method: 'GET',
        headers: {'Accept': 'application/json'}
    })
        .then(response => response.json())
        .then(session => {
            modal.dataset.session = JSON.stringify(session);
            setSessionEditorValues(modal, session);
            modal.style.display = "block";
            modal.querySelector("#sessionEditorName").focus();
        })
        .catch(error => {
            console.error('Error loading session:', error);
        });
}

function submitSessionModal(form) {
    const modal = form.closest("#modalSessionEditor");
    const eventId = modal.dataset.eventId;
    const sessionId = modal.dataset.sessionId;
    const sessionPayload = modal.dataset.session ? JSON.parse(modal.dataset.session) : {};

    sessionPayload.name = form.querySelector("#sessionEditorName").value.trim();
    sessionPayload.eventId = eventId;
    const startDate = sessionStartDateValue(form);
    const durationSeconds = sessionDurationSeconds(form);
    sessionPayload.startTime = startDate ? formatDateTime(startDate) : "";
    sessionPayload.endTime = startDate ? formatDateTime(addSeconds(startDate, durationSeconds)) : "";

    if (!sessionPayload.name || !sessionPayload.startTime || !sessionPayload.endTime) {
        alert("Please enter a session name, start date/time, and duration.");
        return;
    }
    if (startDate < new Date() && !isOriginalPastSessionStart(modal, startDate)) {
        alert("Session start time cannot be earlier than now.");
        return;
    }

    const url = sessionId
        ? `/api/events/${eventId}/sessions/${sessionId}`
        : `/api/events/${eventId}/sessions`;
    const method = sessionId ? 'PUT' : 'POST';

    apiFetch(url, {
        method,
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify(sessionPayload)
    })
        .then(response => {
            if (response.ok || response.status === 201) {
                modal.style.display = "none";
                refreshEventSessions(document.querySelector(`.event-sessions-panel[data-event-id="${eventId}"]`), eventId);
            } else {
                console.error('Error saving session. Status:', response.status);
            }
        })
        .catch(error => {
            console.error('Error saving session:', error);
        });
}

function deleteSelectedSession(container) {
    const sessionPanel = getSessionPanel(container);
    const eventId = sessionPanel.dataset.eventId;
    const selected = sessionPanel.querySelector(".event-session-list input:checked");
    if (!selected) {
        alert("Please select a session first.");
        return;
    }
    if (!confirm("Delete selected session?")) return;

    apiFetch(`/api/events/${eventId}/sessions/${selected.value}`, {
        method: 'DELETE'
    })
        .then(response => {
            if (response.status === 204) {
                refreshEventSessions(sessionPanel, eventId);
            } else {
                console.error('Error deleting session. Status:', response.status);
            }
        })
        .catch(error => {
            console.error('Error deleting session:', error);
        });
}

function formatSessionCaption(session) {
    const sessionName = session.name || "Session";
    const startTime = String(session.startTime || "").replace("T", " ").slice(0, 16);
    return `${sessionName} [${startTime}]`;
}

function setSessionEditorValues(modal, session) {
    const form = modal.querySelector("#sessionEditorForm");
    const startDate = dateFromSessionValue(session.startTime) || nextQuarterDate(new Date());
    const rawEndDate = dateFromSessionValue(session.endTime);
    const durationSeconds = normalizeDurationSeconds(
        rawEndDate && rawEndDate > startDate
            ? Math.round((rawEndDate.getTime() - startDate.getTime()) / 1000)
            : SESSION_DURATION_DEFAULT_SECONDS
    );
    const start = splitDateTimeValue(startDate);

    modal.querySelector("#sessionEditorName").value = session.name || "";
    setSessionStartDateControls(form, start.date);
    ensureSelectOption(form.querySelector("#sessionEditorStartTime"), start.time);
    modal.querySelector("#sessionEditorStartTime").value = start.time;
    modal.querySelector("#sessionEditorDuration").value = String(durationSeconds);
    updateSessionDurationDisplay(form);
    updateSessionEndPreview(form);
}

function splitDateTimeValue(value) {
    const localValue = toDateTimeLocalValue(value || new Date());
    const [date, time] = localValue.split("T");
    return {date: date || "", time: time || ""};
}

function dateFromSessionValue(value) {
    if (!value) return null;
    const date = value instanceof Date ? value : new Date(String(value).replace(" ", "T"));
    return Number.isNaN(date.getTime()) ? null : date;
}

function nextQuarterDate(value) {
    const now = new Date();
    const date = new Date(value);
    date.setSeconds(0, 0);
    const minutes = date.getMinutes();
    const remainder = minutes % SESSION_START_STEP_MINUTES;
    if (remainder !== 0) {
        date.setMinutes(minutes + SESSION_START_STEP_MINUTES - remainder);
    }
    while (date < now) {
        date.setMinutes(date.getMinutes() + SESSION_START_STEP_MINUTES);
    }
    return date;
}

function sessionStartDateValue(form) {
    const date = sessionStartDateControlValue(form);
    const time = form.querySelector("#sessionEditorStartTime").value;
    if (!date || !time) return null;
    const value = new Date(`${date}T${time}`);
    if (Number.isNaN(value.getTime())) return null;
    return formatDateTimeForSessionDisplay(value).startsWith(`${date} ${time}`) ? value : null;
}

function sessionStartDateControlValue(form) {
    const year = form.querySelector("#sessionEditorStartYear").value;
    const month = form.querySelector("#sessionEditorStartMonth").value;
    const day = form.querySelector("#sessionEditorStartDay").value;
    return year && month && day ? `${year}-${month}-${day}` : "";
}

function setSessionStartDateControls(form, date) {
    const [year, month, day] = date.split("-");
    ensureSelectOption(form.querySelector("#sessionEditorStartYear"), year);
    form.querySelector("#sessionEditorStartYear").value = year;
    form.querySelector("#sessionEditorStartMonth").value = month;
    refreshSessionStartDayOptions(form, day);
    form.querySelector("#sessionEditorStartDay").value = day;
}

function ensureSelectOption(select, value) {
    if (!value || Array.from(select.options).some(option => option.value === value)) return;

    const option = document.createElement("option");
    option.value = value;
    option.textContent = value;
    select.appendChild(option);
}

function refreshSessionStartDayOptions(form, preferredDay = form.querySelector("#sessionEditorStartDay").value) {
    const year = Number(form.querySelector("#sessionEditorStartYear").value);
    const month = Number(form.querySelector("#sessionEditorStartMonth").value);
    const daySelect = form.querySelector("#sessionEditorStartDay");
    const daysInMonth = Number.isInteger(year) && Number.isInteger(month)
        ? new Date(year, month, 0).getDate()
        : 31;
    const selectedDay = Math.min(Number(preferredDay) || 1, daysInMonth);

    cleanUpContainer(daySelect);
    for (let day = 1; day <= daysInMonth; day += 1) {
        const option = document.createElement("option");
        option.value = String(day).padStart(2, "0");
        option.textContent = String(day).padStart(2, "0");
        daySelect.appendChild(option);
    }
    daySelect.value = String(selectedDay).padStart(2, "0");
}

function isOriginalPastSessionStart(modal, startDate) {
    const original = modal.dataset.session ? JSON.parse(modal.dataset.session) : null;
    const originalStart = dateFromSessionValue(original?.startTime);
    return originalStart
        && originalStart < new Date()
        && Math.abs(originalStart.getTime() - startDate.getTime()) < 1000;
}

function sessionDurationSeconds(form) {
    return normalizeDurationSeconds(Number(form.querySelector("#sessionEditorDuration").value));
}

function normalizeDurationSeconds(value) {
    const bounded = Math.min(
        SESSION_DURATION_MAX_SECONDS,
        Math.max(SESSION_DURATION_MIN_SECONDS, Number.isFinite(value) ? value : SESSION_DURATION_DEFAULT_SECONDS)
    );
    return Math.round(bounded / SESSION_DURATION_STEP_SECONDS) * SESSION_DURATION_STEP_SECONDS;
}

function addSeconds(date, seconds) {
    return new Date(date.getTime() + seconds * 1000);
}

function updateSessionDurationDisplay(form) {
    const duration = sessionDurationSeconds(form);
    form.querySelector("#sessionEditorDuration").value = String(duration);
    form.querySelector("#sessionEditorDurationLabel").textContent = formatDuration(duration);
}

function updateSessionEndPreview(form) {
    const start = sessionStartDateValue(form);
    const duration = sessionDurationSeconds(form);
    form.querySelector("#sessionEditorEndDisplay").value = start
        ? formatDateTimeForSessionDisplay(addSeconds(start, duration))
        : "";
}

function formatDuration(seconds) {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const remainingSeconds = seconds % 60;
    const parts = [];
    if (hours) parts.push(`${hours}h`);
    if (minutes) parts.push(`${minutes}m`);
    if (remainingSeconds || parts.length === 0) parts.push(`${remainingSeconds}s`);
    return parts.join(" ");
}

function formatDateTimeForSessionDisplay(date) {
    return formatDateTime(date).slice(0, 16);
}

function setupEventMetadata(eventId) {
    const modal = document.getElementById("modalSetupEventMetadata");
    const metadataRows = modal.querySelector("#metadataEditorRows");

    cleanUpContainer(metadataRows);
    apiFetch(`/api/events/${eventId}`, {
        method: 'GET',
        headers: {
            'Accept': 'application/json',
        }
    })
        .then(response => response.json())
        .then(data => {
            const eventName = data.name;
            const metadata = data.metadata;

            modal.querySelector("#modalSetupEventMetadataTitle").textContent = 'Metadata of Event ' + eventName;
            renderMetadataRows(modal, metadata || {});
            modal.dataset.event = JSON.stringify(data);
            modal.style.display = "block";

        })
        .catch(error => {
            console.error('Error updating event:', error);
        });
}

function renderMetadataRows(modal, metadata) {
    const entries = Object.entries(metadata);
    if (entries.length === 0) {
        addMetadataRow(modal);
        return;
    }
    entries.forEach(([key, value]) => addMetadataRow(modal, key, value));
}

function addMetadataRow(modal, key = "", value = "") {
    const tbody = modal.querySelector("#metadataEditorRows");
    const row = tbody.appendChild(document.createElement("tr"));
    const type = metadataValueType(value);
    row.innerHTML = `
        <td><input type="checkbox" class="metadata-row-selected"></td>
        <td><input type="text" class="metadata-key-input"></td>
        <td><input type="text" class="metadata-value-input"></td>
        <td>
            <select class="metadata-type-select">
                <option value="string">Text</option>
                <option value="number">Number</option>
                <option value="boolean">Boolean</option>
                <option value="json">JSON</option>
            </select>
        </td>
    `;
    row.querySelector(".metadata-key-input").value = key;
    row.querySelector(".metadata-value-input").value = metadataValueText(value, type);
    row.querySelector(".metadata-type-select").value = type;
}

function deleteSelectedMetadataRows(modal) {
    modal.querySelectorAll("#metadataEditorRows tr").forEach(row => {
        if (row.querySelector(".metadata-row-selected")?.checked)
            row.remove();
    });
    if (modal.querySelectorAll("#metadataEditorRows tr").length === 0)
        addMetadataRow(modal);
}

function collectEventMetadata(modal) {
    const metadata = {};
    for (const row of modal.querySelectorAll("#metadataEditorRows tr")) {
        const key = row.querySelector(".metadata-key-input").value.trim();
        if (!key) continue;

        const valueText = row.querySelector(".metadata-value-input").value;
        const type = row.querySelector(".metadata-type-select").value;
        metadata[key] = metadataValueFromText(valueText, type);
    }
    return metadata;
}

function metadataValueType(value) {
    if (typeof value === "number") return "number";
    if (typeof value === "boolean") return "boolean";
    if (value !== null && typeof value === "object") return "json";
    return "string";
}

function metadataValueText(value, type) {
    if (type === "json") return JSON.stringify(value);
    if (value === null || value === undefined) return "";
    return String(value);
}

function metadataValueFromText(value, type) {
    if (type === "number") {
        const number = Number(value);
        if (!Number.isFinite(number))
            throw new Error("Invalid number metadata value");
        return number;
    }
    if (type === "boolean")
        return value === "true";
    if (type === "json")
        return JSON.parse(value || "null");
    return value;
}

function saveEventMetadata(modal, closeModal) {
    try {
        submitEventMetadata(collectEventMetadata(modal), modal, closeModal);
    } catch (error) {
        console.error('Error parsing metadata:', error);
        alert("Please check metadata values. JSON values must be valid JSON and number values must be numeric.");
    }
}

export function submitEventMetadata(metadata, modal, closeModal = false) {
    const eventData = JSON.parse(modal.dataset.event);
    eventData.metadata = metadata;

    const eventId = eventData.id;

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
        });
}

function changeEventVenue(eventName, eventId, currentVenueId) {
    const modal = document.getElementById("modalChangeEventVenue");
    modal.dataset.eventId = eventId;
    modal.querySelector("#modalChangeEventVenueTitle").textContent = 'Change Venue for Event ' + eventName;

    const apiUrl = '/api/venues';

    apiFetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            const venueOptions = modal.querySelector('#modalVenueOptions');
            venueOptions.innerHTML = '';
            data.forEach(venue => {
                const radioInput = document.createElement('input');
                radioInput.type = 'radio';
                radioInput.id = venue.id;
                radioInput.name = 'venue';
                radioInput.value = venue.id;
                radioInput.checked = venue.id === currentVenueId;

                const label = document.createElement('label');
                label.htmlFor = venue.id;
                label.textContent = venue.name;

                venueOptions.appendChild(radioInput);
                venueOptions.appendChild(label);
                venueOptions.appendChild(document.createElement('br'));
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
        });
}

function deleteEvent(eventId) {
    if (confirm("Please confirm: delete event will clean up all price settings. And removing will be denied for event with orders placed.")) {
        const apiUrl = `/api/events/${eventId}`;

        apiFetch(apiUrl, {
            method: 'DELETE',
        })
            .then(response => {
                if (response.status === 204) {
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

function showAddEventModal() {
    const modal = document.getElementById("modalAddEvent");
    modal.querySelector("#eventName").value = "";
    modal.querySelector("#venueId").value = "";
    modal.querySelector("#copyEventFromVenue").innerHTML = '<option value="">Select Event</option>';
    modal.style.display = "block";
}

function addNewEvent(form) {

    const eventName = form.querySelector("#eventName").value;
    const venueId = form.querySelector("#venueId").value;
    const copyEventFrom = form.querySelector("#copyEventFromVenue").value;

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
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    const seconds = String(date.getSeconds()).padStart(2, '0');

    return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
}

function toDateTimeLocalValue(value) {
    const date = value instanceof Date ? value : new Date(String(value).replace(" ", "T"));
    if (Number.isNaN(date.getTime())) {
        return "";
    }

    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');

    return `${year}-${month}-${day}T${hours}:${minutes}`;
}
function submitEvent(eventData, copyEventFrom) {
    const apiUrl = '/api/events';
    const headers = new Headers({'Content-Type': 'application/json'});
    if (copyEventFrom) headers.append('X-Copy-From-Id', copyEventFrom);

    return apiFetch(apiUrl, {
        method: 'POST',
        headers: headers,
        body: JSON.stringify(eventData)
    })
        .then(response => {
            if (response.status === 201) {
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

function refreshFormEventVenueList() {
    const apiUrl = '/api/venues';

    apiFetch(apiUrl)
        .then(response => response.json())
        .then(data => {
            const venueSelect = document.getElementById("venueId");
            venueSelect.innerHTML = '<option value="">Select Venue</option>';

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
