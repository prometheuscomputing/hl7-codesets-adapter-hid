package gov.nist.hit.hl7.codeset.adapter.utils;

import com.caucho.hessian.client.HessianProxyFactory;
import gov.cdc.vocab.service.VocabService;
import gov.cdc.vocab.service.bean.CodeSystem;
import gov.cdc.vocab.service.bean.ValueSet;
import gov.cdc.vocab.service.bean.ValueSetConcept;
import gov.cdc.vocab.service.bean.ValueSetVersion;
import gov.cdc.vocab.service.dto.input.CodeSystemSearchCriteriaDto;
import gov.cdc.vocab.service.dto.input.ValueSetSearchCriteriaDto;
import gov.cdc.vocab.service.dto.output.ValueSetConceptResultDto;
import gov.cdc.vocab.service.dto.output.ValueSetResultDto;
import gov.nist.hit.hl7.codeset.adapter.model.Code;
import gov.nist.hit.hl7.codeset.adapter.model.Codeset;
import gov.nist.hit.hl7.codeset.adapter.model.CodesetVersion;
import gov.nist.hit.hl7.codeset.adapter.model.VersionMetadata;
import gov.nist.hit.hl7.codeset.adapter.model.phinvads.PhinvadsValueset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.net.ssl.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class NewPhinvadsScheduler {

    private static final Logger log = LoggerFactory.getLogger(NewPhinvadsScheduler.class);

    private VocabService service;
    @Autowired
    MongoOperations mongoOps;
//	private MongoOperations mongoOps;


    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    public NewPhinvadsScheduler() throws NoSuchAlgorithmException, KeyManagementException {
        String serviceUrl = "https://phinvads.cdc.gov/vocabService/v2";
        /* Start of Fix */
        TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }

        }};


        HessianProxyFactory factory = new HessianProxyFactory();
        try {
            SSLContext sc = SSLContext.getInstance("SSL");

            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = new HostnameVerifier() {
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
            // Install the all-trusting host verifier
            HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            /* End of the fix*/
            setService((VocabService) factory.create(VocabService.class, serviceUrl));
//			mongoOps = new MongoTemplate(new SimpleMongoDbFactory(new MongoClient(), "vocabulary-service"));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

    }

//    @PostConstruct
    public void initPhinvads() throws IOException {
        System.out.println("************ INIT PHINVADS VALUESET METADATA1");
        reportCurrentTime();
    }

    // Every 1:00 AM Saturday
//    @Scheduled(cron = "0 0 1 * * SAT")
    public void reportCurrentTime() {
        log.info("The time is now {}" + dateFormat.format(new Date()));
        log.info("PHINVADSValueSetDigger started at " + new Date());

        List<ValueSet> vss = this.service.getAllValueSets().getValueSets();
        log.info(vss.size() + " value sets' info has been found!");
        int count = 0;
        for (ValueSet vs : vss) {
            count++;
            log.info("########" + count + "/" + vss.size() + "########");
            this.tableSaveOrUpdate(vs);
        }
        log.info("PHINVADSValueSetDigger ended at " + new Date());
    }

    public Codeset tableSaveOrUpdate(ValueSet vs) {
        // 1. Get metadata from PHINVADS web service
        log.info("Get metadata from PHINVADS web service for " + vs.getOid());
        ValueSetVersion vsv = null;

        List<ValueSetVersion> vsvList = this.getService().getValueSetVersionsByValueSetOid(vs.getOid()).getValueSetVersions();
        vsv = vsvList.get(0);
        log.info("Successfully got the metadata from PHINVADS web service for " + vs.getOid());
        log.info(vs.getOid() + " last updated date is " + vs.getStatusDate().toString());
        log.info(vs.getOid() + " the Version number is " + vsv.getVersionNumber());


        // 2. Get Codeset from DB
        log.info("Get metadata from DB for " + vs.getOid());

        Codeset codeset = null;
        codeset = mongoOps.findOne(Query.query(Criteria.where("phinvadsOid").is(vs.getOid())),
                Codeset.class);

        if (codeset != null) {
            log.info("Successfully got the metadata from DBe for " + vs.getOid());
            log.info(vs.getOid() + " last updated date is " + codeset.getDateUpdated());
            log.info(vs.getOid() + " the Version number is " + codeset.getLatestVersion());
        } else {
            log.info("Failed to get the metadata from DB for " + vs.getOid());
        }

        ValueSetConceptResultDto vscByVSVid = null;
        List<ValueSetConcept> valueSetConcepts = null;

        // 3. compare metadata
        boolean needUpdate = false;
        if (vsv != null) {
            if (codeset != null) {
                if (codeset.getDateUpdated() != null && codeset.getDateUpdated().equals(vs.getStatusDate())
                        && codeset.getLatestVersion().getVersion().equals(String.valueOf(vsv.getVersionNumber()))) {
                    CodesetVersion codesetVersion = mongoOps.findOne(Query.query(Criteria.where("version").is(codeset.getLatestVersion())),
                            CodesetVersion.class);

                    if (codesetVersion != null) {
                        Code code = mongoOps.findOne(Query.query(Criteria.where("codesetversionId").is(codesetVersion.getId())),
                                Code.class);
                        if (code != null) {
                            vscByVSVid = this.getService().getValueSetConceptsByValueSetVersionId(vsv.getId(), 1, Integer.MAX_VALUE);
                            valueSetConcepts = vscByVSVid.getValueSetConcepts();
                            if (valueSetConcepts.size() != 0) {
                                needUpdate = true;
                                log.info(vs.getOid() + " Codeset has no change! however local PHINVADS codes may be missing");
                            }
                        }
                    }

                } else {
                    needUpdate = true;
                    log.info(vs.getOid() + " Codeset has a change! because different version number and date.");
                }
            } else {
                needUpdate = true;
                log.info(vs.getOid() + " Codeset is new one.");
            }
        } else {
            needUpdate = false;
            log.info(vs.getOid() + " Codeset has no change! because PHINVADS does not have it.");
        }

        // 4. if updated, get full codes from PHINVADs web service
        if (needUpdate) {
            if (vscByVSVid == null)
                vscByVSVid = this.getService().getValueSetConceptsByValueSetVersionId(vsv.getId(), 1, Integer.MAX_VALUE);
            if (valueSetConcepts == null)
                valueSetConcepts = vscByVSVid.getValueSetConcepts();
//			if (codeset == null)

            List<ValueSetVersion> vsvByVSOid = this.getService().getValueSetVersionsByValueSetOid(vs.getOid())
                    .getValueSetVersions();

            if (codeset == null) {
                codeset = new Codeset();
                codeset.setVersions(new ArrayList<VersionMetadata>());
            }
            CodesetVersion codesetVersion = new CodesetVersion();

            codesetVersion.setVersion(String.valueOf(vsvByVSOid.get(0).getVersionNumber()));
            codesetVersion.setDateUpdated(vs.getStatusDate());

            codeset.setName(vs.getCode());
            codeset.setDescription(vs.getName());
            codeset.setIdentifier(vs.getOid());
            VersionMetadata versionMetadata = new VersionMetadata(String.valueOf(vsvByVSOid.get(0).getVersionNumber()), vs.getStatusDate());
            codeset.setLatestVersion(versionMetadata);
            codeset.getVersions().add(versionMetadata);
            codeset.setProvider("phinvads");
            codeset.setDateUpdated(vs.getStatusDate());
            codeset.setCodeSetVersions(new HashSet<CodesetVersion>());


            Set<String> codeSystemOids = new HashSet<>();
            Map<String, CodeSystem> uniqueIdCodeSystemMap = new HashMap<>();
            List<Code> codes = new ArrayList<Code>();
            try {
                CodesetVersion savedCodesetVersion = mongoOps.save(codesetVersion);

                for (ValueSetConcept pcode : valueSetConcepts) {
                    if (uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()) == null) {
                        CodeSystemSearchCriteriaDto csSearchCritDto = new CodeSystemSearchCriteriaDto();
                        csSearchCritDto.setCodeSearch(false);
                        csSearchCritDto.setNameSearch(false);
                        csSearchCritDto.setOidSearch(true);
                        csSearchCritDto.setDefinitionSearch(false);
                        csSearchCritDto.setAssigningAuthoritySearch(false);
                        csSearchCritDto.setTable396Search(false);
                        csSearchCritDto.setSearchType(1);
                        csSearchCritDto.setSearchText(pcode.getCodeSystemOid());
                        CodeSystem cs = this.getService().findCodeSystems(csSearchCritDto, 1, 5).getCodeSystems().get(0);
                        uniqueIdCodeSystemMap.put(pcode.getCodeSystemOid(), cs);
                    }
                    Code code = new Code();
                    code.setValue(pcode.getConceptCode());
                    code.setDescription(pcode.getCodeSystemConceptName());
                    code.setComments(pcode.getDefinitionText());
//					code.setUsage("P");
                    code.setCodeSystem(uniqueIdCodeSystemMap.get(pcode.getCodeSystemOid()).getHl70396Identifier());
                    code.setCodesetversionId(savedCodesetVersion.getId());
                    codes.add(code);
                }
                mongoOps.insertAll(codes);
                codeset.getCodeSetVersions().add(savedCodesetVersion);
                mongoOps.save(codeset);
                log.info(vs.getOid() + " Codeset is updated.");

            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }



            return codeset;
        } else {
            log.info(vs.getOid() + " Codeset is NOT updated.");
        }
        return null;
    }

    private PhinvadsValueset fixValueSetDescription(PhinvadsValueset t) {
        String description = t.getDescription();
        if (description == null)
            description = "";
        else {
            description = description.replaceAll("\u0019s", " ");
        }
        String defPostText = t.getPostDef();
        if (defPostText == null)
            defPostText = "";
        else {
            defPostText = defPostText.replaceAll("\u0019s", " ");
            defPostText = defPostText.replaceAll("“", "&quot;");
            defPostText = defPostText.replaceAll("”", "&quot;");
            defPostText = defPostText.replaceAll("\"", "&quot;");
        }
        String defPreText = t.getPreDef();
        if (defPreText == null)
            defPreText = "";
        else {
            defPreText = defPreText.replaceAll("\u0019s", " ");
            defPreText = defPreText.replaceAll("“", "&quot;");
            defPreText = defPreText.replaceAll("”", "&quot;");
            defPreText = defPreText.replaceAll("\"", "&quot;");
        }

        t.setDescription(description);
        t.setPostDef(defPostText);
        t.setPreDef(defPreText);

        return t;
    }

    public VocabService getService() {
        return service;
    }

    public void setService(VocabService service) {
        this.service = service;
    }
}

