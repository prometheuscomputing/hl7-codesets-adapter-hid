package gov.nist.hit.hl7.codeset.adapter.controller;

import gov.nist.hit.hl7.codeset.adapter.exception.NotFoundException;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.CodesetVersionMetadataResponse;
import gov.nist.hit.hl7.codeset.adapter.model.response.ProvidersResponse;
import gov.nist.hit.hl7.codeset.adapter.serviceImpl.CodesetServiceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/")
public class HomeController {

    public HomeController() {
    }
    @GetMapping("/")
    public @ResponseBody Object returnEmptyJson() {
        return Collections.emptyMap();
    }


}
