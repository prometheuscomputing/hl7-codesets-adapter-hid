package gov.nist.hit.hl7.codeset.adapter.repository;

import gov.nist.hit.hl7.codeset.adapter.model.CodesetVersion;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

@Repository
public interface CodesetVersionRepository extends MongoRepository<CodesetVersion, String> {
    Optional<CodesetVersion> findByCodesetIdAndVersion(String codesetId, String version);

}