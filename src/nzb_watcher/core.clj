(ns nzb-watcher.core
  (:require [clojure.java.jdbc :as jdbc])
  (:require [clojure.tools.cli :refer [parse-opts]])
;  (:require [clojure.xml :as xml]
;            [clojure.zip :as zip])
  (:require [clj-time.core :as t])
  (:require [clj-time.format :as tf])
 ; (:require [clj-time.coerce :refer [to-timestamp]])
  (:require [clojure.string :as str])
  (:require [clojure.tools.logging :as log])
;  (:require [clojure.data :as data])
  (:require [clojure.java.io :as io])
  (:use [clojure.pprint :only (cl-format)])
  (:require [nzb-watcher.db :as db])
  (:require [nzb-watcher.api :as api])
  (:require [nzb-watcher.util :refer [parse-int mkdirp]])
  (:gen-class))

(import '(java.io File))
(import '[java.util.zip ZipEntry ZipOutputStream])

(use '[clojure.java.io :only (reader writer)])
(use '[clojure.java.shell :only [sh]])

(defmacro dlet [bindings & body]
  `(let [~@(mapcat (fn [[n v]]
                     [n `(let [v# ~v]
                           (cl-format true "~w = ~w~%" '~n v#)
                           v#)])
                   (partition 2 bindings))]
         ~@body))

(defmacro dloop [bindings & body]
  `(loop ~bindings
     ~@(map (fn [[n _]]
                 `(cl-format true "~w = ~w~%" '~n ~n))
               (partition 2 bindings))
     ~@body))


(defn check-episode
  "check if episode has been downloaded"
  [include-id episode]
  (db/check-and-insert-fn "select * from episodes where pattern_id = ? and episode = ?" 
                       "insert into episodes (pattern_id,episode) values (?,?)" 
                       "multiple episodes for include_id %d ep %s" include-id episode))

(defn check-file 
  "check if file has been already downloaded"
  [title]
  (db/check-and-insert-fn "select * from loaded_files where name = ?" 
                       "insert into loaded_files (name) values (?)" 
                       "multiple loaded_files for name %d" title))

(def includes-fmt "select i.id,pattern,extra_pattern,rageid,number,target,prefix, c.number as category,thetvdbid 
                   from includes i join categories c on i.category_id = c.id ~:[where gather_only_flag != true~;~]")

(defn get-includes
  "get list of include regexes"
  ([] (get-includes false))
  ([gather-only-flag]
   (db/db-get (cl-format nil includes-fmt gather-only-flag) :category
           #(let [{:keys [pattern rageid id target prefix thetvdbid extra_pattern]} %]
              (vector (re-pattern (str "(?i)" pattern)) rageid id target prefix
                      (re-pattern (str "(?i)" extra_pattern)) thetvdbid)))))

(defn get-excludes
  "get list of exclude regexes"
  []
  (db/db-get "select * from excludes" :pattern_regex #(re-pattern (str "(?i)" (get % :pattern_regex)))))

(defn get-category-dates
  "get last date processed per category"
  []
  (db/db-get "select * from categories" :number #(tf/parse (first (str/split (.toString (get % :last_seen_dt)) #"\.")))))

(defn update-category-latest-dt
  "update category latest seen date"
  [category latest-date]
  (db/with-db db
    (let [fixed-date (tf/unparse (tf/formatter "YYYY-MM-dd hh:mm:ss") latest-date)]
      (try
        (jdbc/execute! db ["update categories set last_seen_dt = ?::timestamp where number = ?" fixed-date category])
        (catch Exception e (println (.getNextException e)))))))

(defn check-backfill-attempt [name]
  (db/check-and-insert-fn "select * from backfill_attempt where name = ?"
                       nil
                      "extra rows found in backfill_attempt for ?" name))

(defn insert-backfill [name]
  (db/with-db db (jdbc/execute! db ["insert into backfill_attempt (name) values (?)" name])))

(defn delete-backfill [name]
  (db/with-db db (jdbc/execute! db ["delete from backfill_attempt where name = ?" name])))

(defn check-episode-loaded [include-id episode]
  (db/check-and-insert-fn "select * from episodes where pattern_id = ? and episode = ?"
                       nil
                      "multiple episodes for include_id %d ep %s" include-id episode))

(defn get-query-string [include-id]
  (db/with-db db (:pattern (first (jdbc/query db ["select pattern from includes where id =?" include-id])))))
  
(defn backfill
  "search back in category for all files matching a include regex.  (assumes tv show).  find most downloaded per season/episode and download it.  record what was downloaded"
  [include-id & {:keys [cat service] :or {cat "5040"}}]
  (let [query-string (get-query-string include-id)]
    (letfn [(get [offs] (api/browse-nzbs cat offs :q query-string :ext true :service service))]
      (let [eps (loop [eps []
                       offs 0]
                  (let [current (get offs)]
                    (if-not (empty? current)
                      (recur (conj eps current) (+ offs 100))
                      (apply concat eps))))
            crap-removed (filter #(re-find (re-pattern (str "(?i)" (str/replace query-string #" " "."))) (% :title)) eps)
            unloaded (remove #(check-episode-loaded include-id (first (% :episode))) crap-removed)
            untried (remove #(check-backfill-attempt (% :title)) unloaded)
            grouped (group-by (comp first :episode) untried)
            chosen (reduce (fn [eps group]
                             (conj eps (first (sort-by (comp parse-int :grabs) > group)))) [] (vals grouped))]
        (cl-format true "chosen count is ~a~%" (count chosen))
        
        (doseq [nzb chosen]
          (let [{:keys [title link]} nzb]
            (insert-backfill title)
            (with-open [out (clojure.java.io/writer (format "nzbs/%s.nzb" title))]
              (api/throttled-get link out))))))))




(defn check-episode-info [season epnum show-id epname]
  (db/check-and-insert-fn "select episode_name from episode_info where show_id = ? and season = ? and episode_num = ? and episode_name = ?" 
                       "insert into episode_info (show_id, season,episode_num, episode_name) values (?,?,?,?)" 
                       "multiple episode_info for %d %d %d %s"
                       season epnum show-id epname))

(defn lookup-episode-name
  "find info for a single episode (and call populate-episode-info if its not found)"
  [thetvdbid season ep & {:keys [lookup] :or {lookup true}}]
  (when thetvdbid
    (db/with-db db 
      (let [row (jdbc/query db ["select episode_name from episode_info where show_id = ? and season = ? and episode_num = ?" thetvdbid season ep])
            name (:episode_name (first row))]
        (or name
            (and lookup
                 (api/populate-episode-info thetvdbid check-episode-info)
                 (lookup-episode-name thetvdbid season ep :lookup false))
           )))))



(defn continue-nzb
  "2nd round of nzb checks"
  [nzb match-id]
  (let [{:keys [episode title link]} nzb
        episode-check (and episode (check-episode match-id (if (string? episode) episode (first episode))))
        file-check (check-file title)]
    (log/info (format "check2: catid=%d ep=%s ec=%s fc=%s %s" match-id episode episode-check file-check title))
    (if-not (or episode-check file-check)
      (with-open [out (clojure.java.io/writer (format "nzbs/%s.nzb" title))]
        (api/throttled-get link out)))))

(defn process-nzb 
  "first round of nzb checks"
  [nzb includes excludes]
  (let [title (:title nzb)
        rageid-str (:rageid nzb)
        date (:pubDate nzb)
        excludes-fail (some identity (map (fn [[pat]] (re-find pat title)) (vals excludes)))
        rageid (and rageid-str (parse-int rageid-str))
        rageid-pass (and rageid (some identity (map (fn [[pat rid id _ _ _]] (and rid (= rid rageid) id)) includes)))
        includes-pass (some identity (map (fn [[pat rid id _ _ xpat]] (and (re-find pat title)
                                                                           (or (not xpat)
                                                                               (re-find xpat title))
                                                                           id)) includes))
        pass (or rageid-pass includes-pass)
        ]
    (log/info (format "check1: %s %s exclude? %s rageid? %s includes? %s" date title excludes-fail rageid-pass includes-pass))
    (if (and (not excludes-fail) pass )
        (continue-nzb nzb pass))))

(defn process-category
  "process nzbs in a category going back to the cutoff date"
  [category includes excludes [cutoff-date]]
  (log/info (cl-format nil "processing category ~a" category))
  (loop [offset 0
         set-latest? true]
    (let [nzbs-batch (api/browse-nzbs category offset)
          sorted-dates (sort t/after? (map #(get % :pubdate) nzbs-batch))
          latest-date (first sorted-dates)
          earliest-date (last sorted-dates)]
      (when set-latest?
        (update-category-latest-dt category latest-date))
      (doseq [nzb nzbs-batch]
        (process-nzb nzb includes excludes))
      (if (t/after? earliest-date cutoff-date )
          (recur (+ offset 100) false))
  )))

(defn prep-dirs []
  (mkdirp "ziparch")
  (mkdirp "nzbs")
  (doseq [file (file-seq (File. "nzbs/"))]
    (if (.isFile file) 
      (io/delete-file file))))

(def drop-folder "/mnt/users/Jock Cooper/Downloads" )  ; location to drop the nzb zip for sabnzbd to grab
(def arch-folder "ziparch")                            ; local archive for zips
(def usenet-folder "/mnt/usenet/complete")             ; where sabnzbd puts finished downloads
(def default-gather-target "tmp2")                     ; where to put media files if no target is specified

(defn logged-action
  "do something and log it, catching errors"
  [doing action-fn]
  (try
    (log/info doing)
    (action-fn)
    true
    (catch Exception e
      (log/error (format "Error %s: %s" doing e)))))

(defn post-nzbs
  "zip the nzbs and move them to the hot folder for the downloader (eg sabnzbd)"
  []
  (let [nzbs (file-seq (File. "nzbs/"))
        nzbs-count (count nzbs)
        zip-name (str "nzbs-" (tf/unparse (tf/formatter "YYYY-MM-dd-hh-mm-ss") (t/now)) ".zip")
        ]
    (if (<= nzbs-count 1)
      (log/info "no files to process")
      (and (logged-action (format "creating zip %s" zip-name)
                          #(with-open [zip (ZipOutputStream. (io/output-stream zip-name))]
                             (doseq [f nzbs :when (.isFile f)]
                               (.putNextEntry zip (ZipEntry. (.getPath f)))
                               (io/copy f zip)
                               (.closeEntry zip))))
           (logged-action (format "copying %s to %s" zip-name arch-folder)
                          #(io/copy (io/file zip-name)
                                    (io/file arch-folder zip-name)))
           (logged-action (format "copying %s to %s" zip-name drop-folder)
                          #(io/copy (io/file zip-name) (io/file drop-folder zip-name)))
           (logged-action (format "deleting %s" zip-name)
                          #(io/delete-file zip-name))
           ))))

(defn watch
  "entry point for watcher"
  []
  (let [includes (get-includes)
        excludes (get-excludes)
        dates (get-category-dates)]
    (log/info "******************** start run watch mode *****************))*")
    (prep-dirs)
    (doseq [[category incls] (seq includes)]
      (process-category category incls excludes (get dates category)))
    (post-nzbs)
    (log/info "******************** end run watch mode *****************))*")
    (db/close-db)
    ))

(defn get-depth [str]
  (count (str/split (.getPath (File. str)) #"\\|/"))
)

(defn gather
  "go thru the downloaded dirs and pull out the biggest media file.  rename it and collect to a target dir"
  []
  (let [dirs (filter #(.isDirectory %) (file-seq (File. usenet-folder)))
        usenet-depth (get-depth usenet-folder)
        includes (get-includes true)]
    (log/info "******************** start run gather mode *****************))*")
    (doseq [dir dirs]
      (doseq [[category incs] (seq includes)]
        (let [dirname (.getName dir)
              dirpath (.getPath dir)
              dir-depth (get-depth dirpath)
              matches (filter (fn [[pattern _ _ _ _ _]] (re-find pattern dirname)) incs)]

          (if (and (> (count matches) 0) (= (- dir-depth usenet-depth) 1))
            (let [[pattern rageid inc-id target prefix _ thetvdbid] (first matches)
                  [_ season episode] (or (and thetvdbid (re-find #"(?i)S([0-9][0-9])E([0-9][0-9])" dirname))
                                         (re-find #"\.([0-9][0-9])\.([0-9][0-9]\.[0-9][0-9])\." dirname))  ; ##.##.## form
                  ep-tag (cl-format nil "S~2,'0dE~2,'0d" season episode)
                  ep-name (and season (lookup-episode-name thetvdbid (parse-int season) (parse-int episode)))
                  biggest (->> dirpath File. file-seq (sort-by #(.length %)) reverse first)
                  biggest-name (.getName biggest)
                  biggest-ext (last (str/split biggest-name #"\."))
                  target-name (if thetvdbid
                                (cl-format nil "~a.S~aE~a.~:[~;~:*~a.~]~a" (or prefix target) season episode ep-name biggest-ext)
                                (str dirname "." biggest-ext))
                  target-dest (io/file usenet-folder (or target default-gather-target))
                  target-path (io/file target-dest target-name)
                  ]
              
              (and (logged-action "(maybe) recording episode load"
                                  #(if (check-episode inc-id ep-tag)
                                     (log/info (cl-format nil "warning: incid ~a episode ~a already in episodes table" inc-id ep-tag))
                                     ))
                   (logged-action "(maybe) delete backfill"
                                  #(delete-backfill dirname))
                   (logged-action (format "(maybe) creating target dir %s" target-dest)
                                  #(if target (mkdirp (.getPath target-dest))))
                   (logged-action (format "shell call to mv %s %s" (.getPath biggest) (.getPath target-path))
                                  #(sh "mv" (.getPath biggest) (.getPath target-path)))
                   (logged-action (format "shell call to rm -fr %s" dirpath)
                                  #(sh "rm" "-fr" dirpath))
                   ))))))
    (log/info "******************** stop run gather mode *****************))*")
    (db/close-db)
    ))

(def cli-args [["-h" "--help" "Print this help"]
               ["-g" "--gather" "Gather mode"]])


(defn -main [& args]
  
  (let [{options :options
         arguments :arguments
         summary :summary
         errors :errors} (parse-opts args cli-args)]
    (if (:help options)
      (println summary))
    (if (:gather options)
      (gather)
      (watch))
    ))
