(ns feedxcavator.mock-backend
  (:require [clojure.string :as str]
            [cljs.pprint :as pprint])
  (:use [cljs.reader :only [read-string]]))

(def mock-db
  {
   "/front/list-feeds" '({:uuid "5dced3d9e6e847df97adfeb5f872c531", :group nil, :title "Hacker News"} {:uuid "cbfea78f8a6649aeb12d355b1a75f9ef", :group "group/example", :title "Linux Questions"} {:uuid "814557872f7945f0a3d4ee48c0b41ea7", :group nil, :title "Slashdot"})

   ;;;;;;;;;;;;;;;;;;;
   "/front/feed-definition" { "5dced3d9e6e847df97adfeb5f872c531" "# This example transforms Hacker News RSS by omitting every headline\r\n# which does not contain words \"computer\" or \"software\"\r\n# In practice it is more convenient to use the built-in wordfilter feature\r\n\r\ntitle: Hacker News\r\nsuffix: hacker-news\r\nsource: https://news.ycombinator.com/rss\r\nextractor: hacker-news-extractor\r\nparams: [computer, software]\r\n"
                              "814557872f7945f0a3d4ee48c0b41ea7" "# This example takes three pages from Slashdot (including the front one)\r\n\r\ntitle: Slashdot\r\nsuffix: slashdot\r\nsource: https://slashdot.org\r\nselectors:\r\n  item: '#firehose article'\r\n  title: h2 a\r\n  link: h2 a\r\n  summary: .body\r\npages:\r\n  include-source: true\r\n  path: '/?page=%n'\r\n  increment: 1\r\n  start: 1\r\n  end: 2"
                              "cbfea78f8a6649aeb12d355b1a75f9ef" "# This example crawls \"SUSE / openSUSE\" and \"Red Had\" subforums at linuxquestions.org\r\n# The first post of each thread is extracted by linux-questions-extractor\r\n\r\ntitle: Linux Questions\r\nsuffix: linux-questions\r\nsource: https://www.linuxquestions.org/questions/\r\ngroup: group/example\r\ntask: forums\r\nselectors:\r\n  item:  'tbody[id^=\"threadbits\"] tr'\r\n  title: 'a[id^=\"thread_title\"]'\r\n  link:  'a[id^=\"thread_title\"]'\r\nfilter:\r\n  history: true\r\nrealtime: true\r\nextractor: linux-questions-extractor\r\nparams: [suse-opensuse-60, red-hat-31]\r\n"
                             }
   ;;;;;;;;;;;;;;;;;;;
   "/front/feed-url" {"5dced3d9e6e847df97adfeb5f872c531" "https://feedxcavator.appspot.com/feed/hacker-news"
                     "814557872f7945f0a3d4ee48c0b41ea7" "https://feedxcavator.appspot.com/feed/slashdot"
                     "cbfea78f8a6649aeb12d355b1a75f9ef" "https://feedxcavator.appspot.com/feed/linux-questions"
                     }
   ;;;;;;;;;;;;;;;;;;;
   "/front/test-feed" {
"5dced3d9e6e847df97adfeb5f872c531"
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<rss version=\"2.0\">
  <channel>
    <title>Hacker News</title>
    <link>https://news.ycombinator.com/rss</link>
    <link href=\"https://feedxcavator.appspot.com/websub\" rel=\"hub\"/>
    <link href=\"https://feedxcavator.appspot.com/feed/hacker-news\" rel=\"self\" type=\"application/rss+xml\"/>
    <pubDate>Sat, 05 Oct 2019 12:33:44 +0000</pubDate>
    <lastBuildDate>Sat, 05 Oct 2019 12:33:44 +0000</lastBuildDate>
    <item>
      <title>Introduction to Theoretical Computer Science</title>
      <link>https://introtcs.org/public/index.html</link>
      <guid isPermaLink=\"false\">https://introtcs.org/public/index.html</guid>
      <description>&lt;a href=\"https://news.ycombinator.com/item?id=21162963\"&gt;Comments&lt;/a&gt;</description>
    </item>
  </channel>
</rss>"

"814557872f7945f0a3d4ee48c0b41ea7"
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<rss version=\"2.0\">
  <channel>
    <title>Slashdot</title>
    <link>https://slashdot.org</link>
    <link href=\"https://feedxcavator.appspot.com/feed/slashdot\" rel=\"self\" type=\"application/rss+xml\"/>
    <pubDate>Sat, 05 Oct 2019 15:57:11 +0000</pubDate>
    <lastBuildDate>Sat, 05 Oct 2019 15:57:11 +0000</lastBuildDate>
    <item>
      <title>Monty Python's 50th Anniversary Celebrated With 'Extremely Silly' Event</title>
      <link>http://entertainment.slashdot.org/story/19/10/05/0415209/monty-pythons-50th-anniversary-celebrated-with-extremely-silly-event</link>
      <description>&lt;div id=\"text-116794194\" class=\"p\"&gt;
				The Monty Python character known as the Gumby would often be found saying \"My brain hurts\".  Now Reuters reports:
&lt;i&gt;In what is &lt;a href=\"https://www.reuters.com/article/us-montypython-anniversary/pine-no-more-monty-python-celebrates-50-years-of-silliness-idUSKBN1WJ1IF\"&gt;billed as an \"extremely silly\" event&lt;/a&gt;, hordes of Monty Python fans will gather in full Gumby attire in London on Saturday to celebrate the British comedy troupe's 50th anniversary. Kitted out in rubber boots, sleeveless sweaters, rolled-up trousers and with knotted handkerchiefs on their heads, they will attempt to set a Guinness World Record for the Largest Gathering of People Dressed as Gumbys.  \"It's all so excitingly pointless,\" said Python Terry Gilliam, who will &lt;a href=\"https://www.roundhouse.org.uk/whats-on/2019/largest-gathering-of-people-dressed-as-gumbys/\"&gt;host the event&lt;/a&gt;.&lt;/i&gt; &lt;br /&gt;
Meanwhile, the Guardian reports on recently-rediscovered documents from the BBC's archives about the show's launch in 1969:
&lt;i&gt;The BBC response, the archives make clear, was far less positive. At the weekly meeting where senior managers discussed the output, &lt;a href=\"https://www.theguardian.com/tv-and-radio/2019/oct/04/monty-python-at-50-a-half-century-of-silly-walks-edible-props-and-dead-parrots\"&gt;the head of factual had found Python \"disgusting\"&lt;/a&gt;, arts had thought it \"nihilistic and cruel\", while religion objected to a Gilliam animation in which \"Jesus... had swung his arm\". The BBC One controller sensed the makers \"continually going over the edge of what is acceptable\".&lt;/i&gt; &lt;br /&gt;
The Guardian also tracked down 69-year-old Doug Holman who remembers John Cleese giving him tickets to watch a filming of the show when he was 19.  (\"Doug, boldly, writes back, saying he is part of a large group of friends who want to go. Cleese contacts the BBC to request a further 14 tickets...\")&lt;br /&gt; &lt;br /&gt;
50 years later, Holman seems to remember the filming as being wonderfully chaotic. \"There was a restaurant scene but I think the producer abandoned it when Cleese -- seemingly unhappy about having no lines -- disrupted each take by performing random Tourette-like impressions of a mouse being strangled by a psychotic cat. I remember it being total anarchy yet excruciatingly funny, in the literal sense. We all experienced genuine pain from extended bouts of uncontrollable laughter.\"&lt;br /&gt;&lt;/div&gt;
      </description>
    </item>

...
<DEMO>"

"cbfea78f8a6649aeb12d355b1a75f9ef"
"<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<rss version=\"2.0\">
 <channel>
   <title>Linux Questions</title>
   <link>https://www.linuxquestions.org/questions/</link>
   <link href=\"https://feedxcavator.appspot.com/websub\" rel=\"hub\"/>
   <link href=\"https://feedxcavator.appspot.com/feed/linux-questions\" rel=\"self\" type=\"application/rss+xml\"/>
   <pubDate>Sat, 05 Oct 2019 16:15:31 +0000</pubDate>
   <lastBuildDate>Sat, 05 Oct 2019 16:15:31 +0000</lastBuildDate>
   <item>
     <title>Reminder: Are you running an unsupported version of linux?</title>
     <link>https://www.linuxquestions.org/questions/suse-opensuse-60/reminder-are-you-running-an-unsupported-version-of-linux-524951/</link>
     <guid isPermaLink=\"false\">https://www.linuxquestions.org/questions/suse-opensuse-60/reminder-are-you-running-an-unsupported-version-of-linux-524951/</guid>
     <description>&lt;div id=\"post_message_2613043\"&gt;&lt;!-- google_ad_section_start --&gt;We would like to remind everyone to make sure that your systems are currently supported by your Linux vendor or are receiving security updates some other way. If you haven't bothered to update your system or configure automatic updates, then now would be a good time to start. &lt;br /&gt;
&lt;br /&gt;
&lt;b&gt;SUSE/openSUSE&lt;/b&gt;&lt;br /&gt;
&lt;a href=\"http://en.opensuse.org/SUSE_Linux_Lifetime\" target=\"_blank\" rel=\"nofollow\"&gt;openSUSE Life Cycles&lt;/a&gt;&lt;br /&gt;
&lt;a href=\"http://support.novell.com/lifecycle/lcSearchResults.jsp?st=SuSE+linux&amp;amp;x=0&amp;amp;y=0&amp;amp;sl=-1&amp;amp;sg=-1&amp;amp;pid=1000\" target=\"_blank\" rel=\"nofollow\"&gt;Commercial SuSE Distributions&lt;/a&gt;&lt;br /&gt;
&lt;br /&gt;
&lt;br /&gt;
&lt;b&gt;Fedora&lt;/b&gt;&lt;br /&gt;
&lt;a href=\"http://fedoraproject.org/wiki/LifeCycle/EOL\" target=\"_blank\" rel=\"nofollow\"&gt;http://fedoraproject.org/wiki/LifeCycle/EOL&lt;/a&gt;&lt;br /&gt;
&lt;a href=\"http://fedoraproject.org/wiki/Communicate/IRC/Fedora-EOL-Support\" target=\"_blank\" rel=\"nofollow\"&gt;http://fedoraproject.org/wiki/Commun...ra-EOL-Support&lt;/a&gt;&lt;br /&gt;
&lt;br /&gt;
&lt;b&gt;Red Hat&lt;/b&gt;&lt;br /&gt;
&lt;font color=\"Red\"&gt;Unsupported: Redhat Linux&lt;/font&gt; (RHL, not RHEL), see &lt;a href=\"http://www.redhat.com/security/updates/eol/\" target=\"_blank\" rel=\"nofollow\"&gt;http://www.redhat.com/security/updates/eol/&lt;/a&gt;&lt;br /&gt;
Supported: Red Hat Enterprise Linux 4, 5 and 6. The End of Production Phase 3 for RHEL5 is March 31st 2017 and for RHEL6 November 30 2020, see the &lt;a href=\"https://access.redhat.com/support/policy/updates/errata/\" target=\"_blank\" rel=\"nofollow\"&gt;Red Hat Enterprise Linux Life Cycle&lt;/a&gt;.&lt;br /&gt;
&lt;br /&gt;
&lt;b&gt;Mandriva&lt;/b&gt;&lt;br /&gt;
&lt;a href=\"http://www.mandriva.com/en/mandriva-product-lifetime-policy\" target=\"_blank\" rel=\"nofollow\"&gt;http://www.mandriva.com/en/mandriva-...ifetime-policy&lt;/a&gt;&lt;br /&gt;
&lt;br /&gt;
&lt;b&gt;Ubuntu&lt;/b&gt;&lt;br /&gt;
Note:Standard releases are supported for 18 months. Enterprise grades (LTS) are supported for 3 years on desktop 5 years on server&lt;br /&gt;
&lt;a href=\"https://wiki.ubuntu.com/Releases\" target=\"_blank\" rel=\"nofollow\"&gt;https://wiki.ubuntu.com/Releases&lt;/a&gt;&lt;br /&gt;
&lt;br /&gt;
For those distros not listed, please take a minute and check with your vendors website. Remember that keeping your system updated with security patches is one of the most effective measures you can take to keep from getting compromised.&lt;!-- google_ad_section_end --&gt;&lt;/div&gt;</description>
   </item>

...
<DEMO>"
                       }
   ;;;;;;;;;;;;;;;;;;;
   "/front/get-code"
   {
    {:type "extractors"} ";; Feed extractors\r\n\r\n;; Hacker News ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;\r\n\r\n(defextractor hacker-news-extractor [feed]\r\n  (let [words (:params feed)\r\n        rss (api/fetch-url (:source feed) :as :xml)\r\n        headlines (map #(assoc {}\r\n                          :title (<t (?1 % [:title]))\r\n                          :link (<t (?1 % [:link]))\r\n                          :summary (<t (?1 % [:description])))\r\n                       (?* rss [:item]))]\r\n    (filter #(some (fn [s] (str/includes? (str/lower-case %) s)) words) headlines)))\r\n\r\n\r\n;; Linux Questions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;\r\n  \r\n(defbackground linux-questions-extractor [feed]\r\n  (traverse-forum feed api/parse-html-page ;; parse-html-page will apply selectors \r\n                  (fn [t thread]           ;; supplied in the feed definition\r\n                    (api/html-render \r\n                      (?1 thread [[:div (enlive/attr-starts :id \"post_message\")]])))))"
    {:type "handlers"}   ";; Sample hander that is available at <your project id>.appspot.com/handler/add-lq-subforum\r\n;; The handler accepts one query parameter named \"subforum\" and requires authorization \r\n;; with x-feedxcavator-auth header\r\n\r\n(defhandler ^:auth add-lq-subforum [subforum]\r\n  (let [feed (db/find-feed :suffix \"linux-questions\")]\r\n    (db/persist-feed! (update-in feed [:params] conj subforum))))"
    {:type "library"}    ";; A place for reusable library functions\r\n\r\n(defn traverse-forum [feed parse-page get-summary]\r\n  (apply concat\r\n         (for [forum (:params feed)]\r\n           ;; get the list of forum threads\r\n           (let [forum-url (str (:source feed) forum)\r\n                 threads (parse-page feed forum-url)]\r\n             (when (seq threads)\r\n               (for [thread threads]\r\n                 ;; get the first post of each thread\r\n                 (let [thread-page (api/fetch-url (:link thread) :as :html :retry true)]\r\n                   (assoc thread :summary (get-summary thread thread-page)))))))))"
    {:type "scratch"}    ";; Scratch editor for test code evaluation\r\n\r\n(println (api/fetch-url \"http://example.com\" :as :string))\r\n"
    {:type "tasks"}      ";; Define and schedule tasks here\r\n\r\n(deftask news)\r\n(deftask forums)\r\n(deftask updates)\r\n\r\n(deftask* morning-feeds [news forums])\r\n\r\n;; check news and forums at 7:00 GMT\r\n(schedule morning-feeds 07 00)\r\n\r\n;; check forums at 15:00 GMT\r\n(schedule forums 15 00)\r\n\r\n;; run this task every two hours\r\n(schedule-periodically updates 2)"
    }
   ;;;;;;;;;;;;;;;;;;;
   "/front/get-auth-token" "3bfa7e070443352035f141ccca2f925cbadc051c"
   ;;;;;;;;;;;;;;;;;;;
   "/front/save-code" {
                       "scratch"
                       "<!doctype html>
<html>
<head>
    <title>Example Domain</title>

    <meta charset=\"utf-8\" />
    <meta http-equiv=\"Content-type\" content=\"text/html; charset=utf-8\" />
    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />
    <style type=\"text/css\">
    body {
        background-color: #f0f0f2;
        margin: 0;
        padding: 0;
        font-family: \"Open Sans\", \"Helvetica Neue\", Helvetica, Arial, sans-serif;

    }
    div {
        width: 600px;
        margin: 5em auto;
        padding: 50px;
        background-color: #fff;
        border-radius: 1em;
    }
    a:link, a:visited {
        color: #38488f;
        text-decoration: none;
    }
    @media (max-width: 700px) {
        body {
            background-color: #fff;
        }
        div {
            width: auto;
            margin: 0 auto;
            border-radius: 0;
            padding: 1em;
        }
    }
    </style>
</head>

<body>
<div>
    <h1>Example Domain</h1>
    <p>This domain is established to be used for illustrative examples in documents. You may use this
    domain in examples without prior coordination or asking for permission.</p>
    <p><a href=\"http://www.iana.org/domains/example\">More information...</a></p>
</div>
</body>
</html>"
}
   ;;;;;;;;;;;;;;;;;;;
   "/front/get-log-entries"
   {{:n 20} '({:uuid "9256f4fd1d1541c899caafa03386f385", :number 2, :level "info", :source nil, :timestamp 1570282924570, :message "Sample log message"} {:uuid "1096457e131f42da898f976a746598b9", :number 1, :level "error", :source nil, :timestamp 1570282903037, :message "java.lang.Exception: Test exception\n\tat feedxcavator.code_user$eval17627.invokeStatic(Unknown Source)\n\tat feedxcavator.code_user$eval17627.invoke(Unknown Source)\n\tat clojure.lang.Compiler.eval(Compiler.java:7177)\n\tat clojure.lang.Compiler.load(Compiler.java:7636)\n\tat clojure.lang.Compiler.load(Compiler.java:7583)\n\tat clojure.core$load_reader.invokeStatic(core.clj:4087)\n\tat clojure.core$load_string.invokeStatic(core.clj:4089)\n\tat clojure.core$load_string.invoke(core.clj:4089)\n\tat feedxcavator.code$compile_user_code.invokeStatic(code.clj:37)\n\tat feedxcavator.code$compile_user_code.invoke(code.clj:23)\n\tat feedxcavator.backend$save_code.invokeStatic(backend.clj:207)\n\tat feedxcavator.backend$save_code.invoke(backend.clj:203)\n\tat feedxcavator.app$fn__8588.invokeStatic(app.clj:30)\n\tat feedxcavator.app$fn__8588.invoke(app.clj:30)\n\tat compojure.core$wrap_response$fn__8494.invoke(core.clj:158)\n\tat compojure.core$wrap_route_middleware$fn__8478.invoke(core.clj:128)\n\tat compojure.core$wrap_route_info$fn__8483.invoke(core.clj:137)\n\tat compojure.core$wrap_route_matches$fn__8487.invoke(core.clj:146)\n\tat compojure.core$routing$fn__8502.invoke(core.clj:185)\n\tat clojure.core$some.invokeStatic(core.clj:2701)\n\tat clojure.core$some.invoke(core.clj:2692)\n\tat compojure.core$routing.invokeStatic(core.clj:185)\n\tat compojure.core$routing.doInvoke(core.clj:182)\n\tat clojure.lang.RestFn.applyTo(RestFn.java:139)\n\tat clojure.core$apply.invokeStatic(core.clj:667)\n\tat clojure.core$apply.invoke(core.clj:660)\n\tat compojure.core$routes$fn__8506.invoke(core.clj:192)\n\tat feedxcavator.app$context_binder$fn__8632.invoke(app.clj:85)\n\tat ring.middleware.keyword_params$wrap_keyword_params$fn__6433.invoke(keyword_params.clj:53)\n\tat ring.middleware.nested_params$wrap_nested_params$fn__6483.invoke(nested_params.clj:89)\n\tat ring.middleware.params$wrap_params$fn__6397.invoke(params.clj:67)\n\tat ring.middleware.multipart_params$wrap_multipart_params$fn__6557.invoke(multipart_params.clj:173)\n\tat ring.middleware.flash$wrap_flash$fn__6780.invoke(flash.clj:39)\n\tat ring.middleware.session$wrap_session$fn__6765.invoke(session.clj:108)\n\tat ring.middleware.json$wrap_json_body$fn__6984.invoke(json.clj:58)\n\tat clojure.lang.Var.invoke(Var.java:384)\n\tat appengine_magic.servlet$make_servlet_service_method$fn__268.invoke(servlet.clj:99)\n\tat feedxcavator.app_servlet$_service.invokeStatic(app_servlet.clj:7)\n\tat feedxcavator.app_servlet$_service.invoke(app_servlet.clj:6)\n\tat feedxcavator.app_servlet.service(Unknown Source)\n\tat org.eclipse.jetty.servlet.ServletHolder.handle(ServletHolder.java:848)\n\tat org.eclipse.jetty.servlet.ServletHandler$CachedChain.doFilter(ServletHandler.java:1772)\n\tat com.google.apphosting.utils.servlet.JdbcMySqlConnectionCleanupFilter.doFilter(JdbcMySqlConnectionCleanupFilter.java:60)\n\tat org.eclipse.jetty.servlet.ServletHandler$CachedChain.doFilter(ServletHandler.java:1759)\n\tat org.eclipse.jetty.servlet.ServletHandler.doHandle(ServletHandler.java:582)\n\tat org.eclipse.jetty.server.handler.ScopedHandler.handle(ScopedHandler.java:143)\n\tat org.eclipse.jetty.security.SecurityHandler.handle(SecurityHandler.java:513)\n\tat org.eclipse.jetty.server.session.SessionHandler.doHandle(SessionHandler.java:226)\n\tat org.eclipse.jetty.server.handler.ScopedHandler.handle(ScopedHandler.java:143)\n\tat org.eclipse.jetty.server.handler.HandlerWrapper.handle(HandlerWrapper.java:134)\n\tat com.google.apphosting.runtime.jetty9.ParseBlobUploadHandler.handle(ParseBlobUploadHandler.java:119)\n\tat org.eclipse.jetty.server.handler.ContextHandler.doHandle(ContextHandler.java:1182)\n\tat com.google.apphosting.runtime.jetty9.AppEngineWebAppContext.doHandle(AppEngineWebAppContext.java:187)\n\tat org.eclipse.jetty.servlet.ServletHandler.doScope(ServletHandler.java:512)\n\tat org.eclipse.jetty.server.session.SessionHandler.doScope(SessionHandler.java:185)\n\tat org.eclipse.jetty.server.handler.ContextHandler.doScope(ContextHandler.java:1112)\n\tat org.eclipse.jetty.server.handler.ScopedHandler.handle(ScopedHandler.java:141)\n\tat com.google.apphosting.runtime.jetty9.AppVersionHandlerMap.handle(AppVersionHandlerMap.java:293)\n\tat org.eclipse.jetty.server.handler.HandlerWrapper.handle(HandlerWrapper.java:134)\n\tat org.eclipse.jetty.server.Server.handle(Server.java:539)\n\tat org.eclipse.jetty.server.HttpChannel.handle(HttpChannel.java:333)\n\tat com.google.apphosting.runtime.jetty9.RpcConnection.handle(RpcConnection.java:213)\n\tat com.google.apphosting.runtime.jetty9.RpcConnector.serviceRequest(RpcConnector.java:81)\n\tat com.google.apphosting.runtime.jetty9.JettyServletEngineAdapter.serviceRequest(JettyServletEngineAdapter.java:134)\n\tat com.google.apphosting.runtime.JavaRuntime$RequestRunnable.dispatchServletRequest(JavaRuntime.java:728)\n\tat com.google.apphosting.runtime.JavaRuntime$RequestRunnable.dispatchRequest(JavaRuntime.java:691)\n\tat com.google.apphosting.runtime.JavaRuntime$RequestRunnable.run(JavaRuntime.java:661)\n\tat com.google.apphosting.runtime.JavaRuntime$NullSandboxRequestRunnable.run(JavaRuntime.java:853)\n\tat com.google.apphosting.runtime.ThreadGroupPool$PoolEntry.run(ThreadGroupPool.java:270)\n\tat java.lang.Thread.run(Thread.java:748)\n"})}

   ;;;;;;;;;;;;;;;;;;;
   "/front/get-settings"
   {:version "2.1.0"}
})



(defn get-text
  ([url params handler]
   (handler (if (not params)
              (mock-db url)
              (if (:uuid params)
                ((mock-db url) (:uuid params))
                ((mock-db url) params)))))
  ([url handler]
   (get-text url nil handler)))

(defn get-edn
  ([url params handler]
   (handler (if (not params)
              (mock-db url)
              (if (:uuid params)
                ((mock-db url) (:uuid params))
                ((mock-db url) params)))))
  ([url handler]
   (get-edn url nil handler)))

(defn post-multipart [url params handler]
  (js/setTimeout
    (fn []
      (handler (if (or (= url "/front/feed-definition")
                       (= url "/front/save-code"))
                 "DEMO! Nothing is saved."
                 (if (not params)
                          (mock-db url)
                          (if (:uuid params)
                            ((mock-db url) (:uuid params))
                            (if (:type params)
                             ((mock-db url) (:type params))
                             ((mock-db url) params)))))))
    (if (or (= url  "/front/test-feed")
            (= url  "/front/save-code"))
      1000
      0)))

(defn extract-server-error [response-text]
  response-text)