# feedxcavator2

This thing is able to convert anything to RSS with an arbitrary level of 
fine-tuning by using CSS selectors. It's also possible to program an arbitrary
feed-extraction process in a simple Clojure-based DSL directly in the web ui.
Because it's designed as a Google App Engine application, it's troublesome to 
create a GAE account, upload GAE applications and analyze web pages manually to 
craft necessary CSS selectors, probably no one would use the app except me, 
so here is what it looks like:


<a href="https://github.com/GChristensen/feedxcavator2/blob/master/img/xcavator.png" target="_blank"><img src="https://github.com/GChristensen/feedxcavator2/blob/master/img/xcavator_thumb.png" /></a>&nbsp;&nbsp;<a href="https://github.com/GChristensen/feedxcavator2/blob/master/img/custom.png" target="_blank"><img src="https://github.com/GChristensen/feedxcavator2/blob/master/img/custom_thumb.png" /></a>


tl&dr: this is not an RSS aggregator, but an RSS producer that is able to trasform any site
to RSS.

### Supported CSS Subset

Only the following CSS capabilities are currently supported by __feedxcavator__:

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
library for HTML processing and internally converts all the CSS selectors into
[enlive selectors](http://enlive.cgrand.net/syntax.html).
The conversion routine is very straightforward and not so intelligent, so it's 
better to use enlive selectors in complex cases if css selectors do not work. 
Although, it should successfully deal with relatively simple hierarchical 
selectors, which should be enough in the majority of cases.
__feedxcavator__ will assume that elnive selectors are used if the selector 
string is wrapped in square brackets (e.g. [:div#some-id :> :h1.some-class 
:> :a]) and will not try to convert them.

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

