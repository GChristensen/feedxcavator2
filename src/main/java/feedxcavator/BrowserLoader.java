package feedxcavator;

import com.gargoylesoftware.htmlunit.*;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.util.FalsifyingWebConnection;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

public class BrowserLoader
{

    public static WebClient createClient() {
        WebClient client = new WebClient(BrowserVersion.CHROME);

        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setThrowExceptionOnFailingStatusCode(false);
        client.getOptions().setRedirectEnabled(true);
        client.getCache().setMaxSize(0);
        client.setJavaScriptTimeout(30000);

        client.setCookieManager(new CookieManager() {
            protected int getPort(URL url) {
                return 80;
            }
        });

        client.setWebConnection(new FalsifyingWebConnection(client) {
            @Override
            public WebResponse getResponse(WebRequest request) throws java.io.IOException {
                String host = request.getUrl().getHost().toLowerCase();
                if(host.contains("facebook") || host.contains("alexa.com") ) {
                    return createWebResponse(request, "", "application/javascript");
                }

                return super.getResponse(request);
            }
        });

        return client;
    }

    public static WebClient createClientCF(String url) {
        WebClient client = createClient();

        try {
            HtmlPage page = client.getPage(url);

            if (page.querySelector(".cf-browser-verification") != null) {
                client.getOptions().setJavaScriptEnabled(true);
                page = client.getPage(url);

                synchronized(page) {
                    page.wait(6000);
                }

                client.getOptions().setJavaScriptEnabled(false);
            }

            return client;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String doGet(WebClient client, String url, Map<String, String> headers, Map<String, String> cookies) {
        WebRequest requestSettings = null;
        try {
            requestSettings = new WebRequest(new URL(url), HttpMethod.GET);

            for (Map.Entry<String, String> entry : headers.entrySet())
                requestSettings.setAdditionalHeader(entry.getKey(), entry.getValue());

            Page page = client.getPage(requestSettings);
            WebResponse response = page.getWebResponse();

            return response.getContentAsString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static String doPost(WebClient client, String url, String payload, Map<String, String> headers, Map<String, String> cookies) {
        WebRequest requestSettings = null;
        try {
            requestSettings = new WebRequest(new URL(url), HttpMethod.POST);

            for (Map.Entry<String, String> entry : headers.entrySet())
                requestSettings.setAdditionalHeader(entry.getKey(), entry.getValue());

            requestSettings.setRequestBody(payload);

            Page page = client.getPage(requestSettings);
            WebResponse response = page.getWebResponse();

            return response.getContentAsString();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


}
