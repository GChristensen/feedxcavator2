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
of a custom extractor function (which data processing possibilities are limitless) 
defined in Clojure programming language at the "Custom extractors" page. In this case field 
"Custom parameters" may contain string-readable Clojure datum which will be fed to 
`read-string` function and passed to extractor.

There are two types of processing functions which are defined by the `defextractor` and
`defbackground` macros respectively. The first is intended for direct and fast data conversion,
the later should be specified for heavy feeds fetched in the background. Use `defextractor`
for the feeds that need to be extracted during the direct feed URL request (processing time of 
foreground GAE instance requests is limited to one minute), use `defbackground` for feeds that 
should be fetched in background tasks (executed at GAE backend instances with the time limit 
of 10 minutes) defined by `deftask` macro. For feeds with background extractors feed link request 
will return RSS stored earlier by the task (you don't need anyhow bother on this or specify
this in feed settings, all is resolved by DSL), so it will change only after next task execution. 

Background task will also automatically notify the aggregator through pubsubhubbub protocol 
if "Realtime" flag in the feed settings is checked, so aggregators get feed data just after 
extraction.

Extraction DSL example:

```clojure
;; define tasks that will fetch feeds which name contain one of the specified strings
(deftask fetch-daily-feeds ["autofetch"])
(schedule fetch-daily-feeds 13 00)
(schedule fetch-daily-feeds 18 00)

(deftask fetch-periodic-feeds ["tumblr:mass" "goodmorning"])
(schedule fetch-periodic-feeds 13 10)
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

