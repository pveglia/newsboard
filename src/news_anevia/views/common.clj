(ns news-anevia.views.common
  (:use [noir.core :only [defpartial]]
        [hiccup.page :only [include-css html5 include-js]]
        [hiccup.element]))

(defpartial layout [& content]
            (html5
              [:head
               [:title "news-anevia"]
                                        ;(include-css "/css/reset.css")
               [:script {:src "https://login.persona.org/include.js"}]
               [:script {:src "http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js"}]
               (include-js "/js/news.js")
               ]
              [:body
               [:div#wrapper
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
     [:td {:class "data"} (render-submission d)]
     [:td {:id i :class "votes"} v]
     [:td [:button {:type "button"
                :onclick (format "voteUp(\"%s\")" i)} "++"]]]))

(defpartial news-list [items]
  (println items)
  [:table#items {:width "90%" :border "1"}
   (map render-item items)])