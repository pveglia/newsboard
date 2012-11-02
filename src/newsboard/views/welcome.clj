(ns newsboard.views.welcome
  (:require [newsboard.models.backend :as be]
            [taoensso.carmine :as car]
            [noir.response :as resp]
            [noir.session :as session]
            [clj-http.client :as client]
            [clojure.data.json :as json])
  (:use [noir.core :only [defpage render defpartial]]
        [hiccup.form]
        [hiccup.element]
        [hiccup.page]))


(def pages [["Home" "/home"] ["Latest" "/latest"]])

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

(defpartial layout [route & content]
  (html5
   [:head
    [:title "news"]
                                        ;(include-css "/css/reset.css")
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

(defpartial render-item [item]
  (let [d (:data item)
        v (:votes item)
        i (:item-id item)]
    [:tr
     [:td {:class "data"} (render-submission d)
      [:div#subm (format "submitted by %s" (:subm item))]]
     [:td {:id i :class "votes"} v]
     [:td [:button {:type "button" :disabled (be/voted? (session/get "email") i)
                :onclick (format "voteUp(\"%s\", this)" i)} "++"]]]))

(defpartial news-list [items]
  (println items)
  [:table#items {:width "90%" :border "1"}
   [:tr [:th "Items"] [:th "votes"] [:th "Vote!"]]
   (map render-item items)])


(defpartial newContent []
  (if (session/get "email")
    (concat [[:h2 "Post new content!"]
             (form-to [:post "/new"]
                      (label "new-content" "New content:")
                      (text-field "new-content")
                      (submit-button "post"))])))

(defpage "/" []
  (resp/redirect "/home"))

(defpage "/home" []
  (layout "Home"
   (news-list (be/redis-get be/ss-key nil))
   (newContent)))

(defpage "/latest" []
  (layout "Latest"
   [:h1 "Latest News"]
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
