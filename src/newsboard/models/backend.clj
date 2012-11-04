(ns newsboard.models.backend
  (:require [taoensso.carmine :as car]
            [clojure.math.numeric-tower :as math]
))

;; definitions
(def ss-key "news:sortedset")
(def key-latest "news:latest")

(defn voter-key [id]
  (format "voters:%s" id))

(def p (car/make-conn-pool))
(def s (car/make-conn-spec))

;; helper methods
(defmacro wcar [& body] `(car/with-conn p s ~@body))

(defn epoch []
  (int (/ (System/currentTimeMillis) 1000)))

(defn parseInt [s]
  (Integer. (re-find #"\d+" s)))

(defn score-fn [votes date]
  (let [elapsed (/ (- (epoch) date) 3600)] ; in hours
    (/ votes (math/expt (+ 2 elapsed) 1.8))))

(defn get-score [item]
  (let [v (:votes item)
        date (:date item)]
    (score-fn v date)))

;; redis interaction
(defn redis-get [key n]
  (let [end-index n
        ids (wcar (car/zrevrange key 0 end-index))]
    (map #(let [res (wcar (car/hgetall %1))]
            (hash-map :data (nth res 1)
                      :date (parseInt (nth res 3))
                      :votes (parseInt (nth res 5 "0"))
                      :item-id %1
                      :subm (nth res 7 "Unknown")))
         ids)))

(defn redis-submit [news subm]
  (let [id (format "news:%d" (wcar (car/incr "news:id")))
        now (epoch)]
    (wcar (car/hmset id "data" news
                     "date" now
                     "votes" 1
                     "subm" subm) ; insert into sorted set
          (car/zadd key-score (score-fn 1 now) id)
          (car/zadd key-latest now id))))

(defn redis-remove [id]
   (wcar (car/zrem ss-key id)
         (car/zrem key-latest id)
         (car/del (format "voters:%s" id))
         (car/del id)))

(defn update-scores []
  (while true
    (let [items (redis-get ss-key -1)]
      (doseq [it items]
        (let [new-score (get-score it)]
          (wcar (car/zadd ss-key
                          new-score
                          (:item-id it))))))
    (Thread/sleep 10000)))

(defn voted? [voter item]
  (let [res (= 1 (wcar (car/sismember (voter-key item) voter)))]
    res))

(defn vote [voter item]
  (if (not (voted? voter item))
      (let [resp (wcar (car/sadd (voter-key item) voter)
                       (car/hincrby item "votes" 1))]
        (last resp))
    false))
