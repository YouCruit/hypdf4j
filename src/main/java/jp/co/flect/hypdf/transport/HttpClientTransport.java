package jp.co.flect.hypdf.transport;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;
import java.nio.charset.Charset;
import jp.co.flect.hypdf.json.JsonUtils;
import jp.co.flect.hypdf.HyPDFException;
import jp.co.flect.hypdf.model.PdfResponse;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.util.EntityUtils;
import org.apache.http.Header;

import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;

public class HttpClientTransport extends AbstractTransport {
	
	private HttpClient client = null;
	private boolean ignoreSSL = false;
	
	/**
	 * HyPDF uses certificate from StartCom.
	 * Default keystore of JDK doesn't include this CA certificate.
	 * So, HttpClient failed connection.(AsyncHttpClient doesn't failed.)
	 * If you want to use HttpClient, you should install StartCom certificate to keystore by yourself.
	 * Or set this property true.
	 */
	public boolean isIgnoreHostNameVerification() { return this.ignoreSSL;}
	public void setIgnoreHostNameVerification(boolean b) { this.ignoreSSL = b;}

	private String getHeaderValue(HttpResponse res, String name) {
		Header h = res.getFirstHeader(name);
		return h == null ? null : h.getValue();
	}

	private void checkResponse(HttpResponse res) throws IOException {
		int status = res.getStatusLine().getStatusCode();
		if (status != 200) {
			String body = EntityUtils.toString(res.getEntity(), "utf-8");
			String msg = null;
			try {
				Map<String, Object> map = JsonUtils.parse(body);
				msg = (String)map.get("message");
			} catch (Exception e) {
				e.printStackTrace();
			}
			throw new HyPDFException(status, msg == null ? body : msg);
		}
	}
	
	public PdfResponse streamRequest(String url, Map<String, Object> params, File... pdfFiles) throws IOException {
		HttpResponse res = null;
		if (pdfFiles == null || pdfFiles.length == 0) {
			res = simpleRequest(url, params);
		} else {
			res = multipartRequest(url, params, pdfFiles);
		}
		int pages = 0;
		try {
			String pagesStr = getHeaderValue(res, "hypdf-pages");
			if (pagesStr != null) {
				pages = Integer.parseInt(pagesStr);
			}
		} catch (NumberFormatException e) {

		}
		String pageSize = getHeaderValue(res, "hypdf-page-size");
		String pdfVersion = getHeaderValue(res, "hypdf-pdf-version");
		return new PdfResponse(pages, pageSize, pdfVersion, res.getEntity().getContent());
	}
	
	public Map<String, Object> jsonRequest(String url, Map<String, Object> params, File... pdfFiles) throws IOException {
		HttpResponse res = null;
		if (pdfFiles == null || pdfFiles.length == 0) {
			res = simpleRequest(url, params);
		} else {
			res = multipartRequest(url, params, pdfFiles);
		}
		String body = EntityUtils.toString(res.getEntity(), "utf-8");
		return JsonUtils.parse(body);
	}
	
	private HttpResponse simpleRequest(String url, Map<String, Object> params) throws IOException {
		HttpClient client = getHttpClient();
		HttpUriRequest request = null;
		if (url.endsWith("/jobstatus")) {
			StringBuilder buf = new StringBuilder();
			buf.append(url);
			char delimiter = '?';
			for (Map.Entry<String, Object> entry : params.entrySet()) {
				buf.append(delimiter)
					.append(entry.getKey())
					.append("=")
					.append(entry.getValue());
				delimiter = '&';
			}
			request = new HttpGet(buf.toString());
		} else {
			HttpPost method = new HttpPost(url);

			String json = JsonUtils.serialize(params);
			method.setEntity(new StringEntity(json));
			method.setHeader("content-type", "application/json");
			request = method;
		}
		HttpResponse res = client.execute(request);
		checkResponse(res);
		return res;
	}
	
	private HttpResponse multipartRequest(String url, Map<String, Object> params, File... pdfFiles) throws IOException {
		HttpClient client = getHttpClient();
		HttpPost method = new HttpPost(url);
		MultipartEntity entity = new MultipartEntity();
		for (Map.Entry<String, Object> entry : params.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			entity.addPart(key, new StringBody(value.toString(), Charset.forName("utf-8")));
		}
		int index = 1;
		for (File f : pdfFiles) {
			String filename = f.getName();
			String key = "file";
			if (pdfFiles.length > 1) {
				key += "_" + index;
				index++;
			}
			entity.addPart(key, new FileBody(f, filename, "application/pdf", "utf-8"));
		}
		method.setEntity(entity);

		HttpResponse res = client.execute(method);
		checkResponse(res);
		return res;
	}
	
	private HttpClient getHttpClient() {
		if (this.client == null) {
			BasicHttpParams params = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(params, getConnectionTimeout());
			HttpConnectionParams.setSoTimeout(params, getSoTimeout());
		
			DefaultHttpClient client = null;
			if (this.ignoreSSL) {
				try {
					TrustStrategy trustStrategy = new TrustStrategy() {
						public boolean isTrusted(X509Certificate[] chain, String authType)
								throws CertificateException {
							return true;
						}
					};
					X509HostnameVerifier hostnameVerifier = new AllowAllHostnameVerifier();
					SSLSocketFactory sslSf = new SSLSocketFactory(trustStrategy, hostnameVerifier);
					
					SchemeRegistry schemeRegistry = new SchemeRegistry();
					schemeRegistry.register(new Scheme("https", 8443, sslSf));
					schemeRegistry.register(new Scheme("https", 443, sslSf));

					ClientConnectionManager connection = new ThreadSafeClientConnManager(schemeRegistry);
					return new DefaultHttpClient(connection);
				} catch (Exception e) {
					throw new IllegalStateException(e);
				}
			} else {
				client = new DefaultHttpClient(params);
			}
			if (getProxyInfo() != null) {
				ProxyInfo proxyInfo = getProxyInfo();
				HttpHost proxy = new HttpHost(proxyInfo.getHost(), proxyInfo.getPort());
				client.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, proxy);
				if (proxyInfo.getUserName() != null && proxyInfo.getPassword() != null) {
					client.getCredentialsProvider().setCredentials(
						new AuthScope(proxyInfo.getHost(), proxyInfo.getPort()),
						new UsernamePasswordCredentials(proxyInfo.getUserName(), proxyInfo.getPassword()));
				}
			}
			this.client = client;
		}
		return this.client;
	}
}

