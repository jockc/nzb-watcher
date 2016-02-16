(ns nzb-watcher.api
  (:require [clojure.java.io :as io])
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip])
  (:require [clojure.string :as str])
  (:require [clj-time.core :as t])
  (:require [clojure.tools.logging :as log])
  (:use [clojure.data.zip.xml :only (attr= attr text xml-> )])
  (:use [clojure.pprint :only (cl-format)])
  (:require [nzb-watcher.util :refer [parse-int mkdirp]])
  (:require [clj-time.format :as tf]))


(def last-call (atom (t/date-time 1990)))
(def wait-ms 2000)

(defn throttled-action
  "perform and action but only once per wait-ms.  this is needed because some APIs (eg nzb.su) choke if you request too quickly "
  [action]
  (let [interval (t/in-millis (t/interval @last-call (t/now)))]
    (if (< interval wait-ms)
      (Thread/sleep (- wait-ms interval)))
    (swap! last-call (fn [x] (t/now)))
    (try (action) (catch Exception e))
  ))

(defn throttled-api [uri]
  (throttled-action (fn [] (xml/parse uri))))

(defn throttled-get [uri out]
  (throttled-action (fn [] (with-open [in (io/input-stream uri)]
                      (io/copy in out)))))

(def %thetvdb-api-key (atom ""))
(defn thetvdb-api-key []
  (if (= @%thetvdb-api-key "")
    (swap! %thetvdb-api-key (fn [ignored] (str/trim (slurp "resources/thetvdbapi.txt")))))
  @%thetvdb-api-key)
  
(defn lookup-series-id 
  "ask thetvdb.com what the id for a given show is"
  [name]
  (let [theurl (format "http://thetvdb.com/api/GetSeries.php?seriesname=%s" name)
        theresult (try (xml/parse theurl) (catch Exception e))]
    (if theresult
      (let [thezip (zip/xml-zip theresult )]
        (map (fn [show id] (list show id))
             (xml-> thezip :Series :SeriesName text)
             (xml-> thezip :Series :seriesid text))))))

(defn get-episode-number
  "parse the season and episode from a filename"
  [season episode title]
  (if season
    (list (str season episode) (parse-int season) (parse-int episode))
    (let [ep (re-find #".*(E[0-9]+|[0-9][0-9]\.[0-9][0-9]\.[0-9][0-9]).*" title)]
      (and ep (nth ep 1)))))

(defn browse-nzbs
  "grab a chunk of the latest nzbs in a category starting at offset"
  [category offset & {:keys [q ext]}]
  (let [api-key (str/trim (slurp "resources/api.txt"))
        theurl (cl-format nil "https://api.nzb.su/api?t=search&cat=~a&apikey=~a&o=xml&extended=1&offset=~d&limit=100~:[~;~:*&q=~a~]~:[~;&extended=1~]"
                          category api-key offset (and q (str/replace q #" " "%20")) ext)
        theresult (throttled-api theurl)
        thezip (zip/xml-zip theresult )
        result (for [item (xml-> thezip :channel :item)]
                 (into {} (map #(cond
                                  (= (type %) java.lang.String) [(keyword %) (first (xml-> item :newznab:attr (attr= :name %) (attr :value)))]
                                  (= (type %) clojure.lang.Keyword) [% (first (xml-> item % text))])
                               [:title :link :pubDate "rageid" "season" "episode" "grabs"])))]
    (log/info (cl-format nil "cat ~a ~a items" category (count result)))
    (map (fn [hash] (let [{:keys [season episode title pubDate]} hash]
                      (assoc hash :episode (get-episode-number season episode title) :pubdate (tf/parse pubDate)))) result)))


(defn populate-episode-info
  "get all episode info from thetvdb.com for a given show (by id) and put into the episode info table"
  [thetvdbid update-fn]
  (let [theurl (format "http://thetvdb.com/api/apikey=%s/series/%d/all/en.xml" (thetvdb-api-key) thetvdbid)
        theresult (try (xml/parse theurl) (catch Exception e ))]
    (if theresult
      (let [thezip (zip/xml-zip theresult)]
        (doall (map update-fn 
                    (iterate identity thetvdbid)
                    (map parse-int (xml-> thezip :Episode :Combined_season text))
                    (map parse-int (xml-> thezip :Episode :Combined_episodenumber text))
                    (xml-> thezip :Episode :EpisodeName text)))))))