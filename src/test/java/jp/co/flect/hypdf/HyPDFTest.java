package jp.co.flect.hypdf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import jp.co.flect.hypdf.model.HyPDFOption.Footer;
import jp.co.flect.hypdf.model.HyPDFOption.HtmlToPdf;
import jp.co.flect.hypdf.model.JobStatus;
import jp.co.flect.hypdf.model.PdfResponse;
import jp.co.flect.hypdf.transport.TransportFactory;

import org.junit.BeforeClass;
import org.junit.Test;

public class HyPDFTest {

	public static String USERNAME;
	public static String PASSWORD;
	public static File OUTPUT_DIR = new File("target/testoutput");
	public static File INPUT_DIR  = new File("testinput");

	@BeforeClass
	public static void setup() throws IOException{
	    Properties props = new Properties();

	    InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream("test.properties");
	    try {
		props.load(is);
	    } finally {
		is.close();
	    }
	    USERNAME  = props.getProperty("HYPDF_USER");
	    PASSWORD  = props.getProperty("HYPDF_PASSWORD");

	    assertNotNull(USERNAME);
	    assertNotNull(PASSWORD);
	    OUTPUT_DIR.mkdirs();

	    //		System.setProperty ("jsse.enableSNIExtension", "false");
	    TransportFactory.setDefaultFactory(new TransportFactory.HttpClientTransportFactory());
	    TransportFactory.setAlwaysIgnoreHostNameVerification(true);
	}
	
	private void log(String msg) {
		System.out.println("=== " + msg);
	}

	private HyPDF createHyPDF(String username, String password) {
		HyPDF hypdf = new HyPDF(username, password);
		hypdf.setTestMode(true);
		return hypdf;
	}

	@Test
	public void loginError() throws IOException {
		try {
			HyPDF hypdf = createHyPDF("", "");
			hypdf.htmlToPdf("dummy");
		} catch (HyPDFException e) {
			log("loginError response: " + e.getStatusCode() + ", " + e.getMessage());
			assertEquals(401, e.getStatusCode());
			assertNotNull(e.getMessage());
		}
	}

	@Test
	public void simpleHtml() throws IOException {
	    HyPDF hypdf = createHyPDF(USERNAME, PASSWORD);
	    String content = "http://flect-panda.herokuapp.com/public/fontface.html";
	    File file = new File(OUTPUT_DIR, "simpleHtml.pdf");
	    HtmlToPdf option = new HtmlToPdf();
	    Footer footer = new Footer();
	    footer.center = "FLECT";
	    option.footer = footer;

	    PdfResponse res = hypdf.htmlToPdf(content, option);
	    res.writeTo(file);

	    System.out.println("pdfInfo: " + res.getPages() + ", " + res.getPageSize() + ", " + res.getPdfVersion());
	    assertEquals(1, res.getPages());
	    assertTrue(res.getPageSize().indexOf("letter") >= 0);
	    assertEquals("1.4", res.getPdfVersion());
	}

	@Test
	public void callback() throws IOException {
	    HyPDF hypdf = createHyPDF(USERNAME, PASSWORD);
	    //		String content = "https://excel-report2.herokuapp.com/report/html/sample/chart?score=50&score=40&score=60&score=70&score=80&score=82&score=73&score=94&score=100&score=62&score=72&score=43&score=22&score=84&score=63&app.print=false&app.download=false&app.webfont=true&app.border=false";
	    String content = "http://flect-panda.herokuapp.com/public/fontface.html";
	    URL url = new URL("http://flect-panda.herokuapp.com/");
	    String jobId = hypdf.notifyHtmlToPdf(content, url);
	    System.out.println("job_id: " + jobId);
	    assertNotNull(jobId);
	    JobStatus status = hypdf.jobstatus(jobId);
	    System.out.println("status: " + status);
	    assertNotNull(status);
	}

	@Test
	public void pdfInfo() throws IOException {
	    log("pdfInfo");
	    HyPDF hypdf = createHyPDF(USERNAME, PASSWORD);
	    File file = new File(INPUT_DIR, "simpleHtml.pdf");
	    Map<String, Object> ret = hypdf.pdfInfo(file);
	    System.out.println(ret);
	}

	@Test
	public void pdfToTest() throws IOException {
	    HyPDF hypdf = createHyPDF(USERNAME, PASSWORD);
	    File file = new File(INPUT_DIR, "simpleHtml.pdf");
	    Map<String, Object> ret = hypdf.pdfToText(file);
	    System.out.println(ret);
	}

	@Test
	public void pdfUnite() throws IOException {
	    HyPDF hypdf = createHyPDF(USERNAME, PASSWORD);
	    File input = new File(INPUT_DIR, "simpleHtml.pdf");
	    File output = new File(OUTPUT_DIR, "pdfUnite.pdf");
	    PdfResponse res = hypdf.pdfUnite(input, input, input);
	    res.writeTo(output);
	    //assertEquals(3, res.getPages());
	}

	@Test
	public void pdfExtract() throws IOException {
	    HyPDF hypdf = createHyPDF(USERNAME, PASSWORD);
	    File input = new File(INPUT_DIR, "pdfUnite.pdf");
	    File output = new File(OUTPUT_DIR, "pdfExtract.pdf");
	    PdfResponse res = hypdf.pdfExtract(input, 2, 2);
	    res.writeTo(output);
	    //assertEquals(3, res.getPages());
	}
}