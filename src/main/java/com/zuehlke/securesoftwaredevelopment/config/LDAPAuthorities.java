package com.zuehlke.securesoftwaredevelopment.config;

public final class LDAPAuthorities {

    private LDAPAuthorities() {
    }

    public static final String TECHNICIAN = "ROLE_SERVICE_TECHNICIANS";
    public static final String SERVICE_MANAGER = "ROLE_SERVICE_MANAGERS";

    public static final String IS_TECHNICIAN = "hasAuthority('" + TECHNICIAN + "')";
    public static final String IS_SERVICE_MANAGER = "hasAuthority('" + SERVICE_MANAGER + "')";
    public static final String IS_SERVICE_STAFF = IS_TECHNICIAN + " or " + IS_SERVICE_MANAGER;
}
