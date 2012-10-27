(ns news-anevia.views.welcome
  (:require [news-anevia.views.common :as common]
            [taoensso.carmine :as car]
            [noir.response :as resp]
            [noir.session :as session]
            [clj-http.client :as client]
            [clojure.data.json :as json])
  (:use [noir.core :only [defpage render defpartial]]
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

(defpartial login []
  (if (session/get "email")
    [:p
     (format "ciao, %s" (session/get "email"))
     [:br]
     [:button {:type "button" :id "signout" } "sign out"]
     [:script (format "var currentUser=\"%s\";" (session/get "email"))]]
    [:p
     [:button {:type "button" :id "signin" } "sign in"]
     [:script "var currentUser=null;"]]))

(defpage "/" []
  (common/layout
   (login)
   [:h1 "News"]
;   [:div#notification ]
   (common/news-list (redis-get nil))
   [:h2 "Post new content!"]
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
      (doseq [it items]
        (let [new-score (score-fn it)]
          (wcar (car/zadd ss-key
                          new-score
                          (:item-id it))))))
    (Thread/sleep 10000)))

(defpage [:post "/new"] {:as news}
  (redis-submit (:new-content news))
  (resp/redirect "/"))

(defpage [:post "/vote"] {:keys [item]}
  (let [votes (wcar (car/hincrby item "votes" 1))]
    (resp/xml (format "%d" votes))))

(defpage [:post "/auth/login"] {assertion :assertion}
  (let [reply (client/post "https://verifier.login.persona.org/verify"
                           {:form-params
                            {:audience "http://localhost:8080"
                             :assertion assertion}})
        status (:status reply)
        data (json/read-json (:body reply) true)]
    (if (= status 200)
      (if (= (:status data) "okay")
        (do
          (session/put! "email" (:email data))
          (resp/empty))
        (resp/status 401 (:reason data))))))

(defpage [:post "/auth/logout"] {}
  (session/remove! "email")
  (resp/empty))