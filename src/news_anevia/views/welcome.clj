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
        [clojure.math.numeric-tower]
        [hiccup.core :only [html]]))

(def p (car/make-conn-pool))
(def s (car/make-conn-spec))
(defmacro wcar [& body] `(car/with-conn p s ~@body))

(def ss-key "news:sortedset")
(def key-latest "news:latest")
(def pages [["Home" "/home"] ["Latest" "/latest"]])

(defn epoch []
  (int (/ (System/currentTimeMillis) 1000)))

(defn build-data [id]
  )
(defn parseInt [s]
  (Integer. (re-find #"\d+" s)))

(defn redis-get [key all]
  (let [end-index (if all -1 20)
        ids (wcar (car/zrevrange key 0 end-index))]
    (map #(let [res (wcar (car/hgetall %1))]
            (hash-map :data (nth res 1)
                      :date (parseInt (nth res 3))
                      :votes (parseInt (nth res 5 "0"))
                      :item-id %1))
         ids)))

(defpartial login []
  (if (session/get "email")
    (concat [(format "ciao, %s! " (session/get "email"))
        [:a {:href "#" :id "signout" } "sign out"]
        [:script (format "var currentUser=\"%s\";" (session/get "email"))]])
    (concat [[:a {:href "#" :id "signin" } "sign in"]
             [:script "var currentUser=null;"]])))

(defpartial navigation [route]
  (let [mods (map #(if (= (first %1) route)
                     (format "[%s]" (first %1))
                     (link-to (second %1) (format "[%s]" (first %1))))
                  pages)]
    (concat mods)))

(defpartial header [route]
  [:table {:width "90%"} [:tr [:td (navigation route)]
                          [:td {:align "right"} (login)]]]
  [:h1 "News"])


(defpartial newContent []
   [:h2 "Post new content!"]
   (form-to [:post "/new"]
            (label "new-content" "New content:")
            (text-field "new-content")
            (submit-button "post")))

(defpage "/" []
  (resp/redirect "/home"))

(defpage "/home" []
  (common/layout
   (header "Home")
   (common/news-list (redis-get ss-key nil))
   (newContent)))

(defpage "/latest" []
  (common/layout
   (header "Latest")
   [:h1 "Latest News"]
   (common/news-list (redis-get key-latest nil))
   (newContent)))

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
    (/ (- v 1) (expt (+ 2 elapsed) 1.8))))

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

(defpage [:post "/new"] {:as news}
  (redis-submit (:new-content news))
  (resp/redirect "/"))

(defpage [:post "/vote"] {:keys [item]}
  (println item)
  (let [votes (wcar (car/hincrby item "votes" 1))]
    (resp/json {:votes (format "%d" votes)})))

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