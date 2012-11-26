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
  ; "notify user if he is logged in."
  (if (session/get "email")
    (concat [[:button#signout
              {:type "button" :class "btn" :data-toggle "button"}
              "sign out"]
             [:script (format "var currentUser=\"%s\";"
                              (session/get "email"))]])
    (concat [[:button#signin {:type "button" :class "btn"
                       :data-toggle "button"} "sign in"]
             [:script "var currentUser=null;"]])))

(defpartial navigation [route]
  ; "Outputs navigation link on top of the page."
  (let [mods (map #(if (= (first %1) route)
                     (format "[%s]" (first %1))
                     (link-to (second %1) (format "[%s]" (first %1))))
                  pages)]
    (concat mods)))

(defn get-active [route link]
  (println route link)
  (if (= route link)
    {:class "active"}
    nil))

(defpartial header [route]
  ; "Output page header (navigation + login)."
  (list
   [:div.span10
    [:div.navbar
     [:div.navbar-inner
      [:a {:class "brand" :href "#"} "NewsBoard"]
      [:ul.nav
       [:li (get-active route "/home") (link-to "/home" "Home")]
       [:li (get-active route "/latest") (link-to "/latest" "Latest")]
       [:li (get-active route "/comments") (link-to "#" "Comments")]]
      ]]]
   [:div.span2 (login)])
  )

; [:table {:width "100%"}
;       [:tr [:td (navigation route)]
;        [:td {:align "right"} (login)]]]

