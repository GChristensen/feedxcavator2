(ns feedxcavator.code-api
  (:require [feedxcavator.core :as core :exclude [safely-repeat safely-repeat3]]
            [feedxcavator.extraction :as extraction]
            [feedxcavator.htmlunit :as htmlunit]
            [feedxcavator.db :as db]
            [net.cgrand.enlive-html :as enlive]
            [clojure.pprint :as pprint]))

(def production? core/production?)

(def permission-denied core/permission-denied)
(def page-not-found core/page-not-found)
(def internal-server-error core/internal-server-error)
(def web-page core/web-page)
(def attachment-page core/attachment-page)
(def json-page core/json-page)
(def rss-page core/rss-page)
(def html-page core/html-page)
(def text-page core/text-page)
(def redirect-to core/text-page)
(def redirect-url core/redirect-url)
(def redirect-url-b64 core/redirect-url-b64)
(def timestamp core/timestamp)
(def distinct-by core/distinct-by)
(def regex-escape core/regex-escape)
(def to-hex-string core/to-hex-string)
(def md5 core/md5)
(def sha1-sign core/sha1-sign)
(def generate-uuid core/generate-uuid)
(def generate-random core/generate-random)
(def url-encode-utf8 core/url-encode-utf8)
(def url-safe-base64enc core/url-safe-base64enc)
(def url-safe-base64dec core/url-safe-base64dec)
(def base64enc core/base64enc)
(def base64dec core/base64dec)
(def base64dec->bytes core/base64dec->bytes)
(def html-render core/html-render)
(def html-unescape core/html-unescape)
(def html-sanitize core/html-sanitize)
(def html-untag core/html-untag)
(def xml-format core/xml-format)
(def fix-relative-url core/fix-relative-url)
(def find-header core/find-header)
(def str->enlive core/str->enlive)
(def resp->enlive core/resp->enlive)
(def resp->enlive-xml core/resp->enlive-xml)
(def resp->str core/resp->str)
(def fetch-url core/fetch-url)
(def get-last-http-response core/get-last-http-response)
(def get-last-http-error core/get-last-http-error)
(def get-last-network-error core/get-last-network-error)
(def get-last-conversion-error core/get-last-conversion-error)
(def get-feed-url core/get-feed-url)
(def get-app-host core/get-app-host)

(def extra db/extra)

(def apply-selectors extraction/apply-selectors)
(def produce-feed-output extraction/produce-feed-output)
(def parse-html-page extraction/parse-html-page)
(def parse-page-range extraction/parse-page-range)
(def parse-pages extraction/parse-pages)
(def filter-history extraction/filter-history)
(def filter-history! extraction/filter-history!)
(def filter-history-by-guid! extraction/filter-history-by-guid!)
(def filter-content extraction/filter-content)
(def filter-headlines extraction/filter-headlines)
(def add-filter-word extraction/add-filter-word)
(def remove-filter-word extraction/remove-filter-word)
(def add-filter-regex extraction/add-filter-regex)
(def remove-filter-regex extraction/remove-filter-regex)

(def htmlunit-client htmlunit/htmlunit-client)
(def solve-cloudflare htmlunit/solve-cloudflare)
(def htmlunit-fetch-url htmlunit/htmlunit-fetch-url)

(def select enlive/select)

(def pprint pprint/pprint)

(defmacro safely-repeat [statement]
  `(feedxcavator.core/safely-repeat ~statement))

(defmacro safely-repeat3 [statement]
  `(feedxcavator.core/safely-repeat3 ~statement))

