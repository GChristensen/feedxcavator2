# feedxcavator

This is the thing I always dreamed about, it is able to convert anything to RSS
with an arbitrary level of fine-tuning by using CSS selectors. Because it's 
designed as a Google App Engine application, and few people have GAE accounts, 
know how to install GAE applications and want to analyze web pages manually to 
extract necessary data using CSS, probably no one would use it except me, 
so here is what it looks like:

<a href="https://github.com/GChristensen/feedxcavator/wiki/xcavator.png" target="_blank"><img src="https://github.com/GChristensen/feedxcavator/wiki/xcavator_thumb.png" /></a>
 
I use it primarily to get updates about new releases of the shows by specific
release groups at public torrent trackers and also for local newspapers and cinema news.

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

It's possible to create custom data extractors (called 'excavators' here)
when using a private deployment if additional processing logic is necessary.
See __DefaultExcavator__ in the 'excavation.clj' file for an example.

###Hacking on the Application Source Code

* If you have used 'lein appengine-prepare' command to build a binary distribution,
before loading and compiling the application in REPL for interactive development 
you need to clean the distribution with Leiningen's 'lein clean' command 
and also comment out the following directive in project.clj: 
:aot [feedxcavator.app_servlet ...]. 
Do not forget to uncomment it when using 'appengine-prepare' command again 
(these issues are related to appengine-magic library).
* Compojure does not work on GAE as is, so to be able to deploy custom verson 
of the application on GAE you may need to comment out a couple of lines related 
to multipart params in compojure's handler.clj file after you executed 
'lein deps' command, starting, probably, from the maven local cache.

### License

Copyright (C) 2011 g/christensen (gchristnsn@gmail.com)

Distributed under the Eclipse Public License, the same as Clojure.

