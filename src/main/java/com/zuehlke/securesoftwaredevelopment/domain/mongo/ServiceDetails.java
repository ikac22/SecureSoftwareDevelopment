package com.zuehlke.securesoftwaredevelopment.domain.mongo;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "serviceDetails")
public class ServiceDetails {
    @Id
    private String id;

    @Indexed(unique = true)
    private Integer serviceId;

    @Version
    private Long version;

    private List<PerformedService> performedServices = new ArrayList<>();
    private BigDecimal totalPrice = BigDecimal.ZERO;
    private Instant updatedAt;

    public ServiceDetails() {
    }

    public ServiceDetails(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public String getId() {
        return id;
    }

    @JsonAlias("_id")
    public void setId(String id) {
        this.id = id;
    }

    public Integer getServiceId() {
        return serviceId;
    }

    public void setServiceId(Integer serviceId) {
        this.serviceId = serviceId;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public List<PerformedService> getPerformedServices() {
        return performedServices;
    }

    public void setPerformedServices(List<PerformedService> performedServices) {
        this.performedServices = performedServices == null ? new ArrayList<>() : performedServices;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(BigDecimal totalPrice) {
        this.totalPrice = totalPrice;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static class PerformedService {
        private String itemId;
        private String catalogServiceId;
        private String name;
        private String description;
        private Map<String, Object> attributes = new LinkedHashMap<>();
        private BigDecimal laborPrice;
        private List<PerformedSubService> subServices = new ArrayList<>();
        private List<UsedPart> usedParts = new ArrayList<>();
        private String comment;
        private BigDecimal totalPrice = BigDecimal.ZERO;

        public PerformedService() {
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getCatalogServiceId() {
            return catalogServiceId;
        }

        public void setCatalogServiceId(String catalogServiceId) {
            this.catalogServiceId = catalogServiceId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
        }

        public BigDecimal getLaborPrice() {
            return laborPrice;
        }

        public void setLaborPrice(BigDecimal laborPrice) {
            this.laborPrice = laborPrice;
        }

        public List<PerformedSubService> getSubServices() {
            return subServices;
        }

        public void setSubServices(List<PerformedSubService> subServices) {
            this.subServices = subServices == null ? new ArrayList<>() : subServices;
        }

        public List<UsedPart> getUsedParts() {
            return usedParts;
        }

        public void setUsedParts(List<UsedPart> usedParts) {
            this.usedParts = usedParts == null ? new ArrayList<>() : usedParts;
        }

        public String getComment() {
            return comment;
        }

        public void setComment(String comment) {
            this.comment = comment;
        }

        public BigDecimal getTotalPrice() {
            return totalPrice;
        }

        public void setTotalPrice(BigDecimal totalPrice) {
            this.totalPrice = totalPrice;
        }
    }

    public static class PerformedSubService {
        private String itemId;
        private String name;

        public PerformedSubService() {
        }

        public PerformedSubService(String itemId, String name) {
            this.itemId = itemId;
            this.name = name;
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class UsedPart {
        private String itemId;
        private String catalogPartId;
        private String name;
        private String description;
        private String partNumber;
        private String manufacturer;
        private String unit;
        private Map<String, Object> attributes = new LinkedHashMap<>();
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private String comment;
        private BigDecimal lineTotal = BigDecimal.ZERO;

        public UsedPart() {
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public String getCatalogPartId() {
            return catalogPartId;
        }

        public void setCatalogPartId(String catalogPartId) {
            this.catalogPartId = catalogPartId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getPartNumber() {
            return partNumber;
        }

        public void setPartNumber(String partNumber) {
            this.partNumber = partNumber;
        }

        public String getManufacturer() {
            return manufacturer;
        }

        public void setManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
        }

        public String getUnit() {
            return unit;
        }

        public void setUnit(String unit) {
            this.unit = unit;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
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

        public BigDecimal getLineTotal() {
            return lineTotal;
        }

        public void setLineTotal(BigDecimal lineTotal) {
            this.lineTotal = lineTotal;
        }
    }
}
