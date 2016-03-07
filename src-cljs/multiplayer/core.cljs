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

    (t/load-sprite-sheet! (r/get-texture :spritesheet :nearest) assets)

    (m/with-sprite canvas :bg
      [ship (s/make-sprite :ship-yellow :scale 3)]
      (loop [angle 0]
        (s/set-rotation! ship angle)
        (<! (e/next-frame))
        (recur (+
                (cond
                  (left?) -0.05
                  (right?) 0.05
                  :default 0.00)
                angle))))))