(defpartial layout [route & content]
   (html5
   [:head
    [:title "Newsboard"]
    ; (include-css "/css/newsboard.css")
    [:link {:href "/css/bootstrap.css" :rel "stylesheet" :media "screen"}]
;    (include-css "/css/bootstrap.css")
    [:script {:src "https://login.persona.org/include.js"}]
    [:script {:src "http://ajax.googleapis.com/ajax/libs/jquery/1.8.2/jquery.min.js"}]
    (include-js "/js/news.js")
    (include-js "/js/bootstrap.js")
    ]
   [:body
    [:div {:class "container-fluid"}
     (header route)
     [:div.row
      [:div#messages]]
     content]]))

(defpartial code [& content]
  ; "Wraps content into a code tag."
  [:code content])

(defpartial render-submission [item]
  ;  "Given a submission, renders it depending on its type:
  ; if the regexp matches items is a link and it is embedded
  ; into an `a` tag."
  (if (re-matches #"https?://.*" (:data item))
    (link-to {:class "submission"} (:data item) [:strong (:title item)])
    (list [:strong (:title item)] "&nbsp" [:em (:data item)])))

(defn tee [item]
  "debug function for threading macro"
  (println item)
  item)

(defn render-date [item]
  "Outputs a `time ago` part of item."
  (if (contains? item :date)
    (let [diff (- (be/epoch) (get item :date))
          a [[(* 24 (* 60 60)) "day"] [(* 60 60) "hour"] [60 "minute"]]
          fnn' (fn [xs]
                 (let [f (drop-while #(= (first %1) 0) xs)]
                   (if (= 0 (count f)) (list (list 0 "minute")) f)))
          render (fn [i] (format "%d %s%s" (biginteger (first i)) (second i)
                                 (if (= (first i) 1) "" "s")))]
      (->
       (map (fn [[sec str]] (list (math/floor (/ diff sec)) str)) a)
       (fnn') (first) (render)))
    "??"))

(defpartial render-item [item]
  ; "Outputs a table line for each element."
  (let [d (:data item)
        v (:votes item)
        i (:item-id item)
        s (:subm item)]
    [:div.row {:id i}
     [:div.span10
      [:div {:class "item"} (render-submission item)]
      [:div {:class "meta"} (format "submitted by %s, %s ago. " s
                                    (render-date item))
       (let [ncomments (be/count-comments i)]
         (link-to (format "/comments/%s" i)
                  (format "%d comment%s" ncomments (if (not= ncomments 1)
                                                     "s" ""))))
       " "
       (when (and (session/get "email")
                  (= (session/get "email") s))
         (link-to {:onclick (format "deleteItem(\"%s\");" i)}
                  "#" "delete"))]]
     [:div.span1 v]
     [:div.span1
      [:button {:type "button" :disabled (be/voted? (session/get "email") i)
                :onclick (format "voteUp(\"%s\", this)" i)} "++"]]]))

(defpartial news-list [items]
  ; "Get item list and renders them."
   (map render-item items))


(defpartial newContent []
  ; "Renders part of page used to type a new item."
  (if (session/get "email")
    (concat [[:h2 "Post new content:"]
             (form-to {:class "form-inline"} [:post "/new"]
                      (text-field "title" "News Title")
                      (text-field "new-content" "News Content")
                      (submit-button "post"))])))

(defpage "/" []
  ; "home page just redirects to /home."
  (resp/redirect "/home"))

(defmacro nocache [fname & args]
  `(resp/set-headers {"Cache-Control" "max-age=0; private"}
                     (~fname ~@args)))

(defpage "/home" []
  ; "Home page, shows the list of content ordered by score
  ; and a form to input new items."
  (nocache layout "/home"
           [:div.row
            [:div.span6 [:h3 "News Ranking"]]]
           (news-list (be/redis-get be/key-score 19))
           (newContent)))

(defpage "/latest" []
  ; "List content in reverse chronological order."
  (nocache layout "/latest"
          [:h2 "Latest News"]
          (news-list (be/redis-get be/key-latest 19))
          (newContent)))

(defpage [:post "/new"] {:as news}
  ; "End point to create a new content."
  (if (session/get "email")
    (do (be/redis-submit (:new-content news)
                         (:title news)
                         (session/get "email"))
        (resp/redirect "/"))
    (resp/status 401 "Login required")))

(defpage [:post "/vote"] {:keys [item]}
  ; "End point to vote for a content."
  (if (session/get "email")
    (let [votes (be/vote (session/get "email") item)]
      (if votes
        (resp/json {:votes (format "%d" votes)})
        (resp/status 405 "Already voted")))
    (resp/status 401 "Login required")))

(defpage [:post "/auth/login"] {assertion :assertion}
  ; "BrowserId endpoint, it forwards the assertion to persona verifier."
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
  "Logout"
  (session/remove! "email")
  (resp/empty))

(defpage [:delete "/delete/:id"] {id :id}
;  "Delete a content."
  (be/redis-remove id)
  (resp/empty))

(defpartial commentform [id parent]
  (when (session/get "email")
    (form-to [:post (format "/comments/%s" id)]
             (label "comment" "Comment:")
             (text-area "comment")
             (hidden-field "parent" parent)
             (submit-button "post"))))

(defpartial render-comments [level comments]
  (for [node comments]
    [:div {:id (:id node)
           :style "margin-left: 25px"
           :class "comment"}
     [:span#comment (:comment node)]
     [:br]
     [:span#meta (format "by %s, %s ago." (:subm node) (render-date node))]
     [:span#commands
      " "
      (link-to
       {:onclick (format "showCommentForm(\"%s\");" (:id node))}
       "#" "reply")
;      (when (= (session/get "email") (:subm node))
;        (list " or " (link-to
;          {:onclick (format "deleteComment(\"%s\");" (:id node))}
;          "#" "delete")))
     ]
     [:div {:id (format "form:%s" (:id node))
            :style "display: none;"}
      (commentform (:newsid node) (:id node))]
     (render-comments (inc level) (:children node))]))

(defpage "/comments/:id" {id :id}
  (let [item (be/get-item id)
        comments (be/get-comments id)
        ]
    (nocache layout "/comments"
            [:div#ctitle "Comments to: " [:span (render-submission item)]]
            [:div#comments (render-comments 0 (:children comments))]
            [:h3 "Insert new comment"]
            (commentform id ""))))

(defpage [:post "/comments/:id"] [:as post]
  (println post)
  (println "XXXXXX" (:id post))
  (if (session/get "email")
    (do
      (be/add-comment (conj post {:subm (session/get "email")
                                  :newsid (:id post)}))
      ;(resp/redirect (format "/comments/%s" (:id post)))
      (render "/comments/:id" post)
      )
    (resp/status 401 "Loging required")))