@set PATH=%INSTALLS%/appengine-sdk/bin;%PATH%
@rem appcfg --oauth2 update ./war & appcfg --oauth2 backends ./war update worker

appcfg --oauth2 update ./war & @ren .\war\WEB-INF\appengine-web.xml appengine-web.1 & @ren .\war\WEB-INF\appengine-web.2 appengine-web.xml & appcfg --oauth2 update ./war & @ren .\war\WEB-INF\appengine-web.xml appengine-web.2 & @ren .\war\WEB-INF\appengine-web.1 appengine-web.xml

@rem @ren .\war\WEB-INF\appengine-web.xml appengine-web.1
@rem @ren .\war\WEB-INF\appengine-web.2 appengine-web.xml

@rem appcfg --oauth2 update ./war

@rem @ren .\war\WEB-INF\appengine-web.xml appengine-web.2
@rem @ren .\war\WEB-INF\appengine-web.1 appengine-web.xml

