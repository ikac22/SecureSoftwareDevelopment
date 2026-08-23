package com.zuehlke.securesoftwaredevelopment.domain;

public class ViewComment {
    private String personName;
    private String comment;
    private String imagePath;

    public ViewComment(String personName, String comment, String imagePath) {
        this.personName = personName;
        this.comment = comment;
        this.imagePath = imagePath;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }
}
