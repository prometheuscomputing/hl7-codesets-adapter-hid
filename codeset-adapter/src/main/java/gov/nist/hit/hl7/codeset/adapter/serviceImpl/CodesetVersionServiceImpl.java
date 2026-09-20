package gov.nist.hit.hl7.codeset.adapter.serviceImpl;

import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.CodesetVersion;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetVersionRequest;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetRepository;
import gov.nist.hit.hl7.codeset.adapter.repository.CodesetVersionRepository;
import gov.nist.hit.hl7.codeset.adapter.service.CodesetVersionService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.Optional;

@Service
public class CodesetVersionServiceImpl implements CodesetVersionService {
    private final CodesetRepository codesetRepository;
    private final CodesetVersionRepository codesetVersionRepository;

    public CodesetVersionServiceImpl(CodesetRepository codesetRepository, CodesetVersionRepository codesetVersionRepository) {
        this.codesetRepository = codesetRepository;
        this.codesetVersionRepository = codesetVersionRepository;
    }

    @Override
    public CodesetVersion addVersionToCodeset(String codesetId, CodesetVersionRequest codesetVersionRequest) {
        Optional<Codeset> codesetOptional = codesetRepository.findById(codesetId);
        if (!codesetOptional.isPresent()) {
            throw new IllegalArgumentException("Codeset not found with ID: " + codesetId);
        }
        Codeset codeset = codesetOptional.get();
        Optional<CodesetVersion> existingCodesetVersion = codesetVersionRepository.findByCodesetIdAndVersion(codeset.getId(),codesetVersionRequest.getVersion());
        if(existingCodesetVersion.isPresent()){
            throw new IllegalArgumentException("Version " + codesetVersionRequest.getVersion() + " already exists for this Codeset");
        }
        CodesetVersion newCodesetVersion = new CodesetVersion(
        );
        codesetVersionRepository.save(newCodesetVersion);
        return  newCodesetVersion;

    }

    @Override
    public CodesetVersion getVersionDetails(String codesetId, String version) {
        Optional<Codeset> codesetOptional = codesetRepository.findById(codesetId);
        if (!codesetOptional.isPresent()) {
            throw new IllegalArgumentException("Codeset not found with ID: " + codesetId);
        }
        Optional<CodesetVersion> codesetVersion = codesetVersionRepository.findByCodesetIdAndVersion(codesetId, version);
        if(!codesetVersion.isPresent()){
            throw new IllegalArgumentException("Version " + version+ " doesn't exist for this Codeset");
        }
        return  codesetVersion.get();

    }
}
