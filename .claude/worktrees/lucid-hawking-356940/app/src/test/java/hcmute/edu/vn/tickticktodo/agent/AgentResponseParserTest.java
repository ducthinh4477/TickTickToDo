package hcmute.edu.vn.doinbot.agent;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AgentResponseParserTest {

    private AgentResponseParser parser;

    @Before
    public void setUp_khoiTaoParser_sanSangTest() {
        parser = new AgentResponseParser();
    }

    @Test
    public void parse_khiRawResponseNull_traVePlainTextRong() {
        AgentResponseEnvelope envelope = parser.parse(null);

        assertNotNull(envelope);
        assertEquals(AgentAction.CHAT, envelope.getAction());
        assertEquals("", envelope.getRawText());
        assertEquals("", envelope.getReply());
        assertTrue(!envelope.isStructured());
        assertNull(envelope.getPayload());
    }

    @Test
    public void parse_khiRawResponseChiKhoangTrang_traVePlainTextRong() {
        AgentResponseEnvelope envelope = parser.parse("   \n\t   ");

        assertNotNull(envelope);
        assertEquals(AgentAction.CHAT, envelope.getAction());
        assertEquals("", envelope.getRawText());
        assertTrue(!envelope.isStructured());
    }

    @Test
    public void parse_khiJsonDayDu_traVeEnvelopeStructured() throws Exception {
        String raw = "{\"action\":\"create_task\",\"payload\":{\"title\":\"Mua sua\"},\"reply\":\"Da tao\"}";

        AgentResponseEnvelope envelope = parser.parse(raw);

        assertNotNull(envelope);
        assertTrue(envelope.isStructured());
        assertEquals(AgentAction.CREATE_TASK, envelope.getAction());
        assertEquals("Da tao", envelope.getReply());
        assertEquals(raw, envelope.getRawText());

        JSONObject payload = envelope.getPayload();
        assertNotNull(payload);
        assertEquals("Mua sua", payload.getString("title"));
    }

    @Test
    public void parse_khiJsonKhongCoActionNhungCoTitle_traVeCreateTask() throws Exception {
        String raw = "{\"title\":\"Hoc Unit Test\",\"reply\":\"ok\"}";

        AgentResponseEnvelope envelope = parser.parse(raw);

        assertNotNull(envelope);
        assertTrue(envelope.isStructured());
        assertEquals(AgentAction.CREATE_TASK, envelope.getAction());
        assertEquals("ok", envelope.getReply());
        assertNotNull(envelope.getPayload());
        assertEquals("Hoc Unit Test", envelope.getPayload().getString("title"));
    }

    @Test
    public void parse_khiJsonKhongCoPayloadVaReply_traVeGiaTriMacDinhAnToan() {
        String raw = "{\"action\":\"chat\"}";

        AgentResponseEnvelope envelope = parser.parse(raw);

        assertNotNull(envelope);
        assertTrue(envelope.isStructured());
        assertEquals(AgentAction.CHAT, envelope.getAction());
        assertNull(envelope.getPayload());
        assertEquals("", envelope.getReply());
    }

    @Test
    public void parse_khiJsonNamTrongMarkdownFence_traVeStructured() throws Exception {
        String innerJson = "{\"action\":\"list_today\",\"payload\":{\"limit\":5},\"reply\":\"done\"}";
        String raw = "```json\n" + innerJson + "\n```";

        AgentResponseEnvelope envelope = parser.parse(raw);

        assertNotNull(envelope);
        assertTrue(envelope.isStructured());
        assertEquals(AgentAction.LIST_TODAY, envelope.getAction());
        assertEquals("done", envelope.getReply());
        assertEquals(innerJson, envelope.getRawText());
        assertEquals(5, envelope.getPayload().getInt("limit"));
    }

    @Test
    public void parse_khiMarkdownFenceKhongHopLe_traVePlainText() {
        String raw = "```{\\\"action\\\":\\\"chat\\\"}```";

        AgentResponseEnvelope envelope = parser.parse(raw);

        assertNotNull(envelope);
        assertTrue(!envelope.isStructured());
        assertEquals(AgentAction.CHAT, envelope.getAction());
        assertEquals(raw, envelope.getRawText());
    }

    @Test
    public void parse_khiLlmTraVeTextThuaNgoaiJson_traVePlainTextAnToan() {
        String raw = "Ket qua phan tich:\n{\"action\":\"CHAT\"}";

        AgentResponseEnvelope envelope = parser.parse(raw);

        assertNotNull(envelope);
        assertTrue(!envelope.isStructured());
        assertEquals(AgentAction.CHAT, envelope.getAction());
        assertEquals(raw, envelope.getRawText());
    }
}
