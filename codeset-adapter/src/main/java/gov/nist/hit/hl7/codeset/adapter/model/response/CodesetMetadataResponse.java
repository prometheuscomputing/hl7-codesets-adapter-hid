package gov.nist.hit.hl7.codeset.adapter.model.response;

import gov.nist.hit.hl7.codeset.adapter.model.VersionDetails;
import gov.nist.hit.hl7.codeset.adapter.model.VersionMetadata;

import java.util.List;

public class CodesetMetadataResponse {
    private String identifier;
    private String name;
    private VersionMetadata latestStableVersion;
    private List<VersionMetadata> versions;



    public CodesetMetadataResponse() {
    }

    public CodesetMetadataResponse(String identifier, String name, List<VersionMetadata> versions) {
        this.identifier = identifier;
        this.name = name;
        this.versions = versions;
    }

    public String getId() {
        return identifier;
    }

    public void setId(String id) {
        this.identifier = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public VersionMetadata getLatestStableVersion() {
        return latestStableVersion;
    }

    public void setLatestStableVersion(VersionMetadata latestStableVersion) {
        this.latestStableVersion = latestStableVersion;
    }

    public List<VersionMetadata> getVersions() {
        return versions;
    }

    public void setVersions(List<VersionMetadata> versions) {
        this.versions = versions;
    }

}
