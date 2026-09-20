package gov.nist.hit.hl7.codeset.adapter.service;


import gov.nist.hit.hl7.codeset.adapter.exception.NotFoundException;
import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetRequest;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetVersionMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.ProvidersResponse;

import java.io.IOException;
import java.util.List;

public interface CodesetService {
    public List<ProvidersResponse> getProviders() throws IOException;
    public List<CodesetMetadataResponse> getCodesets(String provider, CodesetSearchCriteria codesetSearchCriteria) throws IOException;
    public CodesetMetadataResponse getCodesetMetadata(String provider, String id) throws IOException, NotFoundException;
    public CodesetResponse getCodeset(String provider, String id, CodesetSearchCriteria criteria) throws IOException, NotFoundException;
    public CodesetVersionMetadataResponse getCodesetVersionMetadata(String provider, String id, String version) throws IOException, NotFoundException;

}
