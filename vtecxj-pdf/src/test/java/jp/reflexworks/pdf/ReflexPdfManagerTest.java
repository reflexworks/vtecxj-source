package jp.reflexworks.pdf;

import org.junit.Test;

import jp.reflexworks.pdf.exception.IllegalPdfParameterException;
import jp.reflexworks.taggingservice.exception.TaggingException;

public class ReflexPdfManagerTest {

	@Test(expected = IllegalPdfParameterException.class)
	public void testToPdfRejectDoctype() throws TaggingException, java.io.IOException {
		ReflexPdfManager manager = new ReflexPdfManager();
		String html =
				"<!DOCTYPE html [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>" +
				"<html><body><div class=\"_page\" style=\"pagesize:595,842\">&xxe;</div></body></html>";
		manager.toPdf(html, null);
	}

}
