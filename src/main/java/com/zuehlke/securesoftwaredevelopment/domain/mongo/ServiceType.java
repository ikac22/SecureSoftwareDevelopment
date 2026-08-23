package com.zuehlke.securesoftwaredevelopment.domain.mongo;

import com.fasterxml.jackson.annotation.JsonAlias;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Document(collection = "serviceTypes")
public class ServiceType {
    @Id
    private String id;

    @Indexed(unique = true)
    private String normalizedName;

    @Indexed
    private String name;
    private String description;
    private Map<String, Object> attributes = new LinkedHashMap<>();
    private BigDecimal averageLaborPrice;
    private List<SubServiceTemplate> defaultSubServices = new ArrayList<>();
    private List<DefaultPart> defaultParts = new ArrayList<>();

    public ServiceType() {
    }

    public String getId() {
        return id;
    }

    @JsonAlias("_id")
    public void setId(String id) {
        this.id = id;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
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

    public BigDecimal getAverageLaborPrice() {
        return averageLaborPrice;
    }

    public void setAverageLaborPrice(BigDecimal averageLaborPrice) {
        this.averageLaborPrice = averageLaborPrice;
    }

    public List<SubServiceTemplate> getDefaultSubServices() {
        return defaultSubServices;
    }

    public void setDefaultSubServices(List<SubServiceTemplate> defaultSubServices) {
        this.defaultSubServices = defaultSubServices == null ? new ArrayList<>() : defaultSubServices;
    }

    public List<DefaultPart> getDefaultParts() {
        return defaultParts;
    }

    public void setDefaultParts(List<DefaultPart> defaultParts) {
        this.defaultParts = defaultParts == null ? new ArrayList<>() : defaultParts;
    }

    public static class SubServiceTemplate {
        private String templateId;
        private String name;

        public SubServiceTemplate() {
        }

        public SubServiceTemplate(String templateId, String name) {
            this.templateId = templateId;
            this.name = name;
        }

        public String getTemplateId() {
            return templateId;
        }

        public void setTemplateId(String templateId) {
            this.templateId = templateId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class DefaultPart {
        private String catalogPartId;
        private BigDecimal defaultQuantity;

        public DefaultPart() {
        }

        public DefaultPart(String catalogPartId, BigDecimal defaultQuantity) {
            this.catalogPartId = catalogPartId;
            this.defaultQuantity = defaultQuantity;
        }

        public String getCatalogPartId() {
            return catalogPartId;
        }

        public void setCatalogPartId(String catalogPartId) {
            this.catalogPartId = catalogPartId;
        }

        public BigDecimal getDefaultQuantity() {
            return defaultQuantity;
        }

        public void setDefaultQuantity(BigDecimal defaultQuantity) {
            this.defaultQuantity = defaultQuantity;
        }
    }
}
