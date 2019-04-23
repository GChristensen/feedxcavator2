@set PATH=%INSTALLS%/appengine-sdk/bin;%PATH%

appcfg --oauth2 update ./war & @ren .\war\WEB-INF\appengine-web.xml appengine-web.1 & @ren .\war\WEB-INF\appengine-web.2 appengine-web.xml & appcfg --oauth2 update ./war & @ren .\war\WEB-INF\appengine-web.xml appengine-web.2 & @ren .\war\WEB-INF\appengine-web.1 appengine-web.xml
