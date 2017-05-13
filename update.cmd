set PATH=%PATH%;%INSTALLS%/appengine-sdk/bin
appcfg --oauth2 update ./war & appcfg --oauth2 backends ./war update worker