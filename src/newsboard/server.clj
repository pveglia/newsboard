(ns newsboard.server
  (:require [noir.server :as server]
            [newsboard.views.welcome :as welcome]
            [newsboard.models.backend :as be]))

(server/load-views-ns 'newsboard.views)


(defn -main [& m]
  (let [mode (keyword (or (first m) :dev))
        port (Integer. (get (System/getenv) "PORT" "8080"))
        score-updater (Thread. #(be/update-scores))]
    (.start score-updater)
    (server/start port {:mode mode
                        :ns 'newsboard})))
