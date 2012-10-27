(ns news-anevia.views.welcome
  (:require [news-anevia.views.common :as common]
            [taoensso.carmine :as car]
            [noir.response :as resp])
  (:use [noir.core :only [defpage render]]
        [hiccup.form]
        [hiccup.element]
        [clojure.math.numeric-tower]))

(def p (car/make-conn-pool))
(def s (car/make-conn-spec))
(defmacro wcar [& body] `(car/with-conn p s ~@body))

(def ss-key "news:sortedset")

(defn epoch []
  (int (/ (System/currentTimeMillis) 1000)))

(defn build-data [id]
  )
(defn parseInt [s]
  (Integer. (re-find #"\d+" s)))

(defn redis-get [all]
  (let [end-index (if all -1 20)
        ids (wcar (car/zrevrange ss-key 0 end-index))]
    (println ids)
    (map #(let [res (wcar (car/hgetall %1))]
            (println res)
;            (Thread/sleep 1000)
            (hash-map :data (nth res 1)
                      :date (parseInt (nth res 3))
                      :votes (parseInt (nth res 5 "0"))
                      :item-id %1))
         ids)))


(defpage "/" []
  (common/layout
   [:h1 "Anevia News"]
   [:div#notification ]
   (common/news-list (redis-get nil))
   [:p (link-to "/new" "Submit a new story")]))

(defpage "/new" []
  (common/layout
   [:h1 "Post new content!"]
   (form-to [:post "/new"]
            (label "new-content" "New content:")
            (text-field "new-content")
            (submit-button "post"))))

(defn redis-submit [news]
  (let [id (format "news:%d" (wcar (car/incr "news:id")))
        now (epoch)]
    (wcar (car/hmset id "data" news "date" now "votes" 1))
                                        ; insert into sorted set
    (wcar (car/zadd ss-key 0 id))
    ))

(defn score-fn [item]
  (let [v (:votes item)
        elapsed (/ (- (epoch) (:date item)) 3600)] ; in hours
    (/ (- v 1) (expt (+ 2 elapsed) 1.8))))

(defn update-scores []
  (while true
    (let [items (redis-get true)]
      (println "Updating scores!!")
      (println items)
      (doseq [it items]
        (let [new-score (score-fn it)]
          (println (format "updating score %f for member %s" new-score (:item-id it)))
          (wcar (car/zadd ss-key
                          new-score
                          (:item-id it)))  )

        ))
    (Thread/sleep 5000)))

(defpage [:post "/new"] {:as news}
  (redis-submit (:new-content news))
  (common/layout
   [:p (format "submitted: %s" (common/render-submission (:new-content news)))
    [:p
     (link-to "/new" "New submission") [:br]
     (link-to "/" "Home Page")]]))

(defpage [:post "/vote"] {:keys [item]}
  (let [votes (wcar (car/hincrby item "votes" 1))]
    (resp/xml (format "%d" votes))))