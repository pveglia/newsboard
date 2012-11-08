(ns newsboard.models.backend
  (:require [taoensso.carmine :as car]
            [clojure.math.numeric-tower :as math]
))

;; definitions
(def key-score "news:sortedset")
(def key-latest "news:latest")

(defn voter-key [id]
  (format "voters:%s" id))

;; carmine helper functions
(def p (car/make-conn-pool))
(def s (car/make-conn-spec))

;; helper methods
(defmacro wcar [& body] `(car/with-conn p s ~@body))

(defn epoch []
  "return current time in epoch"
  (int (/ (System/currentTimeMillis) 1000)))

(defn parseInt [s]
  (Integer. (re-find #"\d+" s)))

(defn score-fn [votes date]
  "compute score from number of votes and date. It is
a much more simplified version of hacker news algorithm."
  (let [elapsed (/ (- (epoch) date) 3600)] ; in hours
    (/ votes (math/expt (+ 2 elapsed) 1.8))))

(defn get-score [item]
  "compute score of an item."
  (let [v (:votes item)
        date (:date item)]
    (score-fn v date)))

(defn get-item [id]
  (let [item (conversion (redis2map (wcar (car/hgetall* id))) id)]
    item))

(defn conversion [item id]
  (-> item
      (update-in [:date] #(parseInt %1))
      (update-in [:votes] #(parseInt %1))
      (conj {:item-id id})))

(defn redis2map [item]
  (into {} (for [[k v] item]
             [(keyword k) v])))

(defn redis-get [key n]
  "Return a list of items (maps) ordered as in redis sorted set
at `key`. Items are built from values contained in redis hashes."
  (let [end-index n
        ids (wcar (car/zrevrange key 0 end-index))
        items (map #(get-item %1) ids)]
    items))

(defn redis-submit [news title subm]
  "add a new item into redis backend. Id is created by an incrementing
value stored in key `news:id`."
  (let [id (format "news:%d" (wcar (car/incr "news:id")))
        now (epoch)]
    (wcar (car/hmset id "data" news
                     "date" now
                     "votes" 1
                     "subm" subm
                     "title" title) ; insert into sorted set
          (car/zadd key-score (score-fn 1 now) id)
          (car/zadd key-latest now id))))

(defn redis-remove [id]
  "remove a news item from backend (sorted sets, hash and votes)."
   (wcar (car/zrem key-score id)
         (car/zrem key-latest id)
         (car/del (format "voters:%s" id))
         (car/del id)))

(defn update-scores []
  "This function is called periodically to update news scores."
  (while true
    (let [items (redis-get key-score -1)]
      (doseq [it items]
        (let [new-score (get-score it)]
          (wcar (car/zadd key-score
                          new-score
                          (:item-id it))))))
    (Thread/sleep 10000)))

(defn voted? [voter item]
  "return true if voter has voted for item, false otherwise."
  (let [res (= 1 (wcar (car/sismember (voter-key item) voter)))]
    res))

(defn vote [voter item]
  "adds a vote from voter for item. Votes are implemented with
redis sets, each item has a set of voters."
  (if (not (voted? voter item))
      (let [resp (wcar (car/sadd (voter-key item) voter)
                       (car/hincrby item "votes" 1))]
        (last resp))
    false))

(defn build-tree [rest node]
  (let [children? (fn [item] (= (:parent item) (:id node)))
        children (filter #(children? %1) rest)
        others (filter #(complement (children? %1)) rest)]
    {:id (:id node)
     :children (map (partial build others) children)}))

(defn get-comment [cid]
  (let [item (redis2map (wcar (car/hgetall* cid)))]
    (println item)
    item))

(defn get-comments [id]
  (let [cids (wcar car/smembers (format "comments:%s" id))
        comments (map #(get-comment %1) cids)
        trees (build-tree comments {:id ""})]
    trees))