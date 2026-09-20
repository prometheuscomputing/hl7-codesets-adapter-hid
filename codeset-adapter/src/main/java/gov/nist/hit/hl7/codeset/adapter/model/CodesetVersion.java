package gov.nist.hit.hl7.codeset.adapter.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Document
public class CodesetVersion {
    @Id
    private String id;
    private String codesetId;

    private String version;

    private boolean exposed;

    private Date dateCommitted;

    @CreatedDate
    private Date dateCreated;

    @LastModifiedDate
    private Date dateUpdated;

    private String comments;


    private boolean deprecated;
    private CodesStatus codesStatus;

    public enum CodesStatus {
        NOT_NEEDED, PENDING, SAVED
    }

    public CodesetVersion() {
        super();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCodesetId() {
        return codesetId;
    }

    public void setCodesetId(String codesetId) {
        this.codesetId = codesetId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isExposed() {
        return exposed;
    }

    public void setExposed(boolean exposed) {
        this.exposed = exposed;
    }

    public Date getDateCommitted() {
        return dateCommitted;
    }

    public void setDateCommitted(Date dateCommited) {
        this.dateCommitted = dateCommited;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }




    public Date getDateCreated() {
        return dateCreated;
    }


    public void setDateCreated(Date dateCreated) {
        this.dateCreated = dateCreated;
    }



    public Date getDateUpdated() {
        return dateUpdated;
    }


    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }


    public boolean isDeprecated() {
        return deprecated;
    }


    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public CodesStatus getCodesStatus() {
        return codesStatus;
    }

    public void setCodesStatus(CodesStatus codesStatus) {
        this.codesStatus = codesStatus;
    }
}