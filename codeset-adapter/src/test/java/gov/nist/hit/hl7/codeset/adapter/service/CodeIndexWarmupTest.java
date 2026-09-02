package gov.nist.hit.hl7.codeset.adapter.service;

import gov.nist.hit.hl7.codeset.adapter.controller.CodesetController;
import gov.nist.hit.hl7.codeset.adapter.model.request.CodesetSearchCriteria;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CodeIndexWarmupTest {

    @Test
    public void parsesOidsWithOptionalVersions() {
        List<CodeIndexWarmup.Target> targets = CodeIndexWarmup.parse(" 2.16.840.1.114222.4.11.7356, 2.16.840.1.114222.4.11.829:6 ,2.16.840.1.114222.4.11.909,, ");
        assertEquals(3, targets.size());
        assertEquals("2.16.840.1.114222.4.11.7356", targets.get(0).oid());
        assertNull(targets.get(0).version());
        assertEquals("2.16.840.1.114222.4.11.829", targets.get(1).oid());
        assertEquals("6", targets.get(1).version());
        assertNull(targets.get(2).version());
    }

    @Test
    public void emptySpecMeansNothingToWarm() {
        assertTrue(CodeIndexWarmup.parse("").isEmpty());
        assertTrue(CodeIndexWarmup.parse(null).isEmpty());
    }

    @Test
    public void warmsTheWholeSetResponseAndTheIndexForEachTarget() throws Exception {
        CodesetController controller = mock(CodesetController.class);
        CodesetService service = mock(CodesetService.class);
        CodeIndexWarmup warmup = new CodeIndexWarmup(controller, service, "a:6,b");

        warmup.warmAll();

        ArgumentCaptor<CodesetSearchCriteria> viaController = ArgumentCaptor.forClass(CodesetSearchCriteria.class);
        verify(controller, times(2)).getCodeset(eq("phinvads"), any(), viaController.capture());
        assertEquals("6", viaController.getAllValues().get(0).getVersion());
        assertNull(viaController.getAllValues().get(0).getMatch());
        assertNull(viaController.getAllValues().get(1).getVersion());

        ArgumentCaptor<CodesetSearchCriteria> viaService = ArgumentCaptor.forClass(CodesetSearchCriteria.class);
        verify(service, times(2)).getCodeset(eq("phinvads"), any(), viaService.capture());
        assertEquals(CodeIndexWarmup.PROBE_CODE, viaService.getAllValues().get(0).getMatch());
        assertEquals("6", viaService.getAllValues().get(0).getVersion());
    }

    @Test
    public void oneFailingTargetDoesNotStopTheOthers() throws Exception {
        CodesetController controller = mock(CodesetController.class);
        CodesetService service = mock(CodesetService.class);
        when(controller.getCodeset(eq("phinvads"), eq("bad"), any())).thenThrow(new RuntimeException("cdc down"));
        CodeIndexWarmup warmup = new CodeIndexWarmup(controller, service, "bad,good");

        warmup.warmAll();

        verify(service, times(1)).getCodeset(eq("phinvads"), eq("good"), any());
    }
}
