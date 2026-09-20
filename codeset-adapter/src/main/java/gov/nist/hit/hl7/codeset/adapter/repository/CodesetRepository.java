package gov.nist.hit.hl7.codeset.adapter.repository;

import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.CodesetVersion;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CodesetRepository extends MongoRepository<Codeset, String> {
    Optional<Codeset> findByIdentifier(String codesetId);




}