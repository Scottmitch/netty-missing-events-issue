package io.gatling.issues;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

public class MissingEventsTest {

    int port = 55486;

    private void doTest(byte[] body) throws Exception {
        try (NettyClient client = new NettyClient()) {
            boolean successCount = client.sendRequest(port, body);
            Assert.assertTrue("Should have been able to send request and read response", successCount);
        }
    }

    @Test
    public void testUnauthenticatedGet() throws Exception {
        try (JettyServer server = new JettyServer(port)) {
            doTest(null);
        }
    }

    @Test
    public void testUnauthenticatedChunkedPost() throws Exception {
        try (JettyServer server = new JettyServer(port)) {
            doTest("test".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    public void testUnauthenticatedGetThenChunkedPostWtf() throws Exception {

        try (JettyServer server = new JettyServer(port)) {
            doTest(null);
            doTest("test".getBytes(StandardCharsets.UTF_8));
        }
    }
}
