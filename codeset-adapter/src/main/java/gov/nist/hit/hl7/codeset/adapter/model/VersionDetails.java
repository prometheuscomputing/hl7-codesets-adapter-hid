package gov.nist.hit.hl7.codeset.adapter.model;

import java.util.Date;

public class VersionDetails {
    private String id;

    private String version;
    private Date date;

    public VersionDetails() {
    }

    public VersionDetails(String version, Date date) {
        this.version = version;
        this.date = date;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

}
