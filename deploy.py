import os, sys, shutil, subprocess
import xml.etree.ElementTree as ET

WAR = "war"
XMLNS = {"g": "http://appengine.google.com/ns/1.0"}
GCLOUD = os.environ["CLOUD_SDK"] + "/google-cloud-sdk/bin/gcloud.cmd"
APPENGINE_WEB = os.path.join(os.path.dirname(os.path.realpath(__file__)),
                             WAR, "WEB-INF", "appengine-web.xml")
APPENGINE_WEB_BAK = os.path.join(os.path.dirname(os.path.realpath(__file__)),
                                 WAR, "WEB-INF", "appengine-web.bak")


gae_config = ET.parse(APPENGINE_WEB)
root = gae_config.getroot()
version = root.find("g:version", XMLNS).text
app_id = root.find("g:application", XMLNS).text


def deploy():
    subprocess.Popen([shutil.which("gcloud"), "app", "deploy", APPENGINE_WEB,
                      "--version=" + version, "--project=" + app_id, "--quiet"],
                     stdout=sys.stdout, stderr=sys.stderr).communicate()


deploy()

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

deploy()

os.remove(APPENGINE_WEB)
shutil.move(APPENGINE_WEB_BAK, APPENGINE_WEB)

