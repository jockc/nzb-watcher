(defproject nzb-watcher "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.github.kyleburton/clj-xpath "1.4.4"]
                 [org.clojure/java.jdbc "0.3.7"]
                 [org.clojure/data.zip "0.1.1"]
                 [clj-postgresql "0.4.0"]
                 [org.clojure/tools.cli "0.3.3"]
                 [clj-time/clj-time "0.11.0"]
                 [spyscope "0.1.5"]
                 [org.clojure/tools.logging "0.3.1"]
                 [org.slf4j/slf4j-log4j12 "1.7.1"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail
                                                    javax.jms/jms
                                                    com.sun.jmdk/jmxtools
                                                    com.sun.jmx/jmxri]]
                 ]
  :keep-non-project-classes true
  :main nzb-watcher.core
  :target-path "target/%s"
  :profiles {:dev {:plugins [[cider/cider-nrepl "0.9.1"]]}}
  )
