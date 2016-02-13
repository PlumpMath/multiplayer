(ns multiplayer.routes.home
  (:require [multiplayer.layout :as layout]
            [compojure.core :refer [defroutes GET]]

            [ring.util.http-response :refer [ok]]
            [clojure.java.io :as io]

            ;[chord.format.fressian :as fression]
            [clj-time.core :as time]
            [clj-time.coerce :as coerce]
            [clojure.tools.logging :as log]
         ;[chord.http-kit :refer [wrap-websocket-handler]]
         [chord.http-kit :refer [with-channel wrap-websocket-handler]]
          [clojure.core.async :refer [<! >! put! close! go] :as a]
))

(defn home-page []
  (layout/render "home.html"))


(defonce next-uid (atom 0))

(defn ws-handler [{:keys [ws-channel] :as req}]
  (go
    (let [{greet :message} (<! ws-channel)
          [command & remain] greet
          ]
      (case command
        :login
        (do
          (>! ws-channel [:hi (first remain)
                          (swap! next-uid inc)])
          (loop []
            (let [start (coerce/to-long (time/now))]
              (loop [n 30]
                (<! ws-channel)
                (>! ws-channel [:ok])
                (when (pos? n)
                  (recur (dec n))))

              (let [end (coerce/to-long (time/now))]
                (println "30 messages took " (- end start) " milliseconds")
                (println (float (/ 30000 (- end start))) " mps")
                ))
            (recur))
            ))

      )

    ))


(defroutes home-routes
  (GET "/" [] (home-page))
  (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp)))
  (GET "/str" [request] str)
  (GET "/ws" []
       (wrap-websocket-handler ws-handler {:format :transit-json}))

)
