package jp.reflexworks.batch.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Locale;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

/**
 * テスト用レスポンスクラス
 */
public class BatchJobHttpResponse implements HttpServletResponse {

	@Override
	public String getCharacterEncoding() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public String getContentType() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void setCharacterEncoding(String charset) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setContentLength(int len) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setContentType(String type) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setBufferSize(int size) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public int getBufferSize() {
		// 自動生成されたメソッド・スタブ
		return 0;
	}

	@Override
	public void flushBuffer() throws IOException {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void resetBuffer() {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public boolean isCommitted() {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	@Override
	public void reset() {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setLocale(Locale loc) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public Locale getLocale() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void addCookie(Cookie cookie) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public boolean containsHeader(String name) {
		// 自動生成されたメソッド・スタブ
		return false;
	}

	@Override
	public String encodeURL(String url) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public String encodeRedirectURL(String url) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public String encodeUrl(String url) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public String encodeRedirectUrl(String url) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void sendError(int sc, String msg) throws IOException {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void sendError(int sc) throws IOException {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void sendRedirect(String location) throws IOException {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setDateHeader(String name, long date) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void addDateHeader(String name, long date) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setHeader(String name, String value) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void addHeader(String name, String value) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setIntHeader(String name, int value) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void addIntHeader(String name, int value) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setStatus(int sc) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public void setStatus(int sc, String sm) {
		// 自動生成されたメソッド・スタブ

	}

	@Override
	public int getStatus() {
		// 自動生成されたメソッド・スタブ
		return 0;
	}

	@Override
	public String getHeader(String name) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public Collection<String> getHeaders(String name) {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public Collection<String> getHeaderNames() {
		// 自動生成されたメソッド・スタブ
		return null;
	}

	@Override
	public void setContentLengthLong(long len) {
		// 自動生成されたメソッド・スタブ

	}

}
