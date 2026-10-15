package com.storefront.common.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * Servlet filter that populates SLF4J MDC with correlation identifiers
 * (traceId, spanId, X-Request-ID) to enable distributed tracing and structured JSON logging.
 */
public class CorrelationIdFilter implements Filter {

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_REQUEST_ID = "requestId";

    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_TRACE_ID = "X-Trace-Id";
    public static final String HEADER_SPAN_ID = "X-Span-Id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String requestId = httpRequest.getHeader(HEADER_REQUEST_ID);
            if (requestId == null || requestId.isBlank()) {
                requestId = UUID.randomUUID().toString();
            }

            String traceId = httpRequest.getHeader(HEADER_TRACE_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = requestId;
            }

            String spanId = httpRequest.getHeader(HEADER_SPAN_ID);
            if (spanId == null || spanId.isBlank()) {
                spanId = UUID.randomUUID().toString().substring(0, 8);
            }

            MDC.put(MDC_REQUEST_ID, requestId);
            MDC.put(MDC_TRACE_ID, traceId);
            MDC.put(MDC_SPAN_ID, spanId);

            httpResponse.setHeader(HEADER_REQUEST_ID, requestId);
            httpResponse.setHeader(HEADER_TRACE_ID, traceId);

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(MDC_REQUEST_ID);
                MDC.remove(MDC_TRACE_ID);
                MDC.remove(MDC_SPAN_ID);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
