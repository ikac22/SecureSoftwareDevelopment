package com.zuehlke.securesoftwaredevelopment.domain;

public class Person {
    private int id;
    private String firstName;
    private String lastName;
    private String personalNumber;
    private String address;
    private String partnerCode;

    public Person() {
    }

    public Person(int id, String firstName, String lastName, String personalNumber, String address) {
        this(id, firstName, lastName, personalNumber, address, null);
    }

    public Person(int id, String firstName, String lastName, String personalNumber, String address, String partnerCode) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.personalNumber = personalNumber;
        this.address = address;
        this.partnerCode = partnerCode;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getPersonalNumber() {
        return personalNumber;
    }

    public void setPersonalNumber(String personalNumber) {
        this.personalNumber = personalNumber;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode) {
        this.partnerCode = partnerCode;
    }
}
