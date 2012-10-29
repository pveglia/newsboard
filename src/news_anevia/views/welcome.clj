(ns news-anevia.views.welcome
  (:require [news-anevia.views.common :as common]
            [news-anevia.models.backend :as be]
            [taoensso.carmine :as car]
            [noir.response :as resp]
            [noir.session :as session]
            [clj-http.client :as client]
            [clojure.data.json :as json])
  (:use [noir.core :only [defpage render defpartial]]
        [hiccup.form]
        [hiccup.element]
        [hiccup.core :only [html]]))


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
   (common/news-list (be/redis-get be/ss-key nil))
   (newContent)))

(defpage "/latest" []
  (common/layout
   (header "Latest")
   [:h1 "Latest News"]
   (common/news-list (be/redis-get be/key-latest nil))
   (newContent)))

(defpage [:post "/new"] {:as news}
  (be/redis-submit (:new-content news))
  (resp/redirect "/"))

(defpage [:post "/vote"] {:keys [item]}
  (println item)
  (let [votes (be/vote item)]
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
