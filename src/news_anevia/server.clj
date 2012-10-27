(ns news-anevia.server
  (:require [noir.server :as server]
            [news-anevia.views.welcome :as welcome]))

(server/load-views-ns 'news-anevia.views)


(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))
        score-updater (Thread. #(welcome/update-scores))]
    (.start score-updater)
    (server/start port {:mode mode
                        :ns 'news-anevia})))
