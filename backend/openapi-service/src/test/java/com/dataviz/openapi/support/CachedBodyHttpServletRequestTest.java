package com.dataviz.openapi.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验签前必须把请求体留在内存里（输入流只能读一次），否则下游读不到、签名也覆盖不到同一份字节。
 */
class CachedBodyHttpServletRequestTest {

    @Test
    @DisplayName("请求体原样缓存，下游仍可完整读出")
    void should_preserveBytes_when_wrapAndReread() throws IOException {
        byte[] payload = "{\"event\":\"order.created\"}".getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(payload);

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, 1024);

        assertArrayEquals(payload, wrapped.getCachedBody());
        assertArrayEquals(payload, readAll(wrapped));
    }

    @Test
    @DisplayName("流读到尽头后 isFinished 为真")
    void should_reportFinished_when_streamDrained() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("abc".getBytes(StandardCharsets.UTF_8));

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, 1024);
        javax.servlet.ServletInputStream in = wrapped.getInputStream();
        assertEquals('a', in.read());
        assertEquals('b', in.read());
        assertEquals('c', in.read());
        assertEquals(-1, in.read());
        assertTrue(in.isFinished());
        assertTrue(in.isReady());
    }

    @Test
    @DisplayName("超出签名上限：拒绝接收，不把大请求吸进堆")
    void should_reject_when_bodyExceedsLimit() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(new byte[64]);

        CachedBodyHttpServletRequest.BodyTooLargeException e = assertThrows(
                CachedBodyHttpServletRequest.BodyTooLargeException.class,
                () -> new CachedBodyHttpServletRequest(request, 16));
        assertTrue(e.getMessage().contains("16"));
    }

    @Test
    @DisplayName("空请求体也是合法输入：哈希空字节段，不抛异常")
    void should_acceptEmptyBody_when_contentAbsent() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, 1024);

        assertEquals(0, wrapped.getCachedBody().length);
    }

    @Test
    @DisplayName("按声明的字符集解码，中文正文不乱码")
    void should_honourDeclaredEncoding_when_readReader() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCharacterEncoding("UTF-8");
        request.setContent("回调正文".getBytes(StandardCharsets.UTF_8));

        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(request, 1024);
        BufferedReader reader = wrapped.getReader();
        char[] buf = new char[8];
        int read = reader.read(buf);

        assertEquals(4, read);
        assertEquals("回调正文", new String(buf, 0, read));
    }

    @Test
    @DisplayName("异步读监听器不支持：签名回调是同步读")
    void should_failFast_when_setReadListener() throws IOException {
        CachedBodyHttpServletRequest wrapped = new CachedBodyHttpServletRequest(
                new MockHttpServletRequest(), 1024);

        assertThrows(UnsupportedOperationException.class,
                () -> wrapped.getInputStream().setReadListener(null));
    }

    private static byte[] readAll(CachedBodyHttpServletRequest wrapped) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16];
        int read;
        javax.servlet.ServletInputStream in = wrapped.getInputStream();
        while ((read = in.read(buffer, 0, buffer.length)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
