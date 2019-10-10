# feedxcavator2

This [Google App Engine](https://cloud.google.com/appengine/) application is
able to convert anything to [RSS](https://en.wikipedia.org/wiki/RSS) with an
arbitrary level of fine-tuning. It has a built-in
[WebSub](https://en.wikipedia.org/wiki/WebSub) server for real-time feed updates
and allows to program a feed-extraction process in a simple Clojure-based
[DSL](https://en.wikipedia.org/wiki/Domain-specific_language) directly through
the web-UI (see a [demo](https://feedxcavator.appspot.com)).

### Overview

The features of generated feeds are defined by a feed
[YAML](https://www.tutorialspoint.com/yaml/index.htm) config which specify feed
title, URL, address of the source page, parts of the page to extract, filtering
options, etc. Feedxcavator can automatically process several pages of a
paginated site by a URL template defined in YAML. It is also possible to filter 
feed content by applying one of the user-defined word-filters. Optionally, code in
[Clojure](https://clojure.org/) programming language could be used to extract
feed contents if some complex processing is necessary.

Content extraction could be performed when a feed aggregator fetches 
feeds by URL, or in the background at the scheduled time. In the recent
case, the obtained content may be sent to a WebSub-enabled aggregator
just after the extraction, if the 'realtime' config option is specified.

### YAML configuration parameters

```yaml
title: Feed title
suffix: feed-suffix # a string that defines feed URL at: https://<your project id>.appspot.com/feed/feed-suffix
source: https://example.com # address of the source page
charset: utf-8 # source charset, optional; use if automatic charset resolution does not work for some reason
output: rss # also "json-feed", "json" and "edn" - useful for further aggregation
group: group/subgroup # group path of the feed in UI
task: task-name # the name of a background extraction task defined with deftask in the Clojure code 
selectors: # CSS selectors of feed elements
  item: div.headline  # container element of the headline item
  title: h3 # item title
  link: a # item link (an element with href attribute)
  summary: div.preview # item description
  image: div.cover img # item cover image  (an element with src attribute)
  author: div.user-name # the name of item author
pages: # pagination parameters
  include-source: true # include the URL supplied in the "source" parameter into the set of processed pages
  path: '/?page=%n' # page path template, appended to the source URL
  increment: 1 # increment of the path parameter
  start: 2 # the initial value of the path parameter
  end: 2 # the maximum value of the path parameter
filter: # filtering options
  history: true # feed will omit items seen at the previous time if true  
  content: title+summary # also "title", the feed will be filtered by content if specified
  wordfilter: default # a word-filter to apply
realtime: true # use WebSub to publish a background feed
partition: 100 # send a background feed to the aggregator by parts with the specified number of items in each 
extractor: extractor-function-name # name of the Clojure extractor function to invoke
params: [any addiitional data, [123, 456]] # arbitrary feed parameters available at the Clojure code
```

In the Clojure code all the mentioned options of the YAML config are directly
available as fields of the `feed` extractor function argument (see examples
below). Any other fields could be accessed through the `(api/extra feed :field)`
call.

### Supported CSS subset

The following CSS features are supported by __feedxcavator2__:

```* Elements:                       div
* IDs:                            div#some-id
* Classes:                        div.some-class
* Descendants:                    h1 a
* Direct hierarchy:               div#some-id > h1.some-class > a
* Attribute presents:             a[attr]
* Attribute value:                a[attr="value"]
* Attribute starts:               a[attr^="substr"]
* Attribute contains:             a[attr*="substr"]
* Attribute ends:                 a[attr$="substr"]
* Pseudo-classes:                 h1:first-child
* Parameterized pseudo-classes:   a:nth-of-type(3) 
```

__feedxcavator2__ uses [enlive](https://github.com/cgrand/enlive#readme) library
for HTML processing and internally converts all CSS selectors into [enlive
selectors](http://enlive.cgrand.net/syntax.html). It is possible to use elinve
selectors directly: __feedxcavator2__ will not try to convert a selector if it
 resembles a readable Clojure datum (e.g. **[[:tr (attr= :colspan "2")] :a]**).

### Automatic and custom feed extraction

__feedxcavator2__ will use only supplied CSS selectors to extract data if the
"extractor" field of the YAML config is not specified. Otherwise, it is assumed
that the field contains the name of an extractor function that should be invoked
to provide content. Extractor functions could be coded in Clojure programming
language at the "Extractors" tab. 

There are two types of extractors which are defined by the `defextractor` and
`defbackground` Clojure macros. The first is intended for lightweight directly
obtainable feeds, the later should be used to produce heavy feeds that are
assembled in the background.

Functions defined with `defextractor` are called by a foreground GAE instance 
during the feed URL request by an aggregator. In this case the processing time of
requests is limited to one minute.

Functions defined with `defbackground` are called in the context of tasks
defined by the `deftask` macro. The tasks are executed by GAE backend instances
without a time limit (you don't need to bother on this or specify this in the
feed settings, all is resolved by DSL). In the case of background feeds an
aggregator obtains previously stored feed content from the database, so the feed
will change only after the next task execution.

For the generation of [RSS](https://en.wikipedia.org/wiki/RSS) or [JSON-feed](https://jsonfeed.org/)
content by the application extractors should return a collection of Clojure maps (each
representing a headline) with the following set of fields:

```clojure
{
  :title "Headline Title" 
  :link "Headline URL" 
  :author "Author name (optional)"
  :summary "Article summary (optional)" 
  :image "Image URL (optional)"
  :image-type "image/mime-type (optional)"
}
```

all other fields are ignored. But if the feed is serialized into plain JSON or EDN, 
the result may contain any set of fields.

### Feedxcavator API

The full list of functions and macros available from Clojure code through the `api/` 
namespace prefix could be found [here](https://github.com/GChristensen/feedxcavator2/blob/master/src/main/clj/feedxcavator/code_api.clj)
(currently undocumented). Some other prefixes are also could be used in Clojure code:

* `str/` - [clojure.string](https://clojure.github.io/clojure/clojure.string-api.html)
* `json/` - [clojure.data.json](https://github.com/clojure/data.json) 
* `macro/` - [clojure.tools.macro](https://github.com/clojure/tools.macro)
* `time/` - [clj-time.core](https://github.com/clj-time/clj-time)
* `time-fmt/` - [clj-time.format](https://github.com/clj-time/clj-time)
* `enlive/` - [net.cgrand.enlive-html](https://github.com/cgrand/enlive)
* `log/` - [feedxcavator.log] - logging functions
* `db/` - [feedxcavator.db] - internal db abstractions

Several operations are so abundant in feed generation, that their shortcuts are defined directly in the
user code namespace: 

* `?1` - `(fn [nodes selector] (first (enlive/select nodes selector)))` - the equivalent of `nodes.querySelector(selector)`
* `?*` - `(fn [nodes selector] (enlive/select nodes selector))` - the equivalent of `nodes.querySelectorAll(selector)`
* `<t` - `(fn [nodes] (enlive/text nodes))` - equivalent of `nodes.textContent`
* `<*` - `(fn [nodes] (apply str (enlive/emit* nodes)))` - equivalent of `nodes.outerHTML`

### Fetching web-resources

Use `api/fetch-url` function to obtain any web resource. It accepts the same arguments
as [appengine-magic.services.url-fetch/fetch](https://github.com/GChristensen/appengine-magic#url-fetch-service),
but behaves a little bit differently:

- by default `api/fetch-url` returns `nil` for HTTP response codes >= 300 and does not throw 
any network-related exceptions. To find out what is happening use `api/get-last-http-error`
to get the HTTP response code and `api/get-last-http-response` to get the last
 [ring](https://github.com/ring-clojure) response.
- it accepts additional `:as` keyword argument which converts the response to the corresponding format,
defined by the argument value:
  * `:html` - parsed enlive HTML representation
  * `:xml` - parsed enlive XML representation
  * `:json` - parsed JSON data
  * `:string` - response text
 
Raw ring response is returned when the argument is omitted. 

Of course, it is possible to use `appengine-magic.services.url-fetch/fetch` directly 
by its full name if necessary. 

Cloudflare-protected pages could be obtained with a [HtmlUnit](http://htmlunit.sourceforge.net/) client. 
The process looks like the following:
```clojure
(defbackground cf-extractor [feed]
   (let [client (api/htmlunit-client :filter-js ["facebook.com"])] ;; exclude referenced js by facebook 
     (api/solve-cloudflare client "http://example.com")
     (let [text (api/safely-repeat (api/htmlunit-fetch-url client "http://example.com/example"))
           html (when text (api/str->enlive text))
           headlines (when html (api/apply-selectors html feed))]
...
```

### Defining tasks

Macro `deftask` creates a task name that could be used in the "task" YAML config option and
as an argument for the `schedule` or `schedule-periodically` macros.
`deftask*` macro creates a supertask which accepts a vector of tasks 
that will be executed sequentially in the specified order.

Scheduling macros accept UTC-based time values, a task could be scheduled several times.
See the examples for more details.

### Handlers

__feedxvavator2__ handlers allow to create a custom web-api to manipulate your feeds.
`defhandler` macro defines a function that will be called on the 
`https://<your project id>.appspot.com/handler/handler-function-name` URL invocation.
A handler function can accept values of URL query parameters or a raw ring request if 
the `request` symbol is specified instead of the macro argument list.

If handler name is prefixed with the `^:auth` metadata tag, the request will require authorization
by "x-feedxcavator-auth" header, value of which you may find at the settings tab.

### Database primitives

Some high-level database primitives are available in `db/` namespace:

* `(defn find-feed [& {:keys [suffix title]}] ...` - retrieve a feed by its suffix or title using :suffix or :title keyword arguments
* `(defn persist-feed! [feed] ...` - persists the supplied feed object; currently only the modification of :params feed field is allowed, all other changes may be lost
* `(defn fetch-object [uuid] ...` - fetch a Clojure object by the uuid
* `(defn store-object! [uuid object] ...` - store any serializable Clojure collection with the supplied uuid
* `(defn delete-object! [uuid] ...` - delete an object

### Logging

__feedxvavator2__ supports rudimentary logging through the `log/write` function. It accepts 
a string, object or Throwable and prints its content at the "Log" tab. Optionally, one of the
following log levels may be specified as the first argument: 

* `:info`
* `:warning`
* `:error`

### Word filters

It is possible to apply word-filters to omit certain content from the generated feeds.
The name of the word-filter could be specified in the "wordfilter" parameter of the YAML config.
The "default" wordfilter is used if it is left blank. 

Word-filters can contain ordinary words and regular expressions (it is often useful to
specify \b boundary to avoid excessive matching). There is no GUI to manage word-filter contents -
words or regular expressions could be added programmatically from the "Scratch" tab or through
the __feedxcavator2__ REST API. The author uses [these](https://gist.github.com/GChristensen/c4be3bb8508ad13d982c2f57ac302eb8) [UbiquityWE](https://gchristensen.github.io/ubiquitywe)
commands for such purposes.

##### Clojure API to manipulate world-filters

* `(api/add-filter-word word-filter word)` - add a `word` to a `world-flter` defined by name 
* `(api/remove-filter-word word-filter word)` - remove a `word` from a `world-flter` defined by name
* `(api/add-filter-regex word-filter expr)` - add a regex to `world-flter`
* `(api/remove-filter-regex word-filter expr)` - remove a regex from a `world-flter`
* `(api/list-word-filter word-filter)` - list `word-filter` contents 
* `(api/matches-filter? s word-filter)` - check if string is matched by the `word-filter`

### Receiving mail

To process an e-mail using Clojure extractors:
1. Send or forward it to `any_name@<your project id>.appspotmail.com`
2. Create a feed config with the "source" parameter that contains sender e-mail address (case-sensitive).
3. The mail content will be available as `(:e-mail feed)` in the Clojure extractor function that
will be called on its arrival.

### Clojure code examples

```clojure
;; task setup 

(deftask news)
(deftask forums)
(deftask updates)

(deftask* morning-feeds [news forums])

;; all time values are in UTC
(schedule morning-feeds 07 00)
(schedule news 12 00)
(schedule news 17 00)
(schedule forums 15 00)

;; run this task every two hours
(schedule-periodically updates 2)


;; utility functions

;; The parse-page function transforms the page defined by the supplied url  
;; to a list of headlines with the following fields:
;; {
;;  :title "headline title" 
;;  :link "headline url" 
;;  :summary "article summary" 
;;  :image "image url"
;;  :author "author name"  
;; the next field comes from api/apply-selectors and may be useful when, 
;; for example, some values are accessible only through tag attributes
;;  :html <enlive html representation of the headline container element>
;; }
;; the api/apply-selectors function does all the magic
;; the built-in api/parse-html-page function acts exactly as parse-page defined below
(defn parse-page [feed url]
  (when-let [doc-tree (api/fetch-url url :as :html)]
    (api/apply-selectors doc-tree feed)))


;; extractors

;; Fetch some threads from a set of forums of the Bulletin Board. The "params" field 
;; of the feed config should contain an array of numeric ids of the desired forums.
(defbackground bb-extractor [feed]
  (apply concat
         (for [forum (:params feed)]
           (let [forum-url (str (:source feed) forum)
                 ;; stage 1: extract thread URLs from forum pages (the corresponding  
                 ;; selectors should be specified in the feed settings) and filter out 
                 ;; already seen urls
                 threads (api/filter-history forum-url (parse-page forum-url))]                                             
             (when (not (empty? threads))
               ;; stage 2: fetch thread pages and extract rendered html contents  
               ;; of the first posts using enlive
               (for [thread threads]
                 (let [thread-tree (api/fetch-url (:link thread) :as :html)]
                   (log/write (str "visited: " (:link thread)))
                   (assoc thread
                     ;; remove html tags and special character entities
                     :title (api/html-untag (api/html-unescape (:title thread)))
                     ;; get content of the src field of the first <img> tag 
                     :image (:src (:attrs (?1 thread-tree [:.post_text :img])))
                     ;; get rendered html of the first tag with .post_text class 
                     :summary (<* (?1 thread-tree [:.post_text]))))))))))


;; A kitty page uses javascript to lazily load images in its markup. Image URLs are  
;; available through the "data-lazy-load-src" attribute of <img> tags. Because  
;; the src attribute is empty, api/apply-selectors returns headlines without images. 
;; To obtain these images in RSS we must get them from the enlive representation 
;; of a headline container element, provided by the parse-page function defined above. 
;; The resulting headlines are numerically sorted by the id at the end of the post link: 
;; http://kittysite.net/?post=123 (this may be useful, for example, to purge sticky posts).     
(defextractor kittysite-extractor [feed]
    (let [headlines (parse-page (:source feed))
          headlines-with-images (map #(assoc % :image (:data-lazy-load-src 
                                                        (:attrs (?1 (:html %) [:img.lazy])))) 
                                     headlines)]
      (sort-by #(Integer/valueOf (.substring (:link %) (inc (.lastIndexOf (:link %) "=")))) 
               #(compare %2 %1) 
               headlines-with-images)))


;; Extract data from JSON API.
;; "params" field of the feed config should contain the value of the owner_id 
;; query parameter.
(defbackground json-extractor [feed]
    (let [api-token "..."
          api-version "1"
          url (str "https://json.api/method/data.get?owner_id=" (:params feed) 
                   "&access_token=" api-token "&v=" api-version)
          content (api/fetch-url url :as :json)
          posts ((content "response") "items")
          headlines (for [p posts]
                      {
                       :title (p "title")
                       :link (p "url")
                       :summary (p "text")
                       :image (or ((p "attachments") "photo_640")
                                  ((p "attachments") "photo_320"))
                      })]
      (sort-by :link #(compare %2 %1) headlines)))

      
;; Transform another RSS (just turn titles upper-case).
(defextractor rss-extractor [feed]
  (let [doc-tree (api/fetch-url (:source feed) :as :xml)]
    (for [i (?* doc-tree [:item])]
      (let [tag-content #(<t (?1 i [%]))]
         {
          :title (str/upper-case (tag-content :title))
          :link (tag-content :link)
          :summary (tag-content :description)
         }))))
```

### Private Deployment

You may install a private instance of the application at your GAE account (see
releases). Only the account owner will be able to create or manage feeds, but
feed URLs are accessible publicly. To install, create a GAE project with Java
application and fill in its id into the `<application>` tag of the
'appengine-web.xml' configuration file. Execute deploy.py (Python and Google
Cloud SDK with
[installed](https://cloud.google.com/sdk/gcloud/reference/components/install)
app-engine-java component are required).

### License

Copyright (C) 2011-2019 g/christensen (gchristnsn@gmail.com)

Distributed under the Eclipse Public License, the same as Clojure.