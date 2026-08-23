package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.domain.mongo.PartCatalogItem;
import com.zuehlke.securesoftwaredevelopment.domain.mongo.ServiceType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServiceWorkRequests {
    private ServiceWorkRequests() {
    }

    public static class ServiceTypeSearch {
        private String query;

        public String getQuery() {
            return query;
        }

        public void setQuery(String query) {
            this.query = query;
        }
    }

    public static class PartSearch {
        private Map<String, Object> filters = new LinkedHashMap<>();

        public Map<String, Object> getFilters() {
            return filters;
        }

        public void setFilters(Map<String, Object> filters) {
            this.filters = filters;
        }
    }

    public static class CatalogSelection {
        private String catalogId;
        private Long version;

        public String getCatalogId() {
            return catalogId;
        }

        public void setCatalogId(String catalogId) {
            this.catalogId = catalogId;
        }

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }
    }

    public static class NewServiceType {
        private Long version;
        private ServiceType serviceType;

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }

        public ServiceType getServiceType() {
            return serviceType;
        }

        public void setServiceType(ServiceType serviceType) {
            this.serviceType = serviceType;
        }
    }

    public static class PerformedServiceUpdate {
        private Long version;
        private BigDecimal laborPrice;
        private String comment;
        private List<String> subServices = new ArrayList<>();

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }

        public BigDecimal getLaborPrice() {
            return laborPrice;
        }

        public void setLaborPrice(BigDecimal laborPrice) {
            this.laborPrice = laborPrice;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public List<String> getSubServices() {
            return subServices;
        }

        public void setSubServices(List<String> subServices) {
            this.subServices = subServices == null ? new ArrayList<>() : subServices;
        }
    }

    public static class NewPart {
        private Long version;
        private PartCatalogItem part;
        private BigDecimal quantity;

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }

        public PartCatalogItem getPart() {
            return part;
        }

        public void setPart(PartCatalogItem part) {
            this.part = part;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }
    }

    public static class PartSelection extends CatalogSelection {
        private BigDecimal quantity;

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }
    }

    public static class UsedPartUpdate {
        private Long version;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private String comment;

        public Long getVersion() {
            return version;
        }

        public void setVersion(Long version) {
            this.version = version;
        }

        public BigDecimal getQuantity() {
            return quantity;
        }

        public void setQuantity(BigDecimal quantity) {
            this.quantity = quantity;
        }

        public BigDecimal getUnitPrice() {
            return unitPrice;
        }

        public void setUnitPrice(BigDecimal unitPrice) {
            this.unitPrice = unitPrice;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }
    }
}
