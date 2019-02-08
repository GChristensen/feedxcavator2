package feedxcavator;

import javax.script.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Optional;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static javax.script.ScriptContext.ENGINE_SCOPE;

final class PatternStreamer {
    private final Pattern pattern;
    public PatternStreamer(String regex) {
        this.pattern = Pattern.compile(regex);
    }
    public PatternStreamer(Pattern regex){
        this.pattern=regex;
    }
    public Stream<String> results(CharSequence input) {
        List<String> list = new ArrayList<>();
        for (Matcher m = this.pattern.matcher(input); m.find(); )
            for(int idx = 1; idx<=m.groupCount(); ++idx){
                list.add(m.group(idx));
            }
        return list.stream();
    }
}

public class CFSolver {
//
//    private static Logger log = LoggerFactory.getLogger(CloudFlareAuthorizer.class);
//
//    private HttpClient httpClient;
//    private HttpClientContext httpClientContext;
    private Pattern jsChallenge = Pattern.compile("name=\"jschl_vc\" value=\"(.+?)\"");
    private Pattern password = Pattern.compile("name=\"pass\" value=\"(.+?)\"");
    private Pattern jsScript = Pattern.compile("setTimeout\\(function\\(\\)\\{\\s+(var s,t,o,p,b,r,e,a,k,i,n,g,f.+?\\r?\\n[\\s\\S]+?a\\.value =.+?)\\r?\\n");


    private ScriptEngineManager engineManager = new ScriptEngineManager();
    private ScriptEngine engine = engineManager.getEngineByName("nashorn");

    private static class Response{
        private int httpStatus;
        private String responseText;

        Response(int httpStatus, String responseText) {
            this.httpStatus = httpStatus;
            this.responseText = responseText;
        }
    }

//    public CloudFlareAuthorizer(HttpClient httpClient, HttpClientContext httpClientContext) {
//        this.httpClient = httpClient;
//        this.httpClientContext = httpClientContext;
//    }

    public CFSolver() {

    }

    public String getAuthorizationResult(String url, String challenge) throws IOException, ScriptException {

        URL cloudFlareUrl = new URL(url);
        String authUrl;

        try {

            int retries = 5;
            int timer = 5000;
            //Response response = getResponse(url,url);

            //while (response.httpStatus == HttpStatus.SC_SERVICE_UNAVAILABLE && retries-- > 0) {

//                log.trace("CloudFlare response HTML:");
//                log.trace(response.responseText);

                String answer = getJsAnswer(cloudFlareUrl, challenge);
                String jschl_vc = new PatternStreamer(jsChallenge).results(challenge).findFirst().orElse("");
                String pass =  new PatternStreamer(password).results(challenge).findFirst().orElse("");

                authUrl = String.format("http://%s/cdn-cgi/l/chk_jschl?jschl_vc=%s&pass=%s&jschl_answer=%s",
                        cloudFlareUrl.getHost(),
                        URLEncoder.encode(jschl_vc,"UTF-8"),
                        URLEncoder.encode(pass,"UTF-8"),
                        answer);

                //log.debug(String.format("CloudFlare auth URL: %s",authUrl));

                Thread.sleep(timer);

                //response = getResponse(authUrl, url);
            //}

//            if (response.httpStatus != HttpStatus.SC_OK) {
//                if(response.httpStatus == HttpStatus.SC_FORBIDDEN && response.responseText.contains("cf-captcha-container")){
//                    log.warn("Getting CAPTCHA request from bittrex, throttling retries");
//                    Thread.sleep(15000);
//                }
//                log.trace("Failure HTML: " + response.responseText);
//                return false;
//            }

        }
        catch(Exception e){
            //log.error("Interrupted whilst waiting to perform CloudFlare authorization",ie);
            e.printStackTrace();
            return null;
        }

//        Optional<Cookie> cfClearanceCookie = httpClientContext.getCookieStore().getCookies()
//                .stream()
//                .filter(cookie -> cookie.getName().equals("cf_clearance"))
//                .findFirst();
//
//        if(cfClearanceCookie.isPresent()) {
//            log.info("Cloudflare DDos authorization success, cf_clearance: {}",
//                    cfClearanceCookie.get().getValue());
//        }else{
//            log.info("Cloudflare DDoS is not currently active");
//        }

        return authUrl;
    }

//    private Response getResponse(String url, String referer) throws IOException {
//
//        HttpGet getRequest = new HttpGet(url);
//
//        if(referer != null)
//            getRequest.setHeader(HttpHeaders.REFERER,referer);
//
//        int hardTimeout = 30; // seconds
//        TimerTask task = new TimerTask() {
//            @Override
//            public void run() {
//                getRequest.abort();
//            }
//        };
//        new Timer(true).schedule(task, hardTimeout * 1000);
//
//        HttpResponse httpResponse = httpClient.execute(getRequest,httpClientContext);
//
//        String responseText = Utils.convertStreamToString(httpResponse.getEntity().getContent());
//        int httpStatus = httpResponse.getStatusLine().getStatusCode();
//
//        task.cancel();
//        httpResponse.getEntity().getContent().close();
//        ((CloseableHttpResponse)httpResponse).close();
//        return new Response(httpStatus,responseText);
//    }

    private String getJsAnswer(URL url, String responseHtml) throws ScriptException, MalformedURLException {

        //Credit to Anarov to the improved Regex JS parsing here from https://github.com/Anorov/cloudflare-scrape

        Matcher result = jsScript.matcher(responseHtml);

        if(result.find()){
            String jsCode = result.group(1);
            jsCode = jsCode.replaceAll("a\\.value = (.+ \\+ t\\.length).+","$1");
            jsCode = jsCode.replaceAll("\\s{3,}[a-z](?: = |\\.).+","").replace("t.length",String.valueOf(url.getHost().length()));
            jsCode = jsCode.replaceAll("[\\n\\\\']","");

            if(!jsCode.contains("toFixed")){
                throw new IllegalStateException("BUG: could not find toFixed inside CF JS challenge code");
            }

            return new BigDecimal(engine.eval(jsCode).toString()).setScale(10,BigDecimal.ROUND_HALF_UP).toString();
            //return engine.eval(jsCode).toString();
        }
        throw new IllegalStateException("BUG: could not find initial CF JS challenge code");
    }

}
