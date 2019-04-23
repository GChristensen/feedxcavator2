package feedxcavator;

import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.util.Cookie;
import com.gargoylesoftware.htmlunit.CookieManager;
import com.gargoylesoftware.htmlunit.util.FalsifyingWebConnection;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;

import java.net.URL;

public class CFProof
{
    public static String getPages(String seedPage, String pageBase, String [] pages, String selector)
    {
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

        try {

            String url = seedPage;

            System.out.println(url);

            HtmlPage page = client.getPage(url);

            if (page.querySelector(".cf-browser-verification") != null) {
                client.getOptions().setJavaScriptEnabled(true);
                page = client.getPage(url);

                synchronized(page) {
                    page.wait(7000);
                }

                client.getOptions().setJavaScriptEnabled(false);
            }

            String html = "";

            for (int i = 0; i < pages.length; ++i) {
                url = pageBase.concat(pages[i]);

                System.out.println(url);

                HtmlElement e = ((HtmlPage)client.getPage(url)).getBody();

                if (selector != null)
                    e = e.querySelector(selector);

                if (e != null) {
                    html += e.asXml();
                }
            }

            return html;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }
}
