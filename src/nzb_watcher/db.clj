(ns nzb-watcher.db
  (:require [clj-postgresql.core :as pg])
  (:require [clojure.java.jdbc :as jdbc])
  )

(def db (atom nil))

(defn close-db []
  (swap! db (fn [conn] (pg/close! conn) nil)))

(defmacro with-db [db & body]
  `(let [~db (swap! db (fn [x#] (or x# (pg/pool :host "localhost" :user "jockc" :password "password" :dbname "nzb_watcher"))))]
     (try
       ~@body
       )))

(defn check-and-insert-fn
  "return true if row exists;  insert and return false if it does not"
  [chk-stmt insert-stmt error-msg & args]
  (with-db db 
    (let [rows (jdbc/query db (apply vector chk-stmt args))
          row-count (count rows)]
         (case row-count
           0 (do
               (and insert-stmt (jdbc/execute! db (apply vector insert-stmt args)))
               false)
           1 true
           (throw (Exception. (apply format error-msg args)))))))

(defn db-get
  "get data from db and prepare it"
  [query-str hash-key hash-fn]
  (with-db db
    (reduce (fn [hash row]
              (let [key (get row hash-key)
                    current (or (get hash key) [])]
                (assoc hash key (conj current (hash-fn row)))))
            {} (jdbc/query db [query-str]))))
