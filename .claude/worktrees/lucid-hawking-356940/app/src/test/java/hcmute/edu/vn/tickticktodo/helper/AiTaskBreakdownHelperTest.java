package hcmute.edu.vn.doinbot.helper;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AiTaskBreakdownHelperTest {

    @Mock
    private List<String> mockedSteps;

    @Test
    public void buildPrompt_khiTaskTitleHopLe_traVePromptDungDinhDang() {
        String prompt = AiTaskBreakdownHelper.buildPrompt("Hoan thanh bao cao");

        assertNotNull(prompt);
        assertTrue(prompt.contains("Hoan thanh bao cao"));
        assertTrue(prompt.contains("3-5 bước"));
        assertTrue(prompt.contains("JSON array"));
    }

    @Test
    public void parseSteps_khiJsonArrayHopLe_traVeDanhSachStep() throws Exception {
        List<String> steps = AiTaskBreakdownHelper.parseSteps("[\"Buoc 1\",\"Buoc 2\"]");

        assertNotNull(steps);
        assertEquals(2, steps.size());
        assertEquals("Buoc 1", steps.get(0));
        assertEquals("Buoc 2", steps.get(1));
    }

    @Test
    public void parseSteps_khiNamTrongMarkdownFence_traVeDanhSachStep() throws Exception {
        String raw = "```JSON\n[\"A\",\"B\",\"C\"]\n```";

        List<String> steps = AiTaskBreakdownHelper.parseSteps(raw);

        assertEquals(3, steps.size());
        assertEquals("A", steps.get(0));
        assertEquals("B", steps.get(1));
        assertEquals("C", steps.get(2));
    }

    @Test
    public void parseSteps_khiLlmCoTextThuaNgoaiArray_traVeDanhSachStep() throws Exception {
        String raw = "Goi y cua AI:\n[\"Task A\",\"Task B\"]\nCam on ban.";

        List<String> steps = AiTaskBreakdownHelper.parseSteps(raw);

        assertEquals(2, steps.size());
        assertEquals("Task A", steps.get(0));
        assertEquals("Task B", steps.get(1));
    }

    @Test
    public void parseSteps_khiCoHonNamStep_chiLayToiDaNamStep() throws Exception {
        String raw = "[\"1\",\"2\",\"3\",\"4\",\"5\",\"6\"]";

        List<String> steps = AiTaskBreakdownHelper.parseSteps(raw);

        assertEquals(5, steps.size());
        assertEquals(Arrays.asList("1", "2", "3", "4", "5"), steps);
    }

    @Test
    public void parseSteps_khiRawResponseNull_nemJSONException() {
        assertThrows(JSONException.class, () -> AiTaskBreakdownHelper.parseSteps(null));
    }

    @Test
    public void parseSteps_khiRawResponseRong_nemJSONException() {
        assertThrows(JSONException.class, () -> AiTaskBreakdownHelper.parseSteps("   \n  "));
    }

    @Test
    public void parseSteps_khiKhongCoStepHopLe_nemJSONException() {
        assertThrows(JSONException.class, () -> AiTaskBreakdownHelper.parseSteps("[\"\",\"   \"]"));
    }

    @Test
    public void parseSteps_khiJsonKhongPhaiArray_nemJSONException() {
        assertThrows(JSONException.class, () -> AiTaskBreakdownHelper.parseSteps("{\"step\":\"A\"}"));
    }

    @Test
    public void mergeChecklistIntoDescription_khiDescriptionRong_traVeChecklistMoi() {
        String result = AiTaskBreakdownHelper.mergeChecklistIntoDescription("  ", Arrays.asList("A", "B"));

        assertEquals("AI Breakdown:\n[ ] A\n[ ] B", result);
    }

    @Test
    public void mergeChecklistIntoDescription_khiCoDescription_traVeDescriptionKemChecklist() {
        String result = AiTaskBreakdownHelper.mergeChecklistIntoDescription("  Mo ta cu  ", Arrays.asList("Buoc 1"));

        assertTrue(result.startsWith("Mo ta cu\n\nAI Breakdown:"));
        assertTrue(result.contains("[ ] Buoc 1"));
    }

    @Test
    public void mergeChecklistIntoDescription_khiDungMockedList_traVeNoiDungTuMockito() {
        when(mockedSteps.iterator()).thenReturn(Arrays.asList("Mock 1", "Mock 2").iterator());

        String result = AiTaskBreakdownHelper.mergeChecklistIntoDescription("Mo ta", mockedSteps);

        assertNotNull(result);
        assertTrue(result.contains("[ ] Mock 1"));
        assertTrue(result.contains("[ ] Mock 2"));
        verify(mockedSteps).iterator();
    }

    @Test
    public void mergeChecklistIntoDescription_khiStepsNull_nemNullPointerException() {
        assertThrows(NullPointerException.class,
                () -> AiTaskBreakdownHelper.mergeChecklistIntoDescription("Mo ta", null));
    }
}
