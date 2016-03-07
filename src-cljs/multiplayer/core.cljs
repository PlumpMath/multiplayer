(ns multiplayer.core
  (:require
            [reagent.session :as session]
            [secretary.core :as secretary :include-macros true]

            [goog.history.EventType :as HistoryEventType]
            [markdown.core :refer [md->html]]
            [ajax.core :refer [GET POST]]
            [chord.client :refer [ws-ch]]
            [cljs.core.async :refer [<! >! put! close! timeout]]
            [chord.format.fressian :as fression]
            [cljs-time.local :as local]
            [cljs-time.coerce :as coerce]

            [infinitelives.pixi.canvas :as c]
            [infinitelives.pixi.events :as e]
            [infinitelives.pixi.resources :as r]
            [infinitelives.pixi.texture :as t]
            [infinitelives.pixi.sprite :as s]

            [infinitelives.utils.events :as events]

            [cljs.core.async :refer [<!]]
            )
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [infinitelives.pixi.macros :as m])
  (:import goog.History))

(defonce canvas
  (c/init
   {:expand true
    :engine :auto
    :layers [:bg :world :float :ui]
    :background 0x303030
    }))

(defonce game-state (atom {:angle 0}))

;; how fast the network game pump goes
(def network-update-frames 10)

(defn foo []
  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:3000/ws" {:format :fressian}))]
      (if-not error
        (do
          (>! ws-channel [:login :test])
          (let [{response :message} (<! ws-channel)]

            (js/console.log "response:" (str response))
            ))
        (js/console.log "Error:" (pr-str error))))))

(defn compress [state]
  (-> state
      (update-in [:angle] #(.toFixed % 2))
      (dissoc :reflection-angle))
)

(defn receiver [ch]
  (go
    (loop []
      (swap! game-state assoc :reflection-angle
             (let [{[comm {:keys [reflection-angle]}] :message :as msg} (<! ch)
                   f-ang (js/parseFloat reflection-angle)]
               (js/console.log "msg:" (str msg))
               f-ang))
                                        ;(<! (e/next-frame))
      (recur)
      ))
  )

(defn reporter-game-state []
  (go
    (let [{:keys [ws-channel error]} (<! (ws-ch "ws://localhost:3000/ws" {:format :transit-json}))]
      (if-not error
        (do
          (receiver ws-channel
                    )
          (>! ws-channel [:login :test])

          (loop [fnum 0]
            (when (zero? (mod fnum network-update-frames))
              (>! ws-channel [:state (compress @game-state)]))
            (<! (e/next-frame))
            (recur (inc fnum))
            )

          ;; (let [{response :message} (<! ws-channel)]

          ;;   (js/console.log "response:" (str response))
          ;;   )
          )
        (js/console.log "Error:" (pr-str error))))
)
)

(def assets
  {:ship-blue
   {:pos [0 0]
    :size [32 32]}
   :ship-green
   {:pos [32 0]
    :size [32 32]}
   :ship-violet
   {:pos [64 0]
    :size [32 32]}
   :ship-yellow
   {:pos [96 0]
    :size [32 32]}
   })

(defn left? []
  (events/is-pressed? :left))

(defn right? []
  (events/is-pressed? :right))

(defonce main-thread
  (go
    (<! (r/load-resources canvas :bg ["img/spritesheet.png"]))

    (reporter-game-state)

    (t/load-sprite-sheet! (r/get-texture :spritesheet :nearest) assets)

    (m/with-sprite canvas :bg
      [ship (s/make-sprite :ship-yellow :scale 3 :x -200)
       reflection (s/make-sprite :ship-blue :scale 3 :x 200)]
      (loop [angle 0]
        (s/set-rotation! ship angle)

        (swap! game-state assoc :angle angle)

        ;; set the reflection based on the atom
        (s/set-rotation! reflection (:reflection-angle @game-state))

        (<! (e/next-frame))
        (recur (+
                (cond
                  (left?) -0.05
                  (right?) 0.05
                  :default 0.00)
                angle))))))
