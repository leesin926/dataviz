package com.dataviz.openapi.support;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * HMAC 必须对原始请求体计算，而输入流只能读一次，所以先把字节留在内存里再交回下游。
 * 超过 maxBodyBytes 直接抛 {@link BodyTooLargeException}，让调用方回 413，不把大请求吸进堆。
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    public CachedBodyHttpServletRequest(HttpServletRequest request, int maxBodyBytes) throws IOException {
        super(request);
        this.body = readFully(request.getInputStream(), maxBodyBytes);
    }

    public byte[] getCachedBody() {
        return body;
    }

    private static byte[] readFully(InputStream in, int maxBodyBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        byte[] buffer = new byte[4096];
        int total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > maxBodyBytes) {
                throw new BodyTooLargeException(maxBodyBytes);
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Override
    public ServletInputStream getInputStream() {
        final ByteArrayInputStream source = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
                throw new UnsupportedOperationException("Signed callbacks are read synchronously");
            }

            @Override
            public int read() {
                return source.read();
            }

            @Override
            public int read(byte[] b, int off, int len) {
                return source.read(b, off, len);
            }

            @Override
            public void close() throws IOException {
                source.close();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        String encoding = getCharacterEncoding();
        Charset charset = encoding == null ? Charset.forName("UTF-8") : Charset.forName(encoding);
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
    }

    /** 请求体超过签名允许的上限。 */
    public static class BodyTooLargeException extends RuntimeException {
        public BodyTooLargeException(int maxBodyBytes) {
            super("Request body exceeds " + maxBodyBytes + " bytes");
        }
    }
}
