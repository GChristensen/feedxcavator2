# feedxcavator2

This thing is able to convert anything to RSS with an arbitrary level of 
fine-tuning by using CSS selectors. Because it's designed as a Google App Engine 
application, and few people have GAE accounts, know how to install GAE applications 
and willing to analyze web pages manually to craft necessary CSS selectors, 
probably no one would use it except me, so here is what it looks like:

<a href="https://github.com/GChristensen/feedxcavator/wiki/xcavator.png" target="_blank"><img src="https://github.com/GChristensen/feedxcavator/wiki/xcavator_thumb.png" /></a><a href="https://github.com/GChristensen/feedxcavator/wiki/xcavator.png" target="_blank"><img src="https://github.com/GChristensen/feedxcavator/wiki/xcavator_thumb.png" /></a>

### Supported CSS Subset

Only the following CSS capabilities are supported by __feedxcavator__ CSS 
selectors:

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

