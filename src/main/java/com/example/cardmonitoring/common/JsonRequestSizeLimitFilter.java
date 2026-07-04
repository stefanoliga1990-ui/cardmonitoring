package com.example.cardmonitoring.common;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JsonRequestSizeLimitFilter extends OncePerRequestFilter {

	private static final Set<String> METHODS_WITH_BODY = Set.of("POST", "PUT", "PATCH");

	private final int maximumBodyBytes;

	public JsonRequestSizeLimitFilter(
			@Value("${app.http.max-json-request-size:64KB}") DataSize maximumBodySize) {
		long bytes = maximumBodySize.toBytes();
		if (bytes <= 0 || bytes >= Integer.MAX_VALUE) {
			throw new IllegalArgumentException("app.http.max-json-request-size is invalid");
		}
		this.maximumBodyBytes = (int) bytes;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (!request.getRequestURI().startsWith("/api/") || !METHODS_WITH_BODY.contains(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}

		long declaredLength = request.getContentLengthLong();
		if (declaredLength > maximumBodyBytes) {
			writeTooLarge(response);
			return;
		}

		String contentType = request.getContentType();
		if (contentType == null || !contentType.startsWith(MediaType.APPLICATION_JSON_VALUE)) {
			filterChain.doFilter(request, response);
			return;
		}

		byte[] body = request.getInputStream().readNBytes(maximumBodyBytes + 1);
		if (body.length > maximumBodyBytes) {
			writeTooLarge(response);
			return;
		}
		filterChain.doFilter(new CachedBodyRequest(request, body), response);
	}

	private static void writeTooLarge(HttpServletResponse response) throws IOException {
		response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.getWriter().write("{\"status\":413,\"code\":\"REQUEST_TOO_LARGE\","
				+ "\"detail\":\"Request body exceeds the allowed size\"}");
	}

	private static final class CachedBodyRequest extends HttpServletRequestWrapper {

		private final byte[] body;

		private CachedBodyRequest(HttpServletRequest request, byte[] body) {
			super(request);
			this.body = body;
		}

		@Override
		public ServletInputStream getInputStream() {
			return new CachedServletInputStream(body);
		}

		@Override
		public BufferedReader getReader() {
			return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
		}
	}

	private static final class CachedServletInputStream extends ServletInputStream {

		private final ByteArrayInputStream input;

		private CachedServletInputStream(byte[] body) {
			this.input = new ByteArrayInputStream(body);
		}

		@Override
		public int read() {
			return input.read();
		}

		@Override
		public boolean isFinished() {
			return input.available() == 0;
		}

		@Override
		public boolean isReady() {
			return true;
		}

		@Override
		public void setReadListener(ReadListener readListener) {
			throw new UnsupportedOperationException("Asynchronous reads are not supported");
		}
	}
}
