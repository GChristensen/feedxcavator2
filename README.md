# feedxcavator2

This thing is able to convert anything to RSS with an arbitrary level of 
fine-tuning by using CSS selectors. There is a built-in [pubsubhubbub](https://en.wikipedia.org/wiki/PubSubHubbub)
server, and it's also possible to program an arbitrary
feed-extraction process in a simple Clojure-based DSL directly through the web ui.
Because it's designed as a Google App Engine application, it's troublesome to 
create a GAE account, upload GAE applications and analyze web pages manually to 
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

__feedxcavator__ uses [enlive](https://github.com/cgrand/enlive#readme)
library for HTML processing and internally converts all CSS selectors into
[enlive selectors](http://enlive.cgrand.net/syntax.html).
The conversion routine is quite straightforward, so it's 
better to use enlive selectors in complex cases if css selectors do not work. 
__feedxcavator__ will assume that elnive selectors are used if the selector 
string is wrapped in square brackets (e.g. [:div#some-id :> :h1.some-class 
:> :a]) and will not try to convert them.
Although, regular CSS selector should successfully deal with relatively simple hierarchical 
queries, which should be enough in the majority of cases.


### Private Deployment

You may [install](http://code.google.com/appengine/docs/java/gettingstarted/uploading.html) 
a private [instance](https://github.com/GChristensen/feedxcavator/downloads)
of the application on your GAE account, and only the account owner will be able 
to create or manage feeds (but still will be able to share feed links). The only 
thing you need to do is to fill in application id in the 'appengine-web.xml' file.

It's possible to create custom data extractors in clojure when using a private 
deployment if additional processing logic is necessary.

### License

Copyright (C) 2011 g/christensen (gchristnsn@gmail.com)

Distributed under the Eclipse Public License, the same as Clojure.

