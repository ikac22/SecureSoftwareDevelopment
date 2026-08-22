package com.zuehlke.securesoftwaredevelopment.domain;

public class Technician {
    private final String id;
    private final String displayName;
    private final String email;

    public Technician(String id, String displayName, String email) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }
}
