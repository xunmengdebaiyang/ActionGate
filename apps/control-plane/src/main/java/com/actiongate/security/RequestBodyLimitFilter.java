package com.actiongate.security;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
final class RequestBodyLimitFilter extends OncePerRequestFilter {
    private final int maxBytes;

    RequestBodyLimitFilter(@Value("${actiongate.http.max-body-bytes:4096}") int maxBytes) {
        if (maxBytes < 256 || maxBytes > 10_485_760) {
            throw new IllegalArgumentException("Request body limit must be between 256 and 10485760 bytes");
        }
        this.maxBytes = maxBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maxBytes) {
            response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "Request body exceeds the configured limit");
            return;
        }
        try {
            chain.doFilter(new LimitedRequest(request, maxBytes), response);
        } catch (RequestBodyTooLargeException exception) {
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                        "Request body exceeds the configured limit");
                return;
            }
            throw exception;
        }
    }

    private static final class LimitedRequest extends HttpServletRequestWrapper {
        private final int maxBytes;

        private LimitedRequest(HttpServletRequest request, int maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            ServletInputStream delegate = super.getInputStream();
            return new ServletInputStream() {
                private int count;

                @Override public int read() throws IOException {
                    int value = delegate.read();
                    return value == -1 ? -1 : checked(value, 1);
                }
                @Override public int read(byte[] b, int off, int len) throws IOException {
                    int read = delegate.read(b, off, len);
                    return checked(read, read);
                }
                private int checked(int value, int read) {
                    if (read > 0) {
                        count += read;
                        if (count > maxBytes) throw new RequestBodyTooLargeException();
                    }
                    return value;
                }
                @Override public boolean isFinished() { return delegate.isFinished(); }
                @Override public boolean isReady() { return delegate.isReady(); }
                @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            Charset charset = getCharacterEncoding() == null
                    ? java.nio.charset.StandardCharsets.UTF_8
                    : Charset.forName(getCharacterEncoding());
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }
}
