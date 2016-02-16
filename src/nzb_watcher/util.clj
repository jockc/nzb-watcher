(ns nzb-watcher.util
  )


(defn mkdirp [path]
  (let [dir (java.io.File. path)]
    (if (.exists dir)
      true
      (.mkdirs dir))))

(defn parse-int [s]
   (Integer. (re-find  #"\d+" s )))
