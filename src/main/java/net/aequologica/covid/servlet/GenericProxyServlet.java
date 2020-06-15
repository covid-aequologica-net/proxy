package net.aequologica.covid.servlet;

import java.io.IOException;
import java.util.AbstractMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebInitParam;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

// @formatter:off
@SuppressWarnings("deprecation")
@WebServlet(urlPatterns = "/proxy/*", initParams = { 
        @WebInitParam(name = "targetUri", value = "{scheme}://{host}:{port}/{path}#{proxyType}"),
        @WebInitParam(name = "log", value = "true"), 
})
//@formatter:on
public class GenericProxyServlet extends org.mitre.dsmiley.httpproxy.patch.URITemplateProxyServlet {
    private static final long serialVersionUID = 1L;

    private Map.Entry<String, Integer> onPremiseProxy = null;
    private String consumerAccount = null;

    @Override
    public void init() throws ServletException {
        super.init();

        ////////////////// canary
        String proxyHost = System.getenv("HC_OP_HTTP_PROXY_HOST");
        String proxyPortAsAString = System.getenv("HC_OP_HTTP_PROXY_PORT");
        String globalhost = System.getenv("HC_GLOBAL_HOST");
        if (proxyHost != null && proxyPortAsAString != null && globalhost != null
                && (globalhost.endsWith(".hana.ondemand.com") || globalhost.endsWith(".hanatrial.ondemand.com"))) {
            int proxyPort;
            try {
                proxyPort = Integer.parseInt(proxyPortAsAString);
                onPremiseProxy = new AbstractMap.SimpleEntry<>(proxyHost, proxyPort);
            } catch (NumberFormatException e) {
                onPremiseProxy = null;
            }
        }
        this.consumerAccount = System.getenv("HC_ACCOUNT");
        ////////////////// canary
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doGet(req, resp);
    }

    @Override
    protected HttpClient createHttpClient() {

        try {

            HttpClientBuilder clientBuilder = HttpClients.custom();

            // trust any server
            final SSLContextBuilder builder = new SSLContextBuilder();
            builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());
            final SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(builder.build(),
                    new NoopHostnameVerifier());
            clientBuilder.setSSLSocketFactory(sslsf);
            return clientBuilder.build();

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        super.copyRequestHeaders(servletRequest, proxyRequest);

        ////////////////// canary
        String proxyType = servletRequest.getAttribute("ZEBULON_PROXY_TYPE").toString();
        if (proxyType == null) {
            throw new RuntimeException("[zebulon] Missing \"ZEBULON_PROXY_TYPE\" request attribute");
        }
        if (proxyType.equals("Internet")) {
            String http_proxyHost = System.getProperty("http.proxyHost");
            String http_proxyPort = System.getProperty("http.proxyPort");
            if (http_proxyHost != null && !http_proxyHost.trim().isEmpty() && http_proxyPort != null
                    && !http_proxyPort.trim().isEmpty()) {
                try {
                    Integer http_proxyPortAsInteger = Integer.valueOf(http_proxyPort);
                    HttpHost pprrooxxyy = new HttpHost(http_proxyHost, http_proxyPortAsInteger);
                    proxyRequest.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, pprrooxxyy);
                } catch (NumberFormatException e) {
                }
            }
        } else if (onPremiseProxy != null) {
            HttpHost pprrooxxyy = new HttpHost(onPremiseProxy.getKey(), onPremiseProxy.getValue());
            proxyRequest.getParams().setParameter(ConnRoutePNames.DEFAULT_PROXY, pprrooxxyy);
            // http://www.baeldung.com/httpclient-stop-follow-redirect
            proxyRequest.getParams().setParameter(ClientPNames.HANDLE_REDIRECTS, false);

            if (consumerAccount != null && !consumerAccount.trim().isEmpty()) {
                proxyRequest.setHeader("SAP-Connectivity-ConsumerAccount", consumerAccount);
            }
        }

        ////////////////// canary
    }
}
