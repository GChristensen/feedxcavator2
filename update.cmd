@set PATH=%PATH%;%INSTALLS%/appengine-sdk/bin
@rem appcfg --oauth2 update ./war & appcfg --oauth2 backends ./war update worker

@echo. 
@echo !!! WARNING !!!
@echo. 
@echo Currently you need to manually deploy the 'default' and 'worker' services by 
@echo (un)commenting the correspoinding lines at war/WEB-INF/appengine-web.xml 
@echo and running update.cmd
@echo It is also necessary to delete 'worker' application version at the 
@echo AppEngine web-console if the app was deployed in backend version.
@echo. 
@echo. 

appcfg --oauth2 update ./war