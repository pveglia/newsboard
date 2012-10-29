(ns news-anevia.models.backend
  (:require [taoensso.carmine :as car]
            [clojure.math.numeric-tower :as math]
))

;; definitions
(def ss-key "news:sortedset")
(def key-latest "news:latest")

(def p (car/make-conn-pool))
(def s (car/make-conn-spec))

;; helper methods
(defmacro wcar [& body] `(car/with-conn p s ~@body))

(defn epoch []
  (int (/ (System/currentTimeMillis) 1000)))

(defn parseInt [s]
  (Integer. (re-find #"\d+" s)))


;; redis interaction
(defn redis-get [key all]
  (let [end-index (if all -1 20)
        ids (wcar (car/zrevrange key 0 end-index))]
    (map #(let [res (wcar (car/hgetall %1))]
            (hash-map :data (nth res 1)
                      :date (parseInt (nth res 3))
                      :votes (parseInt (nth res 5 "0"))
                      :item-id %1))
         ids)))

(defn redis-submit [news]
  (let [id (format "news:%d" (wcar (car/incr "news:id")))
        now (epoch)]
    (wcar (car/hmset id "data" news "date" now "votes" 1))
                                        ; insert into sorted set
    (wcar (car/zadd ss-key 0 id))
    (wcar (car/zadd key-latest now id))
    ))

(defn score-fn [item]
  (let [v (:votes item)
        elapsed (/ (- (epoch) (:date item)) 3600)] ; in hours
    (/ (- v 1) (math/expt (+ 2 elapsed) 1.8))))

(defn update-scores []
  (while true
    (let [items (redis-get ss-key true)]
      (println "Updating scores!!")
      (doseq [it items]
        (let [new-score (score-fn it)]
          (wcar (car/zadd ss-key
                          new-score
                          (:item-id it))))))
    (Thread/sleep 10000)))

(defn vote [item]
  (wcar (car/hincrby item "votes" 1)))