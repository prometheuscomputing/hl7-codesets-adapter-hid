package gov.nist.hit.hl7.codeset.adapter.service;

import gov.nist.hit.hl7.codeset.adapter.exception.NotFoundException;
import gov.nist.hit.hl7.codeset.adapter.model.Code;
import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.Provider;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetVersionMetadataResponse;

import java.io.IOException;
import java.util.List;

public interface ProviderService {
    public List<CodesetMetadataResponse> getCodesets(CodesetSearchCriteria codesetSearchCriteria) throws IOException;
    public Provider getProvider();
    public void getCodesetAndSave(String id, String version) throws IOException;
    public String getLatestVersion(String id) throws IOException;
    public List<Code> getCodes(String id, String version, String match) throws IOException;
    public CodesetMetadataResponse getCodesetMetadata(String id) throws IOException, NotFoundException;
    public CodesetVersionMetadataResponse getCodesetVersionMetadata(String id, String version) throws NotFoundException;

}
