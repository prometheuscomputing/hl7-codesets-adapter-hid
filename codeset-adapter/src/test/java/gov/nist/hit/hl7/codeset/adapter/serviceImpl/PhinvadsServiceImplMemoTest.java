package gov.nist.hit.hl7.codeset.adapter.serviceImpl;

import gov.cdc.vocab.service.VocabService;
import gov.cdc.vocab.service.bean.CodeSystem;
import gov.cdc.vocab.service.bean.ValueSet;
import gov.cdc.vocab.service.bean.ValueSetVersion;
import gov.cdc.vocab.service.dto.output.CodeSystemResultDto;
import gov.cdc.vocab.service.dto.output.ValueSetResultDto;
import gov.cdc.vocab.service.dto.output.ValueSetVersionResultDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The PHIN VADS metadata behind a code set does not change between two
 * validations, so the adapter must not ask CDC for it on every lookup.
 */
public class PhinvadsServiceImplMemoTest {

    private static final String OID = "2.16.840.1.114222.4.11.909";

    private static ValueSetResultDto valueSetResult(ValueSet vs) {
        ValueSetResultDto dto = new ValueSetResultDto();
        dto.setValueSets(vs == null ? List.of() : List.of(vs));
        return dto;
    }

    private static ValueSetVersionResultDto versionResult(ValueSetVersion... versions) {
        ValueSetVersionResultDto dto = new ValueSetVersionResultDto();
        dto.setValueSetVersions(List.of(versions));
        return dto;
    }

    private static ValueSetVersion version(String oid, int number) {
        ValueSetVersion v = new ValueSetVersion();
        v.setId("vsv-" + oid + "-" + number);
        v.setValueSetOid(oid);
        v.setVersionNumber(number);
        return v;
    }

    private static PhinvadsServiceImpl service(VocabService cdc, long ttlHours) throws Exception {
        return new PhinvadsServiceImpl(null, null, cdc, ttlHours);
    }

    @Test
    public void valueSetIsFetchedFromPhinvadsOnce() throws Exception {
        VocabService cdc = mock(VocabService.class);
        ValueSet vs = new ValueSet();
        vs.setOid(OID);
        when(cdc.findValueSets(any(), anyInt(), anyInt())).thenReturn(valueSetResult(vs));
        PhinvadsServiceImpl svc = service(cdc, 24);

        assertSame(vs, svc.getValueset(OID));
        assertSame(vs, svc.getValueset(OID));
        verify(cdc, times(1)).findValueSets(any(), anyInt(), anyInt());
    }

    @Test
    public void unknownValueSetIsAskedAgainNextTime() throws Exception {
        VocabService cdc = mock(VocabService.class);
        when(cdc.findValueSets(any(), anyInt(), anyInt())).thenReturn(valueSetResult(null));
        PhinvadsServiceImpl svc = service(cdc, 24);

        assertNull(svc.getValueset(OID));
        assertNull(svc.getValueset(OID));
        verify(cdc, times(2)).findValueSets(any(), anyInt(), anyInt());
    }

    @Test
    public void phinvadsFailureIsNotRemembered() throws Exception {
        VocabService cdc = mock(VocabService.class);
        ValueSet vs = new ValueSet();
        vs.setOid(OID);
        when(cdc.findValueSets(any(), anyInt(), anyInt()))
                .thenThrow(new RuntimeException("cdc down"))
                .thenReturn(valueSetResult(vs));
        PhinvadsServiceImpl svc = service(cdc, 24);

        assertNull(svc.getValueset(OID));
        assertSame(vs, svc.getValueset(OID));
        verify(cdc, times(2)).findValueSets(any(), anyInt(), anyInt());
    }

    @Test
    public void versionListIsFetchedOncePerValueSet() throws Exception {
        VocabService cdc = mock(VocabService.class);
        ValueSetVersion v5 = version(OID, 5);
        ValueSetVersion v6 = version(OID, 6);
        ValueSetVersion other = version("2.16.840.1.114222.4.11.829", 6);
        when(cdc.findValueSetVersions(any(), anyInt(), anyInt())).thenReturn(versionResult(v5, v6, other));
        PhinvadsServiceImpl svc = service(cdc, 24);

        assertSame(v6, svc.getValuesetVersion(OID, "6"));
        assertSame(v6, svc.getValuesetVersion(OID, "6"));
        assertSame(v5, svc.getValuesetVersion(OID, "5"));
        assertEquals(List.of(v5, v6), svc.getValuesetVersions(OID));
        verify(cdc, times(1)).findValueSetVersions(any(), anyInt(), anyInt());

        // An unknown version is checked against PHIN VADS once more (it may
        // have been published since), then the refreshed list is kept.
        assertNull(svc.getValuesetVersion(OID, "9"));
        assertEquals(List.of(v5, v6), svc.getValuesetVersions(OID));
        verify(cdc, times(2)).findValueSetVersions(any(), anyInt(), anyInt());
    }

    @Test
    public void aVersionMissingFromTheRememberedListIsLookedUpAgainOnce() throws Exception {
        VocabService cdc = mock(VocabService.class);
        ValueSetVersion v6 = version(OID, 6);
        ValueSetVersion v7 = version(OID, 7);
        when(cdc.findValueSetVersions(any(), anyInt(), anyInt()))
                .thenReturn(versionResult(v6))
                .thenReturn(versionResult(v6, v7));
        PhinvadsServiceImpl svc = service(cdc, 24);

        assertSame(v6, svc.getValuesetVersion(OID, "6"));
        assertSame(v7, svc.getValuesetVersion(OID, "7"), "a version published after the list was remembered is found");
        assertSame(v7, svc.getValuesetVersion(OID, "7"));
        verify(cdc, times(2)).findValueSetVersions(any(), anyInt(), anyInt());
    }

    @Test
    public void latestVersionIsFetchedOnce() throws Exception {
        VocabService cdc = mock(VocabService.class);
        when(cdc.findValueSetVersions(any(), anyInt(), anyInt())).thenReturn(versionResult(version(OID, 7)));
        PhinvadsServiceImpl svc = service(cdc, 24);

        assertEquals("7", svc.getLatestVersion(OID));
        assertEquals("7", svc.getLatestVersion(OID));
        verify(cdc, times(1)).findValueSetVersions(any(), anyInt(), anyInt());
    }

    @Test
    public void codeSystemIsFetchedOnce() throws Exception {
        VocabService cdc = mock(VocabService.class);
        CodeSystem cs = new CodeSystem();
        cs.setOid("2.16.840.1.113883.6.96");
        CodeSystemResultDto dto = new CodeSystemResultDto();
        dto.setCodeSystems(List.of(cs));
        when(cdc.findCodeSystems(any(), anyInt(), anyInt())).thenReturn(dto);
        PhinvadsServiceImpl svc = service(cdc, 24);

        assertSame(cs, svc.getCodeSystem("2.16.840.1.113883.6.96"));
        assertSame(cs, svc.getCodeSystem("2.16.840.1.113883.6.96"));
        verify(cdc, times(1)).findCodeSystems(any(), anyInt(), anyInt());
    }

    @Test
    public void memoExpiresWithTheTtl() throws Exception {
        VocabService cdc = mock(VocabService.class);
        ValueSet vs = new ValueSet();
        vs.setOid(OID);
        when(cdc.findValueSets(any(), anyInt(), anyInt())).thenReturn(valueSetResult(vs));
        PhinvadsServiceImpl svc = service(cdc, 0);

        svc.getValueset(OID);
        svc.getValueset(OID);
        verify(cdc, times(2)).findValueSets(any(), anyInt(), anyInt());
    }
}
