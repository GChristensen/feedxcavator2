import os, sys, shutil, subprocess
import xml.etree.ElementTree as ET

WAR = "war"
XMLNS = {"g": "http://appengine.google.com/ns/1.0"}


def mkabscfg(file):
    return os.path.join(os.path.dirname(os.path.realpath(__file__)), WAR, "WEB-INF", file)


APPENGINE_WEB = mkabscfg("appengine-web.xml")
APPENGINE_WEB_BAK = mkabscfg("appengine-web.bak")


gae_config = ET.parse(APPENGINE_WEB)
root = gae_config.getroot()
version = root.find("g:version", XMLNS).text
app_id = root.find("g:application", XMLNS).text


def deploy(file):
    subprocess.Popen([shutil.which("gcloud.cmd"), "app", "deploy", file,
                      "--version=" + version, "--project=" + app_id, "--quiet"],
                     stdout=sys.stdout, stderr=sys.stderr).communicate()


deploy(APPENGINE_WEB)

file = open(APPENGINE_WEB, 'r', encoding="utf-8")
xml = file.read()
xml = xml.replace("<!-- 'default' service -->", "<!-- 'default' service --><!--")\
    .replace("<!-- end of 'default' service -->", "--><!-- end of 'default' service -->")\
    .replace("<!-- 'worker' service --><!--", "<!-- 'worker' service -->") \
    .replace("--><!-- end of 'worker' service -->", "<!-- end of 'worker' service -->")
file.close()

shutil.move(APPENGINE_WEB, APPENGINE_WEB_BAK)
file = open(APPENGINE_WEB, 'w', encoding="utf-8")
file.write(xml)
file.close()

deploy(APPENGINE_WEB)

os.remove(APPENGINE_WEB)
shutil.move(APPENGINE_WEB_BAK, APPENGINE_WEB)

deploy(mkabscfg("queue.yaml"))
deploy(mkabscfg("cron.yaml"))
