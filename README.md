# feedxcavator2

This thing is able to convert anything to RSS with an arbitrary level of 
fine-tuning by using CSS selectors. There is a built-in [pubsubhubbub](https://en.wikipedia.org/wiki/PubSubHubbub)
server, and it's also possible to program an arbitrary
feed-extraction process in a simple Clojure-based DSL directly through the web ui.
Because it's designed as a Google App Engine application, it's troublesome to 
create a GAE accounts, upload GAE applications and analyze web pages manually to 
craft necessary CSS selectors, probably no one would use the app except me, 
so here is what it looks like:

<a href="https://github.com/GChristensen/feedxcavator2/blob/master/img/xcavator.png" target="_blank"><img src="https://github.com/GChristensen/feedxcavator2/blob/master/img/xcavator_thumb.png" /></a>&nbsp;&nbsp;<a href="https://github.com/GChristensen/feedxcavator2/blob/master/img/custom.png" target="_blank"><img src="https://github.com/GChristensen/feedxcavator2/blob/master/img/custom_thumb.png" /></a>

tl&dr: this is an RSS producer that is able to transform any site
to RSS (IT'S NOT AN RSS AGGREGATOR!).

### Supported CSS Subset

Only the following CSS capabilities are currently supported by __feedxcavator2__:

<pre>
* Elements:                     div
* IDs:                          div#some-id
* Classes:                      div.some-class
* Descendants:                  h1 a
* Direct hierarchy:             div#some-id > h1.some-class > a
* Attribute check:              a[attr]
* Attribute value:              a[attr="value"]
* Attribute substring:          a[attr*="substr"]
* Pseudo-classes:               h1:first-child
* Parameterized pseudo-classes: a:nth-of-type(3)
</pre>

__feedxcavator2__ uses [enlive](https://github.com/cgrand/enlive#readme)
library for HTML processing and internally converts all CSS selectors into
[enlive selectors](http://enlive.cgrand.net/syntax.html).
The conversion routine is quite straightforward, so it's 
better to use enlive selectors in complex cases if css selectors do not work. 
__feedxcavator2__ will assume that elnive selectors are used if the selector 
string is wrapped in square brackets (e.g. [:div#some-id :> :h1.some-class 
:> :a] or [root]) and will not try to convert them.
Although, regular CSS selectors should successfully deal with relatively simple hierarchical 
queries, which should be enough in the majority of cases.

### Ordinary and custom extractors

__feedxcavator2__ will use only supplied CSS selectors to extract verbatim data if the 
"Custom excavator" field is left blank. If not, it assumes that the field contains the name 
of a custom extractor function defined in Clojure programming language at the "Custom 
extractors" page. In this case field "Custom parameters" may contain a string-readable 
Clojure datum which will be fed to `read-string` function and passed to the extractor.

There are two types of processing functions which are defined by the `defextractor` and
`defbackground` macros. The first is intended for direct and fast data conversion,
the later should be used for heavy feeds fetched in the background. Functions defined
with `defextractor` are called during the direct feed URL request (processing time of 
foreground GAE instance requests is limited to one minute), functions defined with
`defbackground` are called in background tasks defined by `deftask` macro (the tasks 
are executed at GAE backend instances with the time limit of 10 minutes). 
For feeds with `defbackground` extractors feed link request will return RSS stored earlier by 
the task (you don't need anyhow bother on this or specify this in feed settings, 
all is resolved by DSL), so RSS will change only after the next task execution. 

Background task will also automatically notify aggregator through pubsubhubbub protocol 
if "Realtime" flag in the feed settings is checked, so the aggregator can get data of realtime
feeds just after extraction.

Extraction DSL example:

```clojure
;; task setup 

;; define tasks that will fetch all feeds which name (specified in the "Feed title" 
;; field at the task settings) contain one of the given strings
(deftask fetch-daily-feeds ["autofetch"])
(schedule fetch-daily-feeds 13 00) ; GMT
(schedule fetch-daily-feeds 18 00)

(deftask fetch-periodic-feeds ["tumblr:mass" "fb:" "good morning"])
(schedule fetch-periodic-feeds 13 10)

;; utility functions

;; transform page defined by url to a list of the maps (headlines) with the following fieleds:
;; {
;;  :title "headline title" 
;;  :link "headline url" 
;;  :summary "article summary" 
;;  :image "image url" 
;;  :html <enlive headline html representation>
;; }
;; api/apply-selectors function magically knows how to apply selectors from the feed settings 
;; to the page
;; it's also possible to make enlive selects from the :html field
(defn parse-page [url]
  (let [response (api/fetch-url url)
        doc-tree (api/resp->enlive response)]
    (when doc-tree
      (api/apply-selectors doc-tree))))
 
;; filter out headlines which links are already has been seen by the fetcher 
(defn filter-fetcher-history [uuid headlines]
  (let [history (:entries (db/query-fetcher-history uuid))
        result (filter #(not (history (:link %))) headlines)]
    (db/store-fetcher-history! uuid (map #(:link %) headlines))
    result))

;; extractors

;; extractor should return a collection of headline maps with the following fields:
;; {
;;  :title "headline title" 
;;  :link "headline url" 
;;  :summary "article summary" 
;;  :image "image url" 
;; }
;; `feed-settings` parameter contains data from feed settings, `params` contain value of the
;; "Custom parameters" field passed through `read-string` 
(defextractor multipage-extractor [feed-settings params]
  (apply concat (parse-page (:target-url feed-settings))
                (parse-page (str (:target-url feed-settings) "/page/2"))))



```

### Private Deployment

You may [install](http://code.google.com/appengine/docs/java/gettingstarted/uploading.html) 
a private [instance](https://github.com/GChristensen/feedxcavator/downloads)
of the application on your GAE account, and only the account owner will be able 
to create or manage feeds (but still will be able to share feed links). The only 
thing you need to do is to fill in application id in the 'appengine-web.xml' file.

### License

Copyright (C) 2011 g/christensen (gchristnsn@gmail.com)

Distributed under the Eclipse Public License, the same as Clojure.

