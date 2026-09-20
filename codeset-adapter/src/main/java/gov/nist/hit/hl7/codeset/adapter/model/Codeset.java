package gov.nist.hit.hl7.codeset.adapter.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;

import java.util.*;

import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DBRef;
import org.springframework.data.annotation.Id;


@Document
public class Codeset {

    @Id
    private String id;

    private VersionMetadata latestVersion;
    private List<VersionMetadata> versions;
    private Date dateUpdated;
    private String name;
    private String description;
    private String username;
    private String provider;
    private String identifier;
    @DBRef
    private Set<CodesetVersion> codeSetVersions;

    public Codeset() {
        super();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }


    public VersionMetadata getLatestVersion() {
        return latestVersion;
    }

    public void setLatestVersion(VersionMetadata latestVersion) {
        this.latestVersion = latestVersion;
    }

    public List<VersionMetadata> getVersions() {
        return versions;
    }

    public void setVersions(List<VersionMetadata> versions) {
        this.versions = versions;
    }

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Date dateUpdated) {
        this.dateUpdated = dateUpdated;
    }

    public Set<CodesetVersion> getCodeSetVersions() {
        return codeSetVersions;
    }

    public void setCodeSetVersions(Set<CodesetVersion> codeSetVersions) {
        this.codeSetVersions = codeSetVersions;
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

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

}