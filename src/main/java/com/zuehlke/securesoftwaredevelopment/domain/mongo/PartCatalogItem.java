package com.zuehlke.securesoftwaredevelopment.domain.mongo;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Document(collection = "parts")
public class PartCatalogItem {
    @Id
    private String id;

    @Indexed(unique = true)
    private String normalizedKey;

    private String name;
    private String description;
    private String partNumber;
    private String manufacturer;
    private String unit;
    private BigDecimal averageUnitPrice;
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public PartCatalogItem() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNormalizedKey() {
        return normalizedKey;
    }

    public void setNormalizedKey(String normalizedKey) {
        this.normalizedKey = normalizedKey;
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

    public BigDecimal getAverageUnitPrice() {
        return averageUnitPrice;
    }

    public void setAverageUnitPrice(BigDecimal averageUnitPrice) {
        this.averageUnitPrice = averageUnitPrice;
    }

    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes == null ? new LinkedHashMap<>() : new LinkedHashMap<>(attributes);
    }
}
