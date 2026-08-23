(function () {
    'use strict';

    var root = document.getElementById('serviceWorkRoot');
    if (!root) {
        return;
    }

    var serviceId = root.dataset.serviceId;
    var readOnly = root.dataset.readOnly === 'true';
    var details;
    var activeServiceItemId;
    var selectedDefaultParts = [];

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined && text !== null) {
            node.textContent = text;
        }
        return node;
    }

    function button(text, className, handler) {
        var node = element('button', 'btn btn-sm ' + className, text);
        node.type = 'button';
        node.addEventListener('click', handler);
        return node;
    }

    function money(value) {
        return Number(value || 0).toFixed(2);
    }

    function api(path, method, payload) {
        var options = {
            method: method || 'GET',
            credentials: 'same-origin',
            headers: {}
        };
        if (payload !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(payload);
        }
        return fetch(path, options).then(function (response) {
            if (!response.ok) {
                return response.text().then(function (body) {
                    var message = 'Request failed (' + response.status + ')';
                    try {
                        var parsed = JSON.parse(body);
                        message = parsed.message || parsed.error || message;
                    } catch (ignored) {
                        if (body && body.length < 300) {
                            message = body;
                        }
                    }
                    throw new Error(message);
                });
            }
            return response.json();
        });
    }

    function showError(error) {
        var alert = document.getElementById('serviceWorkError');
        alert.textContent = error.message || String(error);
        alert.classList.remove('d-none');
    }

    function clearError() {
        var alert = document.getElementById('serviceWorkError');
        alert.textContent = '';
        alert.classList.add('d-none');
    }

    function useUpdatedDetails(updated) {
        details = updated;
        clearError();
        renderWork();
        return updated;
    }

    function attributesList(attributes) {
        var wrapper = element('div', 'mb-3');
        Object.keys(attributes || {}).forEach(function (key) {
            var badge = element('span', 'badge badge-light border mr-2 mb-1', key + ': ' + attributes[key]);
            wrapper.appendChild(badge);
        });
        return wrapper;
    }

    function renderWork() {
        var container = document.getElementById('performedServices');
        container.textContent = '';

        if (!details.performedServices || details.performedServices.length === 0) {
            container.appendChild(element(
                'div', 'alert alert-warning',
                readOnly ? 'No performed work was recorded.' : 'Add at least one performed service before completion.'
            ));
        } else {
            details.performedServices.forEach(function (performedService) {
                container.appendChild(renderPerformedService(performedService));
            });
        }

        document.getElementById('finalServiceTotal').textContent = money(details.totalPrice);
        var completionTotal = document.getElementById('completeServiceTotal');
        if (completionTotal) {
            completionTotal.textContent = money(details.totalPrice);
        }
    }

    function renderPerformedService(performedService) {
        var card = element('article', 'card mb-4');
        var header = element('div', 'card-header d-flex justify-content-between align-items-center');
        var heading = element('div');
        heading.appendChild(element('h3', 'h5 mb-1', performedService.name));
        heading.appendChild(element('span', 'text-muted', 'Service total: ' + money(performedService.totalPrice)));
        header.appendChild(heading);

        if (!readOnly) {
            var actions = element('div');
            actions.appendChild(button('Edit', 'btn-outline-primary mr-2', function () {
                openEditService(performedService);
            }));
            actions.appendChild(button('Remove', 'btn-outline-danger', function () {
                removeService(performedService);
            }));
            header.appendChild(actions);
        }
        card.appendChild(header);

        var body = element('div', 'card-body');
        body.appendChild(element('p', 'mb-2', performedService.description));
        body.appendChild(attributesList(performedService.attributes));
        body.appendChild(element('p', 'mb-2', 'Labor price: ' + money(performedService.laborPrice)));

        body.appendChild(element('h4', 'h6 mt-3', 'Performed sub-services'));
        if (!performedService.subServices || performedService.subServices.length === 0) {
            body.appendChild(element('p', 'text-muted', 'No sub-services recorded.'));
        } else {
            var subServices = element('ul');
            performedService.subServices.forEach(function (subService) {
                subServices.appendChild(element('li', null, subService.name));
            });
            body.appendChild(subServices);
        }

        if (performedService.comment) {
            body.appendChild(element('p', 'alert alert-light border', 'Comment: ' + performedService.comment));
        }

        var partsHeading = element('div', 'd-flex justify-content-between align-items-center mt-4 mb-2');
        partsHeading.appendChild(element('h4', 'h6 mb-0', 'Used parts'));
        if (!readOnly) {
            partsHeading.appendChild(button('Add part', 'btn-outline-primary', function () {
                activeServiceItemId = performedService.itemId;
                document.getElementById('partCatalogResults').textContent = '';
                document.getElementById('partCatalogSearch').value = '';
                $('#partCatalogModal').modal('show');
                searchParts('partCatalogResults', '', true);
            }));
        }
        body.appendChild(partsHeading);
        body.appendChild(renderParts(performedService));
        card.appendChild(body);
        return card;
    }

    function renderParts(performedService) {
        if (!performedService.usedParts || performedService.usedParts.length === 0) {
            return element('p', 'text-muted', 'No parts used.');
        }

        var wrapper = element('div', 'table-responsive');
        var table = element('table', 'table table-sm table-bordered');
        var head = element('thead');
        var headRow = element('tr');
        ['Part', 'Quantity', 'Unit price', 'Line total', 'Comment']
            .concat(readOnly ? [] : ['Actions'])
            .forEach(function (label) {
                headRow.appendChild(element('th', null, label));
            });
        head.appendChild(headRow);
        table.appendChild(head);

        var body = element('tbody');
        performedService.usedParts.forEach(function (part) {
            var row = element('tr');
            var nameCell = element('td');
            nameCell.appendChild(element('strong', null, part.name));
            nameCell.appendChild(element('div', 'small text-muted',
                [part.manufacturer, part.partNumber, part.unit].filter(Boolean).join(' · ')));
            row.appendChild(nameCell);

            if (readOnly) {
                row.appendChild(element('td', null, String(part.quantity)));
                row.appendChild(element('td', null, money(part.unitPrice)));
                row.appendChild(element('td', null, money(part.lineTotal)));
                row.appendChild(element('td', null, part.comment || ''));
            } else {
                var quantityCell = element('td');
                var quantity = element('input', 'form-control form-control-sm');
                quantity.type = 'number';
                quantity.min = '0.001';
                quantity.step = '0.001';
                quantity.value = part.quantity;
                quantityCell.appendChild(quantity);
                row.appendChild(quantityCell);

                var priceCell = element('td');
                var price = element('input', 'form-control form-control-sm');
                price.type = 'number';
                price.min = '0';
                price.step = '0.01';
                price.value = part.unitPrice;
                priceCell.appendChild(price);
                row.appendChild(priceCell);
                row.appendChild(element('td', null, money(part.lineTotal)));

                var commentCell = element('td');
                var comment = element('input', 'form-control form-control-sm');
                comment.maxLength = 2000;
                comment.value = part.comment || '';
                commentCell.appendChild(comment);
                row.appendChild(commentCell);

                var actionCell = element('td', 'text-nowrap');
                actionCell.appendChild(button('Save', 'btn-outline-primary mr-1', function () {
                    updatePart(performedService.itemId, part.itemId, quantity.value, price.value, comment.value);
                }));
                actionCell.appendChild(button('Delete', 'btn-outline-danger', function () {
                    removePart(performedService.itemId, part.itemId);
                }));
                row.appendChild(actionCell);
            }
            body.appendChild(row);
        });
        table.appendChild(body);
        wrapper.appendChild(table);
        return wrapper;
    }

    function openEditService(performedService) {
        document.getElementById('editServiceItemId').value = performedService.itemId;
        document.getElementById('editServiceLaborPrice').value = performedService.laborPrice;
        document.getElementById('editServiceComment').value = performedService.comment || '';
        document.getElementById('editServiceSubServices').value = (performedService.subServices || [])
            .map(function (subService) { return subService.name; }).join('\n');
        $('#editPerformedServiceModal').modal('show');
    }

    function lines(value) {
        return String(value || '').split(/\r?\n/)
            .map(function (line) { return line.trim(); })
            .filter(Boolean);
    }

    function parsePrimitive(value) {
        var trimmed = value.trim();
        if (trimmed === 'true' || trimmed === 'false') {
            return trimmed === 'true';
        }
        if (trimmed !== '' && !isNaN(Number(trimmed))) {
            return Number(trimmed);
        }
        return trimmed;
    }

    function attributes(value) {
        var result = {};
        lines(value).forEach(function (line) {
            var separator = line.indexOf('=');
            if (separator <= 0) {
                throw new Error('Attributes must use one key=value pair per line.');
            }
            var key = line.substring(0, separator).trim();
            if (!key || key.indexOf('$') !== -1 || key.indexOf('.') !== -1) {
                throw new Error('Attribute keys cannot contain $ or dots.');
            }
            result[key] = parsePrimitive(line.substring(separator + 1));
        });
        return result;
    }

    function searchServiceTypes() {
        api('/api/catalog/service-types/search', 'POST', {
            query: document.getElementById('serviceTypeSearch').value
        }).then(renderServiceTypeResults).catch(showError);
    }

    function renderServiceTypeResults(items) {
        var results = document.getElementById('serviceTypeResults');
        results.textContent = '';
        if (!items.length) {
            results.appendChild(element('div', 'alert alert-secondary', 'No service types found.'));
            return;
        }
        items.forEach(function (item) {
            var row = element('div', 'border rounded p-2 mb-2 d-flex justify-content-between align-items-center');
            var description = element('div');
            description.appendChild(element('strong', null, item.name));
            description.appendChild(element('div', 'small text-muted',
                item.description + ' · Average labor: ' + money(item.averageLaborPrice)));
            row.appendChild(description);
            row.appendChild(button('Add', 'btn-primary', function () {
                api('/api/services/' + serviceId + '/work/services/from-catalog', 'POST', {
                    catalogId: item.id,
                    version: details.version
                }).then(function (updated) {
                    useUpdatedDetails(updated);
                    $('#serviceCatalogModal').modal('hide');
                }).catch(showError);
            }));
            results.appendChild(row);
        });
    }

    function partFilters(query) {
        var filters = {};
        if (query && query.trim()) {
            filters.name = query.trim();
        }
        return {filters: filters};
    }

    function searchParts(targetId, query, forService) {
        api('/api/catalog/parts/search', 'POST', partFilters(query))
            .then(function (items) {
                renderPartResults(targetId, items, forService);
            }).catch(showError);
    }

    function renderPartResults(targetId, items, forService) {
        var results = document.getElementById(targetId);
        results.textContent = '';
        if (!items.length) {
            results.appendChild(element('div', 'alert alert-secondary', 'No parts found.'));
            return;
        }
        items.forEach(function (item) {
            var row = element('div', 'border rounded p-2 mb-2 d-flex justify-content-between align-items-center');
            var description = element('div');
            description.appendChild(element('strong', null, item.name));
            description.appendChild(element('div', 'small text-muted',
                [item.manufacturer, item.partNumber, item.unit, money(item.averageUnitPrice)]
                    .filter(Boolean).join(' · ')));
            row.appendChild(description);

            var controls = element('div', 'd-flex align-items-center');
            var quantity = element('input', 'form-control form-control-sm mr-2');
            quantity.type = 'number';
            quantity.min = '0.001';
            quantity.step = '0.001';
            quantity.value = '1';
            quantity.style.width = '90px';
            controls.appendChild(quantity);
            controls.appendChild(button(forService ? 'Add' : 'Use', 'btn-primary', function () {
                if (forService) {
                    api('/api/services/' + serviceId + '/work/services/' + activeServiceItemId
                        + '/parts/from-catalog', 'POST', {
                        catalogId: item.id,
                        quantity: quantity.value,
                        version: details.version
                    }).then(function (updated) {
                        useUpdatedDetails(updated);
                        $('#partCatalogModal').modal('hide');
                    }).catch(showError);
                } else {
                    addDefaultPart(item, quantity.value);
                }
            }));
            row.appendChild(controls);
            results.appendChild(row);
        });
    }

    function addDefaultPart(part, quantity) {
        if (selectedDefaultParts.some(function (selected) { return selected.part.id === part.id; })) {
            showError(new Error('This default part is already selected.'));
            return;
        }
        selectedDefaultParts.push({part: part, quantity: quantity});
        renderSelectedDefaultParts();
    }

    function renderSelectedDefaultParts() {
        var container = document.getElementById('selectedDefaultParts');
        container.textContent = '';
        selectedDefaultParts.forEach(function (selected) {
            var row = element('div', 'd-flex justify-content-between align-items-center border rounded p-2 mb-1');
            row.appendChild(element('span', null, selected.part.name + ' × ' + selected.quantity));
            row.appendChild(button('Remove', 'btn-outline-danger', function () {
                selectedDefaultParts = selectedDefaultParts.filter(function (current) {
                    return current.part.id !== selected.part.id;
                });
                renderSelectedDefaultParts();
            }));
            container.appendChild(row);
        });
    }

    function createAndAddService() {
        try {
            var serviceType = {
                name: document.getElementById('newServiceName').value,
                description: document.getElementById('newServiceDescription').value,
                averageLaborPrice: document.getElementById('newServiceLaborPrice').value,
                attributes: attributes(document.getElementById('newServiceAttributes').value),
                defaultSubServices: lines(document.getElementById('newServiceSubServices').value)
                    .map(function (name) { return {name: name}; }),
                defaultParts: selectedDefaultParts.map(function (selected) {
                    return {catalogPartId: selected.part.id, defaultQuantity: selected.quantity};
                })
            };
            api('/api/services/' + serviceId + '/work/services/new', 'POST', {
                version: details.version,
                serviceType: serviceType
            }).then(function (updated) {
                useUpdatedDetails(updated);
                $('#serviceCatalogModal').modal('hide');
            }).catch(showError);
        } catch (error) {
            showError(error);
        }
    }

    function createAndAddPart() {
        try {
            var part = {
                name: document.getElementById('newPartName').value,
                unit: document.getElementById('newPartUnit').value,
                averageUnitPrice: document.getElementById('newPartPrice').value,
                manufacturer: document.getElementById('newPartManufacturer').value,
                partNumber: document.getElementById('newPartNumber').value,
                description: document.getElementById('newPartDescription').value,
                attributes: attributes(document.getElementById('newPartAttributes').value)
            };
            api('/api/services/' + serviceId + '/work/services/' + activeServiceItemId + '/parts/new', 'POST', {
                version: details.version,
                quantity: document.getElementById('newPartQuantity').value,
                part: part
            }).then(function (updated) {
                useUpdatedDetails(updated);
                $('#partCatalogModal').modal('hide');
            }).catch(showError);
        } catch (error) {
            showError(error);
        }
    }

    function savePerformedService() {
        var itemId = document.getElementById('editServiceItemId').value;
        api('/api/services/' + serviceId + '/work/services/' + itemId, 'PUT', {
            version: details.version,
            laborPrice: document.getElementById('editServiceLaborPrice').value,
            subServices: lines(document.getElementById('editServiceSubServices').value),
            comment: document.getElementById('editServiceComment').value
        }).then(function (updated) {
            useUpdatedDetails(updated);
            $('#editPerformedServiceModal').modal('hide');
        }).catch(showError);
    }

    function removeService(performedService) {
        if (!window.confirm('Remove ' + performedService.name + ' from this service?')) {
            return;
        }
        api('/api/services/' + serviceId + '/work/services/' + performedService.itemId, 'DELETE', {
            version: details.version
        }).then(useUpdatedDetails).catch(showError);
    }

    function updatePart(serviceItemId, partItemId, quantity, unitPrice, comment) {
        api('/api/services/' + serviceId + '/work/services/' + serviceItemId + '/parts/' + partItemId,
            'PUT', {
                version: details.version,
                quantity: quantity,
                unitPrice: unitPrice,
                comment: comment
            }).then(useUpdatedDetails).catch(showError);
    }

    function removePart(serviceItemId, partItemId) {
        if (!window.confirm('Remove this part from the performed service?')) {
            return;
        }
        api('/api/services/' + serviceId + '/work/services/' + serviceItemId + '/parts/' + partItemId,
            'DELETE', {version: details.version})
            .then(useUpdatedDetails).catch(showError);
    }

    if (!readOnly) {
        document.getElementById('searchServiceTypes').addEventListener('click', searchServiceTypes);
        document.getElementById('toggleNewServiceType').addEventListener('click', function () {
            document.getElementById('newServiceTypeForm').classList.toggle('d-none');
        });
        document.getElementById('searchDefaultParts').addEventListener('click', function () {
            searchParts('defaultPartSearchResults', document.getElementById('newServicePartSearch').value, false);
        });
        document.getElementById('createAndAddServiceType').addEventListener('click', createAndAddService);
        document.getElementById('savePerformedService').addEventListener('click', savePerformedService);
        document.getElementById('searchPartCatalog').addEventListener('click', function () {
            searchParts('partCatalogResults', document.getElementById('partCatalogSearch').value, true);
        });
        document.getElementById('toggleNewPart').addEventListener('click', function () {
            document.getElementById('newPartForm').classList.toggle('d-none');
        });
        document.getElementById('createAndAddPart').addEventListener('click', createAndAddPart);
        $('#serviceCatalogModal').on('shown.bs.modal', function () {
            searchServiceTypes();
        });
    }

    api('/api/services/' + serviceId + '/work')
        .then(useUpdatedDetails)
        .catch(showError);
}());
