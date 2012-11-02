(ns newsboard.views.welcome
  (:require [newsboard.models.backend :as be]
            [taoensso.carmine :as car]
            [noir.response :as resp]
            [noir.session :as session]
            [clj-http.client :as client]
            [clojure.data.json :as json]
            [clojure.math.numeric-tower :as math])
  (:use [noir.core :only [defpage render defpartial]]
        [hiccup.form]
        [hiccup.element]
        [hiccup.page]))


(def pages [["Home" "/home"] ["Latest" "/latest"]])

(defpartial login []
  (if (session/get "email")
    (concat [(format "Hello, %s! " (session/get "email"))
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
  [:h1 "Newsboard"])

(defpartial layout [route & content]
  (html5
   [:head
    [:title "Newsboard"]
    (include-css "/css/newsboard.css")
    [:script {:src "https://login.persona.org/include.js"}]
    [:script {:src "http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js"}]
    (include-js "/js/news.js")
    ]
   [:body
    [:div#wrapper
     (header route)
     [:div#messages]
     content]]))

(defpartial code [& content]
  [:code content])

(defpartial render-submission [str]
  (if (re-matches #"https?://.*" str)
    (link-to {:class "submission"} str str)
    [:code str]))

(defn tee [item]
  (println item)
  item)

(defn render-date [item]
  (if (contains? item :date)
    (let [diff (- (be/epoch) (get item :date))
          a [[(* 24 (* 60 60)) "day"] [(* 60 60) "hour"] [60 "minute"]]
          fnn' (fn [xs]
                 (let [f (drop-while #(= (first %1) 0) xs)]
                   (if (= 0 (count f)) (list (list 0 "minutes")) f)))
          render (fn [i] (format "%d %s%s" (biginteger (first i)) (second i)
                                 (if (= (first i) 1) "" "s")))]
      (->
       (map (fn [[sec str]] (list (math/floor (/ diff sec)) str)) a)
       (fnn') (first) (render)))
    "??"))

(defpartial render-item [item]
  (let [d (:data item)
        v (:votes item)
        i (:item-id item)]
    [:tr
     [:td {:id i :class "votes"} v]
     [:td [:button {:type "button" :disabled (be/voted? (session/get "email") i)
                    :onclick (format "voteUp(\"%s\", this)" i)} "++"]]
     [:td {:class "data"} (render-submission d)
      [:div#subm (format "submitted by %s, %s ago" (:subm item)
                         (render-date item))]]]))

(defpartial news-list [items]
  ;; (println items)
  [:table#items {:width "90%"}
   [:tr [:th "Votes"] [:th "Vote"] [:th "News"]]
   (map render-item items)])


(defpartial newContent []
  (if (session/get "email")
    (concat [[:h2 "Post new content:"]
             (form-to [:post "/new"]
                      (label "new-content" "New content:")
                      (text-field "new-content")
                      (submit-button "post"))])))

(defpage "/" []
  (resp/redirect "/home"))

(defpage "/home" []
  (layout "Home"
          [:h2 "News Ranking"]
          (news-list (be/redis-get be/ss-key nil))
          (newContent)))

(defpage "/latest" []
  (layout "Latest"
          [:h2 "Latest News"]
          (news-list (be/redis-get be/key-latest nil))
          (newContent)))

(defpage [:post "/new"] {:as news}
  (if (session/get "email")
    (do (be/redis-submit (:new-content news) (session/get "email"))
        (resp/redirect "/"))
    (resp/status 401 "Login required")))

(defpage [:post "/vote"] {:keys [item]}
  (println item)
  (if (session/get "email")
    (let [votes (be/vote (session/get "email") item)]
      (if votes
        (resp/json {:votes (format "%d" votes)})
        (resp/status 405 "Already voted")))
    (resp/status 401 "Login required"))
  )

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
