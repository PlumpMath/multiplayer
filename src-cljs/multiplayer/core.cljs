(ns multiplayer.core
  (:require [reagent.core :as r]
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]
            [goog.events :as events]
            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! >! put! close! timeout]]
            [chord.format.fressian :as fression]
            [cljs-time.local :as local]
            [cljs-time.coerce :as coerce]
            )
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:import goog.History))

(defn nav-link [uri title page collapsed?]
  [:ul.nav.navbar-nav>a.navbar-brand
   {:class (when (= page (session/get :page)) "active")
    :href uri
    :on-click #(reset! collapsed? true)}
   title])

(defn navbar []
  (let [collapsed? (r/atom true)]
    (fn []
      [:nav.navbar.navbar-light.bg-faded
       [:button.navbar-toggler.hidden-sm-up
        {:on-click #(swap! collapsed? not)} "☰"]
       [:div.collapse.navbar-toggleable-xs
        (when-not @collapsed? {:class "in"})
        [:a.navbar-brand {:href "#/"} "multiplayer"]
        [:ul.nav.navbar-nav
         [nav-link "#/" "Home" :home collapsed?]
         [nav-link "#/about" "About" :about collapsed?]]]])))

(defn about-page []
  [:div.container
   [:div.row
    [:div.col-md-12
     "this is the story of multiplayer... work in progress"]]])

(defn home-page []
  [:div.container
   [:div.jumbotron
    [:h1 "Welcome to multiplayer"]
    [:p "Time to start building your site!"]
    [:p [:a.btn.btn-primary.btn-lg {:href "http://luminusweb.net"} "Learn more »"]]]
   [:div.row
    [:div.col-md-12
     [:h2 "Welcome to ClojureScript"]]]
   (when-let [docs (session/get :docs)]
     [:div.row
      [:div.col-md-12
       [:div {:dangerouslySetInnerHTML
              {:__html (md->html docs)}}]]])])

(def pages
  {:home #'home-page
   :about #'about-page})

(defn page []
  [(pages (session/get :page))])

;; -------------------------
;; Routes
(secretary/set-config! :prefix "#")

(secretary/defroute "/" []
  (session/put! :page :home))

(secretary/defroute "/about" []
  (session/put! :page :about))

;; -------------------------
;; History
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
        (events/listen
          HistoryEventType/NAVIGATE
          (fn [event]
              (secretary/dispatch! (.-token event))))
        (.setEnabled true)))

;; -------------------------
;; Initialize app
(defn fetch-docs! []
  (GET (str js/context "/docs") {:handler #(session/put! :docs %)}))

(defn mount-components []
  (r/render [#'navbar] (.getElementById js/document "navbar"))
  (r/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (fetch-docs!)
  (hook-browser-navigation!)
  (mount-components))


(defonce game-state (atom 0))

(defonce foo
  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:3000/ws" {:format :transit-json}))]
      (if-not error
        (do
          (>! ws-channel [:login "bruce-fu!"])
          (let [{[id] :message} (<! ws-channel)]

            (js/console.log "id:" id)
            (loop [c 0
                   to (timeout (/ 1000 30))]

              (>! ws-channel [(swap! game-state inc) (Math/random)])
              (if (nil? (let [data (<! ws-channel)]
                          (if (nil? data)
                            ;; conn closed
                            nil

                            (let [t (coerce/to-long (local/local-now))]
                              (when (zero? (mod c 30))
                                (js/console.log "recv:" (str t) (str data) ))

                              ;; finish the 1/30 timeblock
                              (<! to)
                              (recur (inc c)
                                     (timeout (/ 1000 30))))
                            )))))
            ))
        (js/console.log "Error:" (pr-str error))))))
